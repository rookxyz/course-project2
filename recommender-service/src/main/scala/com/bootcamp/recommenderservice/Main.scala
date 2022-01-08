package com.bootcamp.recommenderservice

import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp {
  implicit def logger = Slf4jLogger.getLogger[IO]
  def run(args: List[String]) = RunRecommenderHttpServer.run[IO]()
}
