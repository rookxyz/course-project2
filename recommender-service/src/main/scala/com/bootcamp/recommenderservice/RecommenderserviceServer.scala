package com.bootcamp.recommenderservice

import cats.effect.unsafe.implicits.{global => globalz}
import cats.effect.{ExitCode, IO}
import com.bootcamp.config.FetchApplicationConfig
import com.bootcamp.domain._
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.recommenderservice.CalculateSimilarity.CalculateCosineSimilarity
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.io.{GET, Ok}
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.dsl.io._
import org.http4s.server.Router

import scala.collection.immutable
import scala.concurrent.ExecutionContext.global
import scala.math.Numeric.LongIsIntegral

object RecommenderserviceServer {

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
//        response <- Ok(playersWithCluster.toString())
        _ <- IO(for (elem <- playersWithCluster) println(elem))
        _ = if (!playersWithCluster.exists(p => p.playerId == playerIdObj)) {
          IO.raiseError(new Throwable)
        } // TODO need to terminate here if not found
        playerGamePlayRounds = playersWithCluster
          .map(profile => (profile.playerId -> profile.gamePlay.gamePlay.map(game => (game._1 -> game._2.gameRounds))))
        allGameTypes: List[GameType] = playerGamePlayRounds
          .foldLeft(Set.empty[GameType])((acc, c) => acc ++ c._2.keySet)
          .toList
        playerFullActivityMap: Map[PlayerId, Array[Long]] = playerGamePlayRounds
          .map(i =>
            (i._1 -> allGameTypes
              .map(gt => (gt -> i._2.getOrElse(gt, 0L)))
              .sortBy(_._1)
              .map(_._2)
              .toArray),
          )
          .toMap
        playerActivity: Option[Array[Long]] = playerFullActivityMap.get(playerIdObj)
        otherPlayersActivity = playerFullActivityMap - playerIdObj
        topSimilarPlayers = otherPlayersActivity
          .map(a => a._1 -> CalculateCosineSimilarity.apply(a._2, playerActivity.get))
          .toList
          .sortBy(i => i._2)(Ordering[Float].reverse)
          .take(50)
          .toMap // TODO make configurable number of similar players
        topSimilarPlayersActivity: Map[PlayerId, Array[Long]] = otherPlayersActivity.filterKeys(
          topSimilarPlayers.keySet,
        )
        averagedGamePlay: List[(GameType, Float)] = allGameTypes.map { gt =>
          val indx = allGameTypes.indexOf(gt)
          val n = topSimilarPlayersActivity.size.toFloat
          val avgRating = topSimilarPlayersActivity.map { i =>
            val arr = CalculateSimilarity.normalize[Long](i._2)
            arr(indx)
          }.sum / n
          println(s"Caclulation N = $n, to sum ${topSimilarPlayersActivity.map(_._2(indx))}")
          gt -> avgRating
        }
        _ <- IO {
          println(s"Player full activity: ${playerFullActivityMap.keys}")
          println(s"Player full activity: ${playerFullActivityMap.values.foreach(_.mkString("Array(", ", ", ")"))}")
          println(s"Other players activity ${otherPlayersActivity.keys}")
          println(allGameTypes)
          println(s"Average activity: $averagedGamePlay")
          println(s"Player activity: ${playerActivity.get.mkString("Array(", ", ", ")")}")
        }
        playerUnseenGameTypesSorted = averagedGamePlay
          .zip(playerActivity.get.toList)
          .filter(_._2 == 0)
          .sortBy(_._1._2)(Ordering[Float].reverse)
          .map(_._1._1)
        responseJson: Json = playerUnseenGameTypesSorted.asJson
        response <- Ok(responseJson.noSpaces)
      } yield response).handleErrorWith(_ => Ok(s"null"))
    }

  private val httpApp = (r: PlayerRepository) => Router("/" -> recommenderService(r)).orNotFound

  def run(args: List[String]): IO[ExitCode] =
    for {
      config <- FetchApplicationConfig.apply[IO]
      dbConfig = config.db
      playerRepository = PlayerRepository(dbConfig)
      httpConfig = config.http
      _ <- BlazeServerBuilder[IO](global)
        .bindHttp(httpConfig.port.value, httpConfig.host)
        .withHttpApp(httpApp(playerRepository))
        .serve
        .compile
        .drain

    } yield ExitCode.Success

}
