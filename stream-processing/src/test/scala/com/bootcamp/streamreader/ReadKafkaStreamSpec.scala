package com.bootcamp.streamreader

import cats.effect.IO
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.Main.PlayerDataConsumer
import com.bootcamp.streamreader.domain.GameType._
import com.bootcamp.streamreader.domain._
import io.circe.parser.decode
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant
import scala.concurrent.duration._

class MySpec extends munit.CatsEffectSuite with Matchers with EmbeddedKafka with Eventually with IntegrationPatience {

  test("Runtime exception on invalid message test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val program =
      Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = new UpdatePlayerProfile(ref, repository)
        val service = InitPlayerProfile(state)
        val consumer = new PlayerDataConsumer(kafkaConfig, service)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", "message1")(
        config,
        new StringSerializer,
        new StringSerializer,
      )
      an[RuntimeException] should be thrownBy program
        .unsafeRunTimed(2.seconds)
        .get
    }
  }
  test("Encode/Decode PlayerGameRound ") {
    val t = "2021-11-28T14:14:34.257Z"
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val gr = PlayerGameRound(
      PlayerId("p1"),
      GameId("g1"),
      TableId("t1"),
      Roulette,
      Money(BigDecimal(111.11)),
      Money(BigDecimal(222.22)),
      Instant.parse(t),
    )
    for {
      d <- decode[PlayerGameRound](message1)
    } yield d shouldBe gr
  }

  test("Accepts correct Json test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val program =
      Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = new UpdatePlayerProfile(ref, repository)
        val service = InitPlayerProfile(state)
        val consumer = new PlayerDataConsumer(kafkaConfig, service)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer,
      )

      program.unsafeRunTimed(10.seconds).get.length shouldBe 1
    }
  }

  test("Test repository is updated") {

    val expected = Some(
      PlayerSessionProfile(
        PlayerId("p1"),
        Cluster(1),
        PlayerGamePlay(
          Map(Baccarat -> GameTypeActivity(1, Money(111.11), Money(222.22))),
        ),
      ),
    )

    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val rref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty)
    val program =
      rref.flatMap { ref =>
        val state = new UpdatePlayerProfile(ref, repository)
        val service = InitPlayerProfile(state)
        val consumer = new PlayerDataConsumer(kafkaConfig, service)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer,
      )

      program.unsafeRunTimed(10.seconds)
      repository
        .readByPlayerId(PlayerId("p1"))
        .unsafeRunSync() shouldBe expected
    }
  }

  test("Aggregates players game play test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val rref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty)
    val program = rref.flatMap { ref =>
      val state = new UpdatePlayerProfile(ref, repository)
      val service = InitPlayerProfile(state)
      val consumer = new PlayerDataConsumer(kafkaConfig, service)
      consumer.stream.take(3).compile.toList // read one record and exit
    }

    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
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
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
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
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
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

      program.unsafeRunTimed(10.seconds)

      // TODO How can I verify the contents of the Ref in a test?
      val x = rref.flatMap(ref =>
        for {
          s <- ref.get
          _ <- IO { println(s) }
        } yield (),
      )
      x.unsafeRunSync()
      println(repository.readByPlayerId(PlayerId("p1")).unsafeRunSync())
    }
  }
  test("Aggregates multiple player game play test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val repository = PlayerRepository.inMem
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val ref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty).unsafeRunSync()
    val program: IO[List[Unit]] = {
      val state = new UpdatePlayerProfile(ref, repository)
      val service = InitPlayerProfile(state)
      val consumer = new PlayerDataConsumer(kafkaConfig, service)
      consumer.stream.take(1).compile.toList // read one record and exit
    }
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
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
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val message3 =
      """
        |    {
        |    "playerId": "p3",
        |    "gameId":"g3",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer,
      )
      publishToKafka("topic", "p2", message2)(
        config,
        new StringSerializer,
        new StringSerializer,
      )
      publishToKafka("topic", "p3", message3)(
        config,
        new StringSerializer,
        new StringSerializer,
      )

      program.unsafeRunAndForget()

      // TODO how to get the output from stream processing to compare to fixed expected value?
      val expected =
        List(
          PlayerSessionProfile(
            PlayerId("p1"),
            Cluster(0),
            PlayerGamePlay(
              Map(
                UnknownGameType -> GameTypeActivity(
                  1,
                  Money(111.11),
                  Money(222.22),
                ),
              ),
            ),
          ),
          PlayerSessionProfile(
            PlayerId("p2"),
            Cluster(0),
            PlayerGamePlay(
              Map(
                UnknownGameType -> GameTypeActivity(
                  1,
                  Money(BigDecimal(111.11)),
                  Money(BigDecimal(222.22)),
                ),
              ),
            ),
          ),
          PlayerSessionProfile(
            PlayerId("p3"),
            Cluster(0),
            PlayerGamePlay(
              Map(
                Roulette -> GameTypeActivity(
                  1,
                  Money(BigDecimal(111.11)),
                  Money(BigDecimal(222.22)),
                ),
              ),
            ),
          ),
        )

      eventually {
        println(ref.get.unsafeRunSync())
      }
      eventually {
        println("ref.get" + ref.get.unsafeRunSync())
        repository.storage.values.toList shouldBe expected
      }
    // repository.storage should contain allElementsOf expected
    }
  }
}
