package com.bootcamp.recommenderservice

import com.bootcamp.domain.{GameType, PlayerId, PlayerSessionProfile}
import com.bootcamp.recommenderservice.CalculateSimilarity.{CalculateCosineSimilarity, normalize}

object GetPlayerRecommendations {
  val getPlayerFullActivityMap: (List[GameType], Seq[(PlayerId, Map[GameType, Long])]) => Map[PlayerId, Vector[
    Float,
  ]] = (gameTypes, playerActivity) =>
    playerActivity.toMap
      .mapValues(gamePlay =>
        gameTypes.foldLeft(Vector.empty[Long]) { (acc, cur) =>
          acc :+ gamePlay.getOrElse(cur, 0L)
        },
      )
      .mapValues(normalize(_))

  val getTopSimilarPlayers: (Vector[Float], Map[PlayerId, Vector[Float]], Int) => Map[PlayerId, Float] = (x, y, n) =>
    y
      .map { case (playerId, ratings) =>
        playerId -> CalculateCosineSimilarity.apply(ratings, x).getOrElse(0f)
      }
      .toList
      .sortBy { case (_, ratings) => ratings }(Ordering[Float].reverse)
      .take(n)
      .toMap

  def getAveragedGamePlay(allGameTypes: List[GameType]): Map[PlayerId, Vector[Float]] => List[(GameType, Float)] =
    topSimilarPlayersActivity =>
      allGameTypes.map { gameType =>
        val index = allGameTypes.indexOf(gameType)
        val n = topSimilarPlayersActivity.size.toFloat
        val avgRating = topSimilarPlayersActivity.map(_._2(index)).sum / n
        gameType -> avgRating
      }

  def getPlayerUnseenGameTypesSorted(playerRatings: Vector[Float]): List[(GameType, Float)] => List[GameType] =
    _.zip(playerRatings)
      .filter { case (_, rating) => rating == 0 }
      .sortBy { case (recommendations, _) => recommendations._2 }(Ordering[Float].reverse)
      .map { case (recommendations, _) => recommendations._1 } //(_._1._1)
      .toList

  def getPlayerGamePlayRounds: Seq[PlayerSessionProfile] => Seq[(PlayerId, Map[GameType, Long])] =
    _.map(profile =>
      (profile.playerId -> profile.gamePlay.gamePlay.map { case (gameType, gameTypeActivity) =>
        (gameType -> gameTypeActivity.gameRounds)
      }),
    )

  def getAllGameTypes: Seq[(PlayerId, Map[GameType, Long])] => List[GameType] =
    _.flatMap { case (_, gamePlay) => gamePlay.toSeq }
      .groupBy { case (playerId, _) => playerId }
      .map { case (gameType, playerData) => (gameType, playerData.map(_._2).sum) }
      .toList
      .sortBy { case (_, games) => games }(Ordering[Long].reverse)
      .map { case (gameType, _) => gameType }

  def apply(playerId: PlayerId, playersWithCluster: Seq[PlayerSessionProfile]): List[GameType] = {

    val playerGamePlayRounds: Seq[(PlayerId, Map[GameType, Long])] = getPlayerGamePlayRounds(playersWithCluster)

    val allGameTypes: List[GameType] = getAllGameTypes(playerGamePlayRounds)

    val allPlayerFullActivity: Map[PlayerId, Vector[Float]] =
      getPlayerFullActivityMap(allGameTypes, playerGamePlayRounds)

    val thisPlayerFullActivity: Vector[Float] = allPlayerFullActivity(playerId)

    val otherPlayersFullActivity: Map[PlayerId, Vector[Float]] = allPlayerFullActivity - playerId

    val topSimilar: Map[PlayerId, Float] = getTopSimilarPlayers(thisPlayerFullActivity, otherPlayersFullActivity, 50)

    val topSimilarPlayersActivity: Map[PlayerId, Vector[Float]] = otherPlayersFullActivity.filterKeys(topSimilar.keySet)

    println(s"Player actual played: ${playerGamePlayRounds.toList.filter(_._1 == playerId).map(_._2).toString()}")
    println(s"Player played: ${allGameTypes zip thisPlayerFullActivity}")
    println(s"Averages: ${getAveragedGamePlay(allGameTypes)(topSimilarPlayersActivity)}")
    println()
    println(
      s"Recommending: ${(getAveragedGamePlay(allGameTypes) andThen getPlayerUnseenGameTypesSorted(thisPlayerFullActivity))(
        topSimilarPlayersActivity,
      )}",
    )
    println()

    (getAveragedGamePlay(allGameTypes) andThen getPlayerUnseenGameTypesSorted(thisPlayerFullActivity))(
      topSimilarPlayersActivity,
    )
  }
}
