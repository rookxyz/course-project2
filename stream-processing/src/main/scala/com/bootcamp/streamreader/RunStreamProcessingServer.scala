package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO}
import com.bootcamp.config.{DbConfig, FetchStreamingConfig, KafkaConfig}
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import org.typelevel.log4cats.slf4j.Slf4jLogger

object RunStreamProcessingServer {
  def run(customDbConfig: Option[DbConfig] = None, customKafkaConfig: Option[KafkaConfig] = None): IO[ExitCode] =
    for {
      logger <- Slf4jLogger.create[IO]
      config <- FetchStreamingConfig.apply
      dbConfig = customDbConfig.getOrElse(config.db)
      kafkaConfig = customKafkaConfig.getOrElse(config.kafka)
      _ <- logger.info(s"Starting streaming service...")
      _ <- logger.info(s"Received database endpoint ${dbConfig.endpoint}")
      _ <- logger.info(
        s"Received Kafka endpoint ${kafkaConfig.host}:${kafkaConfig.port.value} and topic ${kafkaConfig.topic}",
      )
      repository <- PlayerRepository(dbConfig)
      _ <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val stream: IO[ConsumePlayerData] = ConsumePlayerData.of(kafkaConfig, state, service)

        stream.flatMap(consumer => consumer.start.as(ExitCode.Success))
      }
    } yield ExitCode.Success
}
