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

  def customRoute[F[_]: MonadThrow]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "movies" / movieId =>
      Ok(s"Requested $movieId")

    }
  }

  def run[F[_]: Async: Logger: LiftIO: MonadThrow: Applicative](
    customDbConfig: Option[DbConfig] = None,
    customHttpConfig: Option[HttpConfig] = None,
  ): F[ExitCode] = {

    val config: F[RecommenderHttpConfig] = FetchRecommenderHttpConfig.applyF[F]
    val httpConfig: F[HttpConfig] = config.map(c => c.http)
    val x: F[(PlayerRepository, HttpConfig)] = for {
      config <- config
      httpConfig <- MonadThrow[F].pure(customHttpConfig.getOrElse(config.http))
      dbConfig <- MonadThrow[F].pure(customDbConfig.getOrElse(config.db))
      playerRepository <- LiftIO[F].liftIO(PlayerRepository(dbConfig))
    } yield (playerRepository, httpConfig)

    (for {
      r <- Resource.make(x)(_ => Async[F].unit)
      server <- buildServer(r._2, r._1)
    } yield server).use((s: Server) => Async[F].never)

  }

  //    (for {
//      _ <- Resource.make(Logger[F].info(s"Starting health server on $port"))(_ =>
//        Logger[F].info(s"Stopping health server on $port"),
//      )
//
//      _ <- Resource.make(FetchRecommenderHttpConfig.applyF[F])
//
//      _ <- Resource.make(Logger[F].info(s"Received database endpoint ${dbConfig.endpoint}"))
//      server <- BlazeServerBuilder[F]
//        .bindHttp(8080, "0.0.0.0")
//        .withHttpApp(
//          Router(
//            "/" -> customRoute[F],
//          ).orNotFound,
//        )
//        .resource
//    } yield server).use(_ => MonadThrow[F].pure(ExitCode.Success))

  def buildServer[F[_]: Async: Logger: LiftIO: MonadThrow](
    httpConfig: HttpConfig,
    playerRepository: PlayerRepository,
  ): Resource[F, Server] =
    BlazeServerBuilder[F]
      .bindHttp(httpConfig.port.value, httpConfig.host)
      .withHttpApp(RecommenderServiceRoute.apply[F](playerRepository))
      .resource
}
