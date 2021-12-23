package com.bootcamp.integration

import cats.effect.{FiberIO, IO}
import cats.effect.implicits._
import cats.implicits._
import cats.effect.kernel.{Outcome, Ref, Resource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey}
import com.bootcamp.config.{DbConfig, KafkaConfig, Port}
import com.bootcamp.domain.GameType._
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.recommenderservice.RunRecommenderHttpServer
import com.bootcamp.streamreader.{
  ConsumePlayerData,
  CreateDynamoDbTables,
  CreateTemporaryPlayerProfile,
  RunStreamProcessingServer,
  UpdatePlayerProfile,
}
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

class E2EDemoTestSpec
    extends munit.CatsEffectSuite
    with Matchers
    with EmbeddedKafka
    with Eventually
    with IntegrationPatience
    with TestContainerForEach {

  class DynamoContainer(port: Int, underlying: GenericContainer) extends GenericContainer(underlying) {
    def getPort: String = s"${mappedPort(port)}"
  }
  object DynamoContainer {

    case class Def(port: Int)
        extends GenericContainer.Def[DynamoContainer](
          new DynamoContainer(
            port,
            GenericContainer(
              dockerImage = "amazon/dynamodb-local",
              exposedPorts = Seq(port),
            ),
          ),
        )
  }

  override val containerDef =
    DynamoContainer.Def(
      8000,
    )

  test("Aggregates players game play test with test container") {
    withContainers { dynamoDbContainer =>
      dynamoDbContainer.start()
      val x = containerDef.start()
      println(s"Container started running on port: ${x.getPort}")
      val kafkaConfig = KafkaConfig("localhost", Port(16001), "bootcamp-topic", "group1", "client1", 25, 2.seconds)
      val dbConfig = DbConfig(s"http://localhost:${x.getPort}", 5, 1000, "aaa", "bbbb", "profiles3", "clusters3")
      val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(
        kafkaPort = kafkaConfig.port.value,
      )
      ////// Start DB setup
      implicit val db: DynamoDB = new DynamoDB(
        AmazonDynamoDBClientBuilder.standard
          .withEndpointConfiguration(new EndpointConfiguration(dbConfig.endpoint, "eu-central-1"))
          .build,
      )
      val tablesMap = CreateDynamoDbTables.createDbTables(dbConfig)

      ////// end of DB setup

      withRunningKafkaOnFoundPort(config) { implicit config =>
        def publishGameRoundsToKafka(messagesN: Int, playersN: Int): List[IO[Unit]] = {
          def getRandomElement[A](seq: Seq[A]): A = {
            val i = scala.util.Random.nextInt(seq.length)
            seq(i)
          }
          val gameTypes: Vector[String] =
            Vector("Blackjack", "Roulette", "Baccarat", "UltimateWinPoker", "SpinForeverRoulette", "NeverLoseBaccarat")
          val sign: Vector[Int] = Vector(-1, 0, 1)
          val seqNumMap = scala.collection.mutable.Map.empty[String, Long]
          (0 to messagesN).map { i =>
            IO.sleep(0.millis) *>
              IO {
                val r = scala.util.Random
                val playerId: String = s"p${r.nextInt(playersN)}"
                val gameId: String = s"g$i"
                val gameType: String = getRandomElement(gameTypes)
                val wager: BigDecimal = BigDecimal(r.nextInt(100)).setScale(2)
                val payout: BigDecimal =
                  wager.+(BigDecimal(getRandomElement(sign) * 0.1f).*(wager)).setScale(2, RoundingMode.UP)
                val tableId = s"t${r.nextInt(playersN)}"

                val message =
                  s"""
                      |    {
                      |    "playerId": "$playerId",
                      |    "gameId":"$gameId",
                      |    "tableId":"$tableId",
                      |    "gameType":"$gameType",
                      |    "stakeEur":$wager,
                      |    "payoutEur":$payout,
                      |    "gameEndedTime":"${Instant.now}",
                      |    "seqNum": ${seqNumMap.getOrElseUpdate(playerId, 0)}
                      |    }
                      |""".stripMargin

                //                      |    "playerId": "$playerId",
                seqNumMap(playerId) = seqNumMap(playerId) + 1
                publishMessageToKafka(playerId, message)
              }
          }.toList
        }

        def publishMessageToKafka(partition: String, message: String): Unit = {
          publishToKafka(kafkaConfig.topic, partition, message)(
            config,
            new StringSerializer,
            new StringSerializer,
          )
          println(s"Published message for player $partition to topic ${kafkaConfig.topic}$message")
        }
        val players: Int = 100
        val clusters: Int = 5
        val messages: Int = 50000
        CreateDynamoDbTables.fillClustersTable(tablesMap("clusters"), players, clusters)
        val program = for {
          http <- IO.race(RunRecommenderHttpServer.run(Some(dbConfig)), IO.sleep(4.minutes)).start
          consumer <- RunStreamProcessingServer.run(Some(dbConfig), Some(kafkaConfig)).start
          _ <- publishGameRoundsToKafka(messages, players).traverse_(i => i)
          _ <- consumer.join
//          _ <- http.join
        } yield ()

        program.unsafeRunSync()

      }
    }
  }
}
