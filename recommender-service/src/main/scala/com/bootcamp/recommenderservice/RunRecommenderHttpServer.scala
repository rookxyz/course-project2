package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO}
import com.bootcamp.domain._
import com.bootcamp.config.FetchRecommenderHttpConfig
import com.bootcamp.playerrepository.PlayerRepository
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger

object RunRecommenderHttpServer {

  private def recommenderService(playerRepository: PlayerRepository) = HttpRoutes
    .of[IO] { case GET -> Root / "recommender" / "playerId" / playerId =>
      val playerIdObj = PlayerId(playerId)
      (for { // TODO refactor, fix bug that returns played gametypes
        playerCluster <- playerRepository.readClusterByPlayerId(playerIdObj)
        playersWithCluster <- playerRepository.readPlayersByCluster(playerCluster.get)
        _ = IO.raiseWhen(!playersWithCluster.exists(p => p.playerId == playerIdObj))(
          new Throwable("Player has no activity"),
        )
        playerUnseenGameTypesSorted = GetPlayerRecommendations.apply(playerIdObj, playersWithCluster)
        responseJson: Json = playerUnseenGameTypesSorted.asJson
        response <- if (playerUnseenGameTypesSorted.nonEmpty) Ok(responseJson.noSpaces) else NotFound()
      } yield response).handleErrorWith(_ => NotFound())
    }

  private val httpApp = (r: PlayerRepository) => Router("/" -> recommenderService(r)).orNotFound

  def run: IO[ExitCode] =
    for {
      config <- FetchRecommenderHttpConfig.apply
      log <- Slf4jLogger.create[IO]
      dbConfig = config.db
      playerRepository <- PlayerRepository(dbConfig)
      httpConfig = config.http
      _ <- log.info(s"Recommender Http server started on ${httpConfig.host}:${httpConfig.port}")
      _ <- BlazeServerBuilder[IO]
        .bindHttp(httpConfig.port.value, httpConfig.host)
        .withHttpApp(httpApp(playerRepository))
        .serve
        .compile
        .drain

    } yield ExitCode.Success

}
