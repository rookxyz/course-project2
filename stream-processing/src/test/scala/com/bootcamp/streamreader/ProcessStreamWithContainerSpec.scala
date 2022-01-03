package com.bootcamp.streamreader

import cats.effect.IO
import cats.effect.kernel.Ref
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey}
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.domain._
import com.bootcamp.domain.GameType._
import com.bootcamp.config.{DbConfig, KafkaConfig, Port}
import com.dimafeng.testcontainers.{ContainerDef, GenericContainer}
import com.dimafeng.testcontainers.munit.TestContainerForEach
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.matchers.should.Matchers
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import scala.concurrent.duration._

class ProcessStreamWithContainerSpec
    extends munit.CatsEffectSuite
    with Matchers
    with EmbeddedKafka
    with Eventually
    with IntegrationPatience
    with TestContainerForEach {

  import CreateDynamoDbTables._

  class DynamoContainer(port: Int, underlying: GenericContainer) extends GenericContainer(underlying) {
    // you can add any methods or fields inside your container's body
    def getPort: String = s"${mappedPort(port)}"
  }
  object DynamoContainer {

    // In the container definition you need to describe, how your container will be constructed:
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
      //command = Seq("java -jar DynamoDBLocal.jar -inMemory -sharedDb"),
    )

  test("Aggregates players game play test with test container") {
    withContainers { dynamoDbContainer =>
      dynamoDbContainer.start()
      val x = containerDef.start()
      println(s"Container started running on port: ${x.getPort}")
      val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
      val dbConfig =
        DbConfig(s"http://localhost:${x.getPort}", 5, 1000, "aaa", "bbbb", "profiles3", "clusters3", 300.seconds)
      val repository = PlayerRepository(dbConfig).unsafeRunSync()
      val config = EmbeddedKafkaConfig(
        kafkaPort = kafkaConfig.port.value,
      )
      val program = for {
        logger <- Slf4jLogger.create[IO]
        - <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
          val state = UpdatePlayerProfile(ref, repository)
          val service = CreateTemporaryPlayerProfile.apply(IO.pure(Some(Instant.ofEpochMilli(0L))))
          val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
          consumer.stream.take(3).compile.toList // read one record and exit
        }
      } yield ()

      // Start DB setup

      implicit val db: DynamoDB = new DynamoDB(
        AmazonDynamoDBClientBuilder.standard
          .withEndpointConfiguration(new EndpointConfiguration(dbConfig.endpoint, "eu-central-1"))
          .build,
      )
//      deleteDbTables
      val tablesMap = createDbTables(dbConfig)
      val clustersTable = tablesMap.get("clusters")
      clustersTable.get
        .putItem(
          new Item()
            .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p1"))
            .withNumber("cluster", 1),
        )
        .ensuring(true)
      clustersTable.get
        .putItem(
          new Item()
            .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p2"))
            .withNumber("cluster", 1),
        )
        .ensuring(true)
      clustersTable.get
        .putItem(
          new Item()
            .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p3"))
            .withNumber("cluster", 1),
        )
        .ensuring(true)
      ////// end of DB setup
      val expected = Some(
        PlayerSessionProfile(
          PlayerId("p1"),
          Cluster(1),
          SeqNum(0),
          SeqNum(3),
          0L,
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
          |    "playerId": "p1",
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
          |    "playerId": "p1",
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

        program.unsafeRunTimed(20.seconds)

        assertIO(repository.readByPlayerId(PlayerId("p1")), expected)
      }
    }
  }

}
