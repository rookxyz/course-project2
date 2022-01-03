package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO}
import cats.syntax.option.none
import com.bootcamp.config.{DbConfig, FetchStreamingConfig, KafkaConfig}
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant

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
        val service = CreateTemporaryPlayerProfile.apply(IO.none[Instant])
        val stream: IO[ConsumePlayerData] = ConsumePlayerData.of(kafkaConfig, state, service)
        val flushInactiveProfiles = FlushInactiveProfiles.of(ref, dbConfig)
        for {
          gameConsumer <- stream
          flushProfiles <- flushInactiveProfiles
          result <- gameConsumer.stream.concurrently(flushProfiles.apply).compile.drain.as(ExitCode.Success)
        } yield result
      }
    } yield ExitCode.Success
}
