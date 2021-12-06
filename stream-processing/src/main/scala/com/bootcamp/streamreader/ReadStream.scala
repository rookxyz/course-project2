package com.bootcamp.streamreader

import cats.effect.kernel.Ref

import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._
import com.typesafe.config.ConfigFactory
import fs2.kafka._
import io.circe.parser._

import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    ConfigSource
      .fromConfig(ConfigFactory.load("application"))
      .load[AppConfig] match {
      case Left(_) => IO.unit.as(ExitCode.Error)
      case Right(config) => {
        val repository = PlayerRepository()
        Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap {
          ref =>
            val state = new PlayerState(ref, repository)
            val service = PlayerProfileStateHandler(state)
            val stream = new PlayerDataConsumer(config.kafka, service)
            stream.start.as(ExitCode.Success)
        }
      }
    }
  }

  class PlayerDataConsumer(
      kafkaConfig: KafkaConfig = KafkaConfig("127.0.0.1", Port(0), "topic"),
      playerProfileHandler: PlayerProfileStateHandler
  ) {

    def start: IO[Unit] = stream.compile.drain

    val port: Port = kafkaConfig.port

    val consumerSettings: ConsumerSettings[IO, String, String] =
      ConsumerSettings(
        keyDeserializer = Deserializer[IO, String],
        valueDeserializer = Deserializer[IO, String]
      ) // TODO check if can decode to PlayerGameRound here
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
        .withGroupId("group1") // TODO get from config
        .withClientId("client1") // TODO get from config

    val stream =
      KafkaConsumer
        .stream(consumerSettings)
        .subscribeTo(kafkaConfig.topic)
        .records
        .groupWithin(
          25,
          2.seconds
        ) // TODO needs to be configurable default 25, 15
//        .evalTap(i => IO { println(i) })
        .evalMapChunk { chunk =>
          // 1) group/handle data into Seq[PlayerSessionProfile] and pass to playerService(players)
          // 2) test it works
          // 3) in mem state (Ref) - 11) init from db if empty.
          //   3.1) last game ids
          // 4) write to db
          // TODO 5) sequenceNumber
          // TODO check sequenceNumber is correct
          // TODO check last sequenceNumber in state
          // TODO restore state from db if there is gap
          val playerRounds: IO[Seq[(PlayerId, PlayerGameRound)]] = IO {
            chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) {
              case (a, b) =>
                val playerId = PlayerId(b.record.key)
                val playerGameRound = {
                  decode[PlayerGameRound](b.record.value) match {
                    case Left(e) =>
                      throw new RuntimeException(
                        s"Error: Could not create PlayerGameRound from Json. $e"
                      ) // TODO Log unsuccessful decode
                    case Right(playerGameRound) => playerGameRound
                  }
                }
                a :+ (playerId, playerGameRound)
            }
          }
          val program = for {
            r <- playerRounds
            p <- playerProfileHandler.createPlayerSessionProfile(r)
            _ <- playerProfileHandler
              .processNewPlayerProfiles(p)
          } yield ()
          program.map(_ => chunk.map(_.offset))
        }
        .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
  }
}
