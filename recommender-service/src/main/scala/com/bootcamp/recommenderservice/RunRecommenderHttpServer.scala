package com.bootcamp.recommenderservice

import cats.{Applicative, Monad, MonadThrow}
import cats.effect.kernel.Sync
import cats.effect.{Async, ExitCode, IO, LiftIO, Resource}
import com.bootcamp.config.{DbConfig, FetchRecommenderHttpConfig, HttpConfig, RecommenderHttpConfig}
import com.bootcamp.playerrepository.PlayerRepository
import org.http4s.blaze.server.BlazeServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._
import org.apache.http.conn.routing.HttpRoute
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{Router, Server}

import scala.language.higherKinds

object RunRecommenderHttpServer {

  def run[F[_]: Async: MonadThrow: LiftIO: Logger](
    customDbConfig: Option[DbConfig] = None,
    customHttpConfig: Option[HttpConfig] = None,
  ): F[ExitCode] = {

    val config: F[(PlayerRepository, HttpConfig)] = for {
      config <- FetchRecommenderHttpConfig.applyF[F]
      httpConfig <- MonadThrow[F].pure(customHttpConfig.getOrElse(config.http))
      dbConfig <- MonadThrow[F].pure(customDbConfig.getOrElse(config.db))
      playerRepository <- LiftIO[F].liftIO(PlayerRepository(dbConfig))
    } yield (playerRepository, httpConfig)

    (for {
      r <- Resource.make(config)(_ => Async[F].unit)
      server <- buildServer(r._2, r._1)
    } yield server).use(_ => Async[F].never)

  }

  def buildServer[F[_]: Async: MonadThrow: LiftIO: Logger](
    httpConfig: HttpConfig,
    playerRepository: PlayerRepository,
  ): Resource[F, Server] =
    BlazeServerBuilder[F]
      .bindHttp(httpConfig.port.value, httpConfig.host)
      .withHttpApp(RecommenderServiceRoute.apply[F](playerRepository))
      .resource
}
