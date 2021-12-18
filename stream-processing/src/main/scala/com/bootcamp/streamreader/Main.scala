package com.bootcamp.streamreader

import cats.effect.kernel.Ref

import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- FetchApplicationConfig.apply
      repository = PlayerRepository(config.db)
      _ <- Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = CreateTemporaryPlayerProfile.apply
        val stream = new PlayerDataConsumer(config.kafka, state, service)
        stream.start.as(ExitCode.Success)
      }
    } yield ExitCode.Success
}
