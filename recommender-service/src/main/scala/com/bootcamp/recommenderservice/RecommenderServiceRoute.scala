package com.bootcamp.recommenderservice

import cats.data.Kleisli
import cats.effect.IO
import com.bootcamp.domain.PlayerId
import com.bootcamp.playerrepository.PlayerRepository
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.io._
import org.http4s.server.Router

class RecommenderServiceRoute(playerRepository: PlayerRepository) {
  def apply: HttpRoutes[IO] =
    HttpRoutes
      .of[IO] { case GET -> Root / "recommender" / "playerId" / playerId =>
        val playerIdObj = PlayerId(playerId)
        (for {
          playerCluster <- playerRepository.readClusterByPlayerId(playerIdObj)
          playersWithCluster <- playerRepository.readPlayersByCluster(playerCluster.get)
          _ = IO.raiseWhen(!playersWithCluster.exists(p => p.playerId == playerIdObj))(
            new Throwable("Player has no activity"),
          )
          playerUnseenGameTypesSorted = GetPlayerRecommendations.apply(playerIdObj, playersWithCluster)
          responseJson: Json = playerUnseenGameTypesSorted.asJson
          response <- if (playerUnseenGameTypesSorted.nonEmpty) Ok(responseJson.noSpaces) else Ok("null")
        } yield response).handleErrorWith(_ => NotFound())
      }
}

object RecommenderServiceRoute {
  val httpApp: PlayerRepository => Kleisli[IO, Request[IO], Response[IO]] = (r: PlayerRepository) =>
    Router("/" -> RecommenderServiceRoute(r)).orNotFound
  def apply(playerRepository: PlayerRepository): HttpRoutes[IO] = new RecommenderServiceRoute(playerRepository).apply
}
