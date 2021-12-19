package com.bootcamp.streamreader

import cats.effect.{IO, Sync}
import com.bootcamp.config.domain.{KafkaConfig, Port}
import com.bootcamp.domain.{PlayerGameRound, PlayerId}
import fs2.kafka.{AutoOffsetReset, CommittableOffsetBatch, ConsumerSettings, Deserializer, KafkaConsumer}
import io.circe.parser.decode
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ConsumePlayerData(
  kafkaConfig: KafkaConfig,
  updatePlayerProfile: UpdatePlayerProfile,
  createTemporaryPlayerProfile: CreateTemporaryPlayerProfile,
  logger: SelfAwareStructuredLogger[IO],
) {

  def start: IO[Unit] = stream.compile.drain
  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  val port: Port = kafkaConfig.port

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(
      keyDeserializer = Deserializer[IO, String],
      valueDeserializer = Deserializer[IO, String],
    ) // TODO check if can decode to PlayerGameRound here
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
      .withGroupId(kafkaConfig.groupId)
      .withClientId(kafkaConfig.clientId)

  val stream =
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(kafkaConfig.topic)
      .records
      .groupWithin(
        kafkaConfig.chunkSize,
        kafkaConfig.chunkTimeout,
      )
      .evalMap { chunk =>
        val playerRounds: IO[Seq[(PlayerId, PlayerGameRound)]] = IO {
          chunk
            .foldLeft(Seq.empty[Option[(PlayerId, PlayerGameRound)]]) { case (a, b) =>
              val playerId = PlayerId(b.record.key)

              decode[PlayerGameRound](b.record.value) match {
                case Left(e) => // TODO I do not see the logs appear
                  logger.warn(e)(s"WARN: Could not create PlayerGameRound from Json: ${b.record.value}")
                  a :+ Option.empty[(PlayerId, PlayerGameRound)]
                case Right(playerGameRound) => a :+ Some((playerId, playerGameRound))
              }
            }
            .flatten
        }
        val updatedProfiles = for {
          playerRounds <- playerRounds
          temporaryProfiles <- createTemporaryPlayerProfile.apply(playerRounds)
          _ <- updatePlayerProfile.apply(temporaryProfiles)
        } yield ()

        updatedProfiles
          .map(_ => chunk.map(_.offset))
      }
      .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
}
