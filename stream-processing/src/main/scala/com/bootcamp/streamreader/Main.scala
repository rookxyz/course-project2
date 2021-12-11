package com.bootcamp.streamreader

import cats.effect.kernel.Ref

import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Configuration.fetchAppConfig match {
      case Left(_) => IO.unit.as(ExitCode.Error)
      case Right(config) =>
        val repository = PlayerRepository()
        Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap { ref =>
          val state = new UpdatePlayerProfile(ref, repository)
          val service = InitPlayerProfile(state)
          val stream = new PlayerDataConsumer(config.kafka, service)
          stream.start.as(ExitCode.Success)
        }
    }

}

// p1, p1-1, p1-2, p2-4, p1-3, p2-5
// p2, p2-2, p2-3, connection problem, issue fixed, p2-6

// 1, 2, 3, 6
// 12324234, 12324238
