package com.bootcamp.streamreader

import cats.effect.IO
import cats.effect.kernel.Ref
import com.bootcamp.config.domain.{KafkaConfig, Port}
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.domain._
import com.bootcamp.domain.GameType._
import io.circe
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import scala.concurrent.duration._

class MySpec extends munit.CatsEffectSuite with Matchers with EmbeddedKafka with Eventually with IntegrationPatience {

  test("Runtime exception on invalid message test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )

    val program = for {
      logger <- Slf4jLogger.create[IO]
      _ <- logger.error(s"First log!")
      - <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    } yield ()
    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", "message1")(
        config,
        new StringSerializer,
        new StringSerializer,
      )

      // TODO how to check for log - read file or can capture?
      an[RuntimeException] should be thrownBy program
        .unsafeRunTimed(10.seconds)
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
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum":1
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
      SeqNum(1L),
    )
    val d: Either[circe.Error, PlayerGameRound] = decode[PlayerGameRound](message1)
    d match {
      case Left(error) =>
        println(error)
        0 shouldBe 1
      case Right(value) =>
        println(value)
        value shouldBe gr
    }

  }

  test("Accepts correct Json test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val program = for {
      logger <- Slf4jLogger.create[IO]
      li <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    } yield li
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum":1
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

  test("Test PlayerSessionProfile Json") {
    val profile = PlayerSessionProfile(
      PlayerId("p1"),
      Cluster(1),
      SeqNum(0L),
      SeqNum(1L),
      PlayerGamePlay(
        Map(
          Baccarat -> GameTypeActivity(1, Money(111.11), Money(222.22)),
          Roulette -> GameTypeActivity(2, Money(113.11), Money(221.22)),
        ),
      ),
    )

    val expected =
      """{
        |  "playerId" : "p1",
        |  "playerCluster" : 1,
        |  "firstSeqNum" : 0,
        |  "lastSeqNum" : 1,
        |  "gamePlay" : {
        |    "gamePlay" : {
        |      "\"Baccarat\"" : {
        |        "gameRounds" : 1,
        |        "stakeEur" : 111.11,
        |        "payoutEur" : 222.22
        |      },
        |      "\"Roulette\"" : {
        |        "gameRounds" : 2,
        |        "stakeEur" : 113.11,
        |        "payoutEur" : 221.22
        |      }
        |    }
        |  }
        |}""".stripMargin

//    println(profile.asJson.toString())
    profile.asJson.toString() shouldBe expected

  }

  test("Test repository is updated") {

    val expected = Some(
      PlayerSessionProfile(
        PlayerId("p1"),
        Cluster(1),
        SeqNum(0L),
        SeqNum(1L),
        PlayerGamePlay(
          Map(Baccarat -> GameTypeActivity(1, Money(111.11), Money(222.22))),
        ),
      ),
    )

    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
//    val dbConfig = DbConfig("localhost", Port(22222), "aaa", "bbbb", "profiles", "clusters")
    val repository = PlayerRepository()

    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )

    val program = for {
      logger <- Slf4jLogger.create[IO]
      - <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    } yield ()
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
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val program = for {
      logger <- Slf4jLogger.create[IO]
      - <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    } yield ()

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

      program.unsafeRunTimed(10.seconds)

      repository.readByPlayerId(PlayerId("p1")).unsafeRunSync() shouldBe expected
    }
  }
  test("Aggregates multiple player game play test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val repository = PlayerRepository.inMem
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val ref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty).unsafeRunSync()

    val program = for {
      logger <- Slf4jLogger.create[IO]
      - <- IO {
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(2).compile.toList // read one record and exit
      }
    } yield ()
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
        |    "seqNum": 1
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
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum": 1
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
        Set(
          PlayerSessionProfile(
            PlayerId("p1"),
            Cluster(1),
            SeqNum(0L),
            SeqNum(1L),
            PlayerGamePlay(
              Map(
                Baccarat -> GameTypeActivity(
                  1,
                  Money(111.11),
                  Money(222.22),
                ),
              ),
            ),
          ),
          PlayerSessionProfile(
            PlayerId("p2"),
            Cluster(1),
            SeqNum(0L),
            SeqNum(1L),
            PlayerGamePlay(
              Map(
                Baccarat -> GameTypeActivity(
                  1,
                  Money(BigDecimal(111.11)),
                  Money(BigDecimal(222.22)),
                ),
              ),
            ),
          ),
          PlayerSessionProfile(
            PlayerId("p3"),
            Cluster(1),
            SeqNum(0L),
            SeqNum(1L),
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
//        println("ref.get" + ref.get.unsafeRunSync())
        repository.storage.values.toSet shouldBe expected
      }
    // repository.storage should contain allElementsOf expected
    }
  }

  test("Out of order sequence number test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val repository = PlayerRepository()
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val initRepository = PlayerSessionProfile(
      PlayerId("p1"),
      Cluster(1),
      SeqNum(0L),
      SeqNum(2L),
      PlayerGamePlay(
        Map(
          Baccarat -> GameTypeActivity(
            2,
            Money(2.0),
            Money(-2.0),
          ),
        ),
      ),
    )
    val initState = PlayerSessionProfile(
      PlayerId("p1"),
      Cluster(1),
      SeqNum(0L),
      SeqNum(1L),
      PlayerGamePlay(
        Map(
          Baccarat -> GameTypeActivity(
            1,
            Money(1.0),
            Money(-1.0),
          ),
        ),
      ),
    )
    repository.store(Seq(initRepository)).unsafeRunSync()
    val rref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map(PlayerId("p1") -> initState))

    val program = for {
      logger <- Slf4jLogger.create[IO]
      - <- rref flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val consumer = new ConsumePlayerData(kafkaConfig, state, service, logger)
        consumer.stream.take(3).compile.toList // read one record and exit
      }
    } yield ()
    val expected = Some(
      PlayerSessionProfile(
        PlayerId("p1"),
        Cluster(1),
        SeqNum(0),
        SeqNum(5),
        PlayerGamePlay(
          Map(
            Baccarat -> GameTypeActivity(4, Money(4.0), Money(-4.0)),
            Roulette -> GameTypeActivity(1, Money(1.0), Money(-1.0)),
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
        |    "stakeEur": 1.0,
        |    "payoutEur":-1.0,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum": 3
        |    }
        |""".stripMargin

    val message2 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g2",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur": 1.0,
        |    "payoutEur":-1.0,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum": 4
        |    }
        |""".stripMargin

    val message3 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g3",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur": 1.0,
        |    "payoutEur":-1.0,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum": 5
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

      repository.readByPlayerId(PlayerId("p1")).unsafeRunSync() shouldBe expected
    }
  }
}
