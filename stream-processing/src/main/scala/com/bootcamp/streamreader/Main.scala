package com.bootcamp.streamreader

import cats.effect.{ExitCode, IO, IOApp, Resource}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    RunStreamProcessingServer.run()
}
