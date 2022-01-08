package com.bootcamp.recommenderservice

import cats.{Applicative, Monad, MonadError, MonadThrow}
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, LiftIO}
import cats.implicits.toFlatMapOps
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.syntax.EncoderOps
import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._

import scala.language.higherKinds

class RecommenderServiceRoute[F[_]: MonadThrow: Applicative: LiftIO](playerRepository: PlayerRepository)
    extends Http4sDsl[F] {

  def apply: HttpRoutes[F] =
    HttpRoutes
      .of[F] { case GET -> Root / "recommender" / "playerId" / id =>
        val playerId = PlayerId(id)
        (for {
          profile <- LiftIO[F].liftIO(playerRepository.readByPlayerId(playerId))
          playerSessionProfile <- MonadThrow[F].fromOption(profile, new Throwable(s"Player has no cluster"))
          playerCluster <- Monad[F].pure(playerSessionProfile.playerCluster)
          playersWithCluster <- LiftIO[F].liftIO(playerRepository.readPlayersByCluster(playerCluster))
          playerUnseenGameTypesSorted <- Monad[F].pure(GetPlayerRecommendations.apply(playerId, playersWithCluster))
          responseJson <- Monad[F].pure(playerUnseenGameTypesSorted.asJson)
          response <- if (playerUnseenGameTypesSorted.nonEmpty) Ok(responseJson.noSpaces) else Ok("null")
        } yield response).handleErrorWith(_ => NotFound())
      }
}

object RecommenderServiceRoute {
  def apply[F[_]: MonadThrow: LiftIO](
    playerRepository: PlayerRepository,
  ): HttpApp[F] =
    Router(
      "/" -> new RecommenderServiceRoute[F](playerRepository).apply,
    ).orNotFound
}
