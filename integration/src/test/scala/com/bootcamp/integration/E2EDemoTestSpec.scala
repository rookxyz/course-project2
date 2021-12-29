package com.bootcamp.integration

import cats.effect.IO
import cats.effect.implicits._
import cats.implicits._
import cats.effect.kernel.{Outcome, Ref, Resource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.bootcamp.config.{DbConfig, KafkaConfig, Port}
import com.bootcamp.domain.GameType._
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.recommenderservice.RunRecommenderHttpServer
import com.bootcamp.streamreader.{
  ConsumePlayerData,
  CreateDynamoDbTables,
  RunStreamProcessingServer,
  UpdatePlayerProfile,
}
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.typelevel.log4cats.Logger

import java.time.Instant
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory

class E2EDemoTestSpec extends munit.CatsEffectSuite with EmbeddedKafka with TestContainerForEach {
  LoggerFactory
    .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(Level.ERROR)
  val log = LoggerFactory
    .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

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

  test("End to End integration test for demo with test container") {
    withContainers { dynamoDbContainer =>
      dynamoDbContainer.start()
      val x = containerDef.start()
      println(s"Container started running on port: ${x.getPort}")
      val kafkaConfig = KafkaConfig("localhost", Port(16001), "bootcamp-topic", "group1", "client1", 25, 2.seconds)
      val dbConfig =
        DbConfig(s"http://localhost:${x.getPort}", 5, 1000, "secret1", "secret2", "profiles3", "clusters3", 10.seconds)
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
          val maxGameTypes: Int = 2
          val seqNumMap = scala.collection.mutable.Map.empty[String, (Long, Set[String])]
          (0 to messagesN).map { i =>
            IO.sleep(500.millis) *>
              IO {
                val r = scala.util.Random
                val playerId: String = s"p${r.nextInt(playersN)}"
                val gameId: String = s"g$i"
                val playerGameTypes: Set[String] = seqNumMap.getOrElse(playerId, (0, Set.empty[String]))._2
                val gameType: String =
                  if (playerGameTypes.size >= maxGameTypes) playerGameTypes.toList(r.nextInt(maxGameTypes))
                  else getRandomElement(gameTypes)
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
                      |    "seqNum": ${seqNumMap.getOrElseUpdate(playerId, (0, Set(gameType)))._1}
                      |    }
                      |""".stripMargin
                if (i % 1000 == 0) println(s"Published $i messages! $message")
                seqNumMap(playerId) = (seqNumMap(playerId)._1 + 1, seqNumMap(playerId)._2 + gameType)
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
          log.info(s"Published message for player $partition to topic ${kafkaConfig.topic}$message")
        }
        val players: Int = 20
        val clusters: Int = 3
        val messages: Int = 5000
        CreateDynamoDbTables.fillClustersTable(tablesMap("clusters"), players, clusters)
        val program = for {
          _ <- IO.race(RunRecommenderHttpServer.run(Some(dbConfig)), IO.sleep(4.minutes)).start
          _ <- RunStreamProcessingServer.run(Some(dbConfig), Some(kafkaConfig)).start
          _ <- publishGameRoundsToKafka(messages, players).traverse_(i => i)
        } yield ()

        program.unsafeRunSync()

      }
    }
  }
}
