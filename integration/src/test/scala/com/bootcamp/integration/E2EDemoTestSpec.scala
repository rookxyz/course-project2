package com.bootcamp.integration

import cats.effect.{IO}
import cats.effect.kernel.Ref
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey}
import com.bootcamp.config.{DbConfig, KafkaConfig, Port}
import com.bootcamp.domain.GameType._
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.recommenderservice.RunRecommenderHttpServer
import com.bootcamp.streamreader.CreateDynamoDbTables
import com.bootcamp.streamreader.{ConsumePlayerData, CreateTemporaryPlayerProfile, UpdatePlayerProfile}
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger
import fs2._
import scala.concurrent.duration._

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
      val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
      val dbConfig = DbConfig(s"http://localhost:${x.getPort}", "aaa", "bbbb", "profiles3", "clusters3")
      val repository = PlayerRepository(dbConfig).unsafeRunSync()
      val config = EmbeddedKafkaConfig(
        kafkaPort = kafkaConfig.port.value,
      )
      val program = for {
        logger <- Slf4jLogger.create[IO]
        httpServer <- RunRecommenderHttpServer.run
        - <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
          val state = UpdatePlayerProfile(ref, repository)
          val service = CreateTemporaryPlayerProfile.apply
          val consumer =
            new ConsumePlayerData(
              kafkaConfig,
              state,
              service,
              logger,
            ) // TODO any way to use .run but mock the dbConfig?
          consumer.stream.take(1000).compile.drain
        }
      } yield ()

      // Start DB setup

      implicit val db: DynamoDB = new DynamoDB(
        AmazonDynamoDBClientBuilder.standard
          .withEndpointConfiguration(new EndpointConfiguration(dbConfig.endpoint, "eu-central-1"))
          .build,
      )

//      import com.bootcamp.streamreader.CreateDynamoDbTables._
      val tablesMap = CreateDynamoDbTables.createDbTables(dbConfig)
      CreateDynamoDbTables.fillClustersTable(tablesMap("clusters"))

      ////// end of DB setup
      val expected = Some(
        PlayerSessionProfile(
          PlayerId("p1"),
          Cluster(1),
          SeqNum(0),
          SeqNum(3),
          PlayerGamePlay(
            Map(
              Baccarat -> GameTypeActivity(2, Money(222.22), Money(444.44)),
              Roulette -> GameTypeActivity(1, Money(111.11), Money(222.22)),
            ),
          ),
        ),
      )

      val message1 =
        """
          |    {
          |    "playerId": "p1",
          |    "gameId":"g1",
          |    "tableId":"t1",
          |    "gameType":"Baccarat",
          |    "stakeEur":111.11,
          |    "payoutEur":222.22,
          |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
          |    "seqNum": 1
          |    }
          |""".stripMargin

      val message2 =
        """
          |    {
          |    "playerId": "p2",
          |    "gameId":"g2",
          |    "tableId":"t1",
          |    "gameType":"Baccarat",
          |    "stakeEur":111.11,
          |    "payoutEur":222.22,
          |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
          |    "seqNum": 2
          |    }
          |""".stripMargin

      val message3 =
        """
          |    {
          |    "playerId": "p2",
          |    "gameId":"g3",
          |    "tableId":"t1",
          |    "gameType":"Roulette",
          |    "stakeEur":111.11,
          |    "payoutEur":222.22,
          |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
          |    "seqNum": 3
          |    }
          |""".stripMargin

      withRunningKafkaOnFoundPort(config) { implicit config =>
        Stream
          .eval(IO {

            publishToKafka("topic", "p1", message1)(
              config,
              new StringSerializer,
              new StringSerializer,
            )
            publishToKafka("topic", "p1", message2)(
              config,
              new StringSerializer,
              new StringSerializer,
            )
            publishToKafka("topic", "p1", message3)(
              config,
              new StringSerializer,
              new StringSerializer,
            )
          })
          .repeatN(10)
          .delayBy(1.seconds)
          .compile
          .drain
          .unsafeRunSync()
      }

    }
  }

}
