package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO}
import com.bootcamp.config.{DbConfig, FetchRecommenderHttpConfig, HttpConfig}
import com.bootcamp.playerrepository.PlayerRepository
import org.http4s.blaze.server.BlazeServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger

object RunRecommenderHttpServer {
  def run(customDbConfig: Option[DbConfig] = None, customHttpConfig: Option[HttpConfig] = None): IO[ExitCode] =
    for {
      config <- FetchRecommenderHttpConfig.apply
      log <- Slf4jLogger.create[IO]
      dbConfig = customDbConfig.getOrElse(config.db)
      playerRepository <- PlayerRepository(dbConfig)
      httpConfig = customHttpConfig.getOrElse(config.http)
      _ <- log.info(s"Recommender Http server starting on ${httpConfig.host}:${httpConfig.port.value}")
      _ <- log.info(s"Received database endpoint ${dbConfig.endpoint}")
      _ <- BlazeServerBuilder[IO]
        .bindHttp(httpConfig.port.value, httpConfig.host)
        .withHttpApp(RecommenderServiceRoute.httpApp(playerRepository))
        .serve
        .compile
        .drain
    } yield ExitCode.Success
}
