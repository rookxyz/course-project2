package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO}
import com.bootcamp.config.FetchApplicationConfig
import org.http4s.HttpRoutes
import org.http4s.dsl.io.{GET, Ok}
import org.http4s.implicits._
import org.http4s.server.blaze._

import org.http4s.dsl.io._
import org.http4s.server.Router

import scala.concurrent.ExecutionContext.global

object RecommenderserviceServer {

  private val recommenderService = HttpRoutes
    .of[IO] { case GET -> Root / "recommender" / "playerId" / playerId =>
      Ok(s"Hello, $playerId")
    }

  private val httpApp = Router("/" -> recommenderService).orNotFound

  def run(args: List[String]): IO[ExitCode] =
    for {
      config <- FetchApplicationConfig.apply
      httpConfig = config.http
      _ <- BlazeServerBuilder[IO](global)
        .bindHttp(httpConfig.port.value, httpConfig.host)
        .withHttpApp(httpApp)
        .serve
        .compile
        .drain

    } yield ExitCode.Success

}
