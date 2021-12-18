package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.streamreader.domain.{KafkaConfig, PlayerGameRound, PlayerId, Port}
import fs2.kafka.{AutoOffsetReset, CommittableOffsetBatch, ConsumerSettings, Deserializer, KafkaConsumer}
import io.circe.parser.decode

class PlayerDataConsumer(
  kafkaConfig: KafkaConfig,
  updatePlayerProfile: UpdatePlayerProfile,
  createTemporaryPlayerProfile: CreateTemporaryPlayerProfile,
) {

  def start: IO[Unit] = stream.compile.drain

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
          chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) { case (a, b) =>
            val playerId = PlayerId(b.record.key)
            val playerGameRound = {
              decode[PlayerGameRound](b.record.value) match {
                case Left(e) =>
                  println(b.record.value)
                  throw new RuntimeException(
                    s"Error: Could not create PlayerGameRound from Json. $e",
                  ) // TODO Log unsuccessful decode
                case Right(playerGameRound) => playerGameRound
              }
            }
            a :+ (playerId, playerGameRound)
          }
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
