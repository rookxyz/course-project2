package com.bootcamp.recommenderservice

import cats.effect.{ExitCode, IO}
import com.bootcamp.domain._
import com.bootcamp.config.FetchRecommenderHttpConfig
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.recommenderservice.CalculateSimilarity.CalculateCosineSimilarity
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.io.{GET, Ok}
import org.http4s.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.server.Router

import scala.math.Numeric.LongIsIntegral

object RunRecommenderHttpServer {

  private def recommenderService(playerRepository: PlayerRepository) = HttpRoutes
    .of[IO] { case GET -> Root / "recommender" / "playerId" / playerId =>
      /*
      1. get player cluster
      2. get all players by cluster
      3. calculate player similarity
      4. get top X similar players to playerId
      5. get average ratings for game types
      6. filter out seen game tpes
      7. create response object
      8. encode to Json
      9. return response
       */
      println(s"Got request for player $playerId")

      val playerIdObj = PlayerId(playerId)
      (for { // TODO refactor, fix bug that returns played gametypes
        playerCluster <- playerRepository.readClusterByPlayerId(playerIdObj)
        playersWithCluster <- playerRepository.readPlayersByCluster(playerCluster.get)
        _ = IO.raiseWhen(!playersWithCluster.exists(p => p.playerId == playerIdObj))(
          new Throwable("Player has no activity"),
        )
        playerUnseenGameTypesSorted = GetPlayerRecommendations.x(playerIdObj, playersWithCluster)
        responseJson: Json = playerUnseenGameTypesSorted.asJson
        response <- Ok(responseJson.noSpaces)
      } yield response).handleErrorWith(_ => NotFound())
    }

  private val httpApp = (r: PlayerRepository) => Router("/" -> recommenderService(r)).orNotFound

  def run: IO[ExitCode] =
    for {
      config <- FetchRecommenderHttpConfig.apply
      dbConfig = config.db
      playerRepository <- PlayerRepository(dbConfig)
      httpConfig = config.http
      _ <- BlazeServerBuilder[IO]
        .bindHttp(httpConfig.port.value, httpConfig.host)
        .withHttpApp(httpApp(playerRepository))
        .serve
        .compile
        .drain

    } yield ExitCode.Success

}
