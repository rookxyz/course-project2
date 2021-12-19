package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.config.FetchApplicationConfig
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.domain._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- FetchApplicationConfig.apply
      repository = PlayerRepository(config.db)
      _ <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val stream = new ConsumePlayerData(config.kafka, state, service)
        stream.start.as(ExitCode.Success)
      }
    } yield ExitCode.Success
}
