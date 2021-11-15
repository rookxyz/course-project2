package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    RecommenderserviceServer.stream[IO].compile.drain.as(ExitCode.Success)
}
