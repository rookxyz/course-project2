package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    RecommenderserviceServer.stream[IO].compile.drain.as(ExitCode.Success)
}


// TODO should Resource approach be used to start http server? a resource for connections, something, logging, metrics, etc
// TODO should have cache ?