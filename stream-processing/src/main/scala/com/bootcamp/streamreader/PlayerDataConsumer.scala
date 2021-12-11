package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.streamreader.domain.{KafkaConfig, PlayerGameRound, PlayerId, Port}
import fs2.kafka.{AutoOffsetReset, CommittableOffsetBatch, ConsumerSettings, Deserializer, KafkaConsumer}
import io.circe.parser.decode

class PlayerDataConsumer(
  kafkaConfig: KafkaConfig,
  initPlayerProfile: InitPlayerProfile,
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
      ) // TODO needs to be configurable default 25, 15
      .evalMap { chunk =>
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
          chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) { case (a, b) =>
            val playerId = PlayerId(b.record.key)
            val playerGameRound = {
              decode[PlayerGameRound](b.record.value) match {
                case Left(e) =>
                  throw new RuntimeException(
                    s"Error: Could not create PlayerGameRound from Json. $e",
                  ) // TODO Log unsuccessful decode
                case Right(playerGameRound) => playerGameRound
              }
            }
            a :+ (playerId, playerGameRound)
          }
        }
        playerRounds.flatMap(initPlayerProfile.apply).map(_ => chunk.map(_.offset))
      }
      .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
}
