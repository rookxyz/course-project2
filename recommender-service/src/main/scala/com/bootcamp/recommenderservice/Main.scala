package com.bootcamp.recommenderservice

import cats.effect.IOApp

object Main extends IOApp {
  def run(args: List[String]) = RunRecommenderHttpServer.run()
}

// TODO should Resource approach be used to start http server? a resource for connections, something, logging, metrics, etc
// TODO should have cache ?
