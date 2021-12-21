package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO}
import com.bootcamp.config.FetchStreamingConfig
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import org.typelevel.log4cats.slf4j.Slf4jLogger

object RunStreamProcessingServer {
  def run: IO[ExitCode] =
    for {
      config <- FetchStreamingConfig.apply
      logger <- Slf4jLogger.create[IO]
      _ <- logger.info(s"Starting streaming service...")
      repository <- PlayerRepository(config.db)
      _ <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val stream = ConsumePlayerData.of(config.kafka, state, service)
        stream.flatMap(consumer => consumer.start.as(ExitCode.Success))
      }
    } yield ExitCode.Success
}
