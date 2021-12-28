package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.config.{KafkaConfig, Port}
import com.bootcamp.domain.{PlayerGameRound, PlayerId}
import fs2.kafka.{AutoOffsetReset, CommittableOffsetBatch, ConsumerSettings, Deserializer, KafkaConsumer}
import io.circe.parser.decode
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.atomic.AtomicLong

object ConsumePlayerData {
  def of(
    kafkaConfig: KafkaConfig,
    updatePlayerProfile: UpdatePlayerProfile,
    createTemporaryPlayerProfile: CreateTemporaryPlayerProfile,
  ): IO[ConsumePlayerData] =
    Slf4jLogger.create[IO].map(new ConsumePlayerData(kafkaConfig, updatePlayerProfile, createTemporaryPlayerProfile, _))
}

class ConsumePlayerData(
  kafkaConfig: KafkaConfig,
  updatePlayerProfile: UpdatePlayerProfile,
  createTemporaryPlayerProfile: CreateTemporaryPlayerProfile,
  log: Logger[IO],
) {

  def start: IO[Unit] = stream.compile.drain
  val port: Port = kafkaConfig.port

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(
      keyDeserializer = Deserializer[IO, String],
      valueDeserializer = Deserializer[IO, String],
    )
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
      .withGroupId(kafkaConfig.groupId)
      .withClientId(kafkaConfig.clientId)

  val counter = new AtomicLong(0L)

  val stream =
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(kafkaConfig.topic)
      .records
      .groupWithin(
        kafkaConfig.chunkSize,
        kafkaConfig.chunkTimeout,
      )
      // The below evalTap is only for demo purposes
//      .evalTap(i =>
//        IO {
//          val tempCounter = counter.addAndGet(i.size.toLong)
//          if (tempCounter % 1000 <= 50) println(s"Consumed $tempCounter messages, last is ${i.last.get.record.value}")
//        },
//      )
      .evalMap { chunk =>
        val playerRounds: IO[Seq[Either[(Throwable, String), (PlayerId, PlayerGameRound)]]] = IO {

          chunk
            .foldLeft(Seq.empty[Either[(Throwable, String), (PlayerId, PlayerGameRound)]]) { case (a, b) =>
              val playerId = PlayerId(b.record.key)
              decode[PlayerGameRound](b.record.value) match {
                case Left(e)                => a :+ Left((e, b.record.value))
                case Right(playerGameRound) => a :+ Right((playerId, playerGameRound))
              }
            }
        }
        import cats.implicits._
        val updatedProfiles = for {
          validRounds <- playerRounds.map(_.flatMap(_.toOption))
          invalidRounds <- playerRounds.map(_.flatMap(_.swap.toOption))
          temporaryProfiles <- createTemporaryPlayerProfile.apply(validRounds)
          _ <- updatePlayerProfile.apply(temporaryProfiles)
          _ <- invalidRounds.toList.traverse_(e =>
            log.warn(s"Could not decode Game Round Json ${e._1.getMessage} ${e._2}"),
          )
        } yield ()

        updatedProfiles
          .map(_ => chunk.map(_.offset))
      }
      .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
}
