package com.bootcamp.recommenderservice

import com.bootcamp.domain.{GameType, PlayerId, PlayerSessionProfile}
import com.bootcamp.recommenderservice.CalculateSimilarity.{CalculateCosineSimilarity, normalize}

object GetPlayerRecommendations {
  val getPlayerFullActivityMap: (List[GameType], Seq[(PlayerId, Map[GameType, Long])]) => Map[PlayerId, Array[
    Float,
  ]] = (x, y) =>
    y.toMap
      .mapValues(i =>
        x
          .map(gameType => (gameType -> i.getOrElse(gameType, 0L)))
          .toMap
          .values
          .toArray[Long],
      )
      .mapValues(normalize(_))

  val getTopSimilarPlayers: (Array[Float], Map[PlayerId, Array[Float]], Int) => Map[PlayerId, Float] = (x, y, n) =>
    y
      .map(a => a._1 -> CalculateCosineSimilarity.apply(a._2, x).getOrElse(0f))
      .toList
      .sortBy((i: (PlayerId, Float)) => i._2)(Ordering[Float].reverse)
      .take(n)
      .toMap

  def getAveragedGamePlay(allGameTypes: List[GameType]): Map[PlayerId, Array[Float]] => List[(GameType, Float)] =
    topSimilarPlayersActivity =>
      allGameTypes.map { gameType =>
        val index = allGameTypes.indexOf(gameType)
        val n = topSimilarPlayersActivity.size.toFloat
        val avgRating = topSimilarPlayersActivity.map(_._2(index)).sum / n
        gameType -> avgRating
      }

  def getPlayerUnseenGameTypesSorted(x: Array[Float]): List[(GameType, Float)] => List[GameType] =
    _.zip(x.toList)
      .filter(_._2 == 0)
      .sortBy(_._1._2)(Ordering[Float].reverse)
      .map(_._1._1)
      .toList

  def getPlayerGamePlayRounds: Seq[PlayerSessionProfile] => Seq[(PlayerId, Map[GameType, Long])] =
    _.map(profile => (profile.playerId -> profile.gamePlay.gamePlay.map(game => (game._1 -> game._2.gameRounds))))

  def getAllGameTypes: Seq[(PlayerId, Map[GameType, Long])] => List[GameType] =
    _.flatMap(i => i._2.toSeq)
      .groupBy(_._1)
      .map { i => (i._1, i._2.map(_._2).sum) }
      .toList
      .sortBy(_._2)(Ordering[Long].reverse)
      .map(_._1)

  def apply(playerId: PlayerId, playersWithCluster: Seq[PlayerSessionProfile]): List[GameType] = {

    val playerGamePlayRounds: Seq[(PlayerId, Map[GameType, Long])] = getPlayerGamePlayRounds(playersWithCluster)

    val allGameTypes: List[GameType] = getAllGameTypes(playerGamePlayRounds)

    val allPlayerFullActivity: Map[PlayerId, Array[Float]] =
      getPlayerFullActivityMap(allGameTypes, playerGamePlayRounds)

    val thisPlayerFullActivity: Array[Float] = allPlayerFullActivity(playerId)

    val otherPlayersFullActivity: Map[PlayerId, Array[Float]] = allPlayerFullActivity - playerId

    val topSimilar: Map[PlayerId, Float] = getTopSimilarPlayers(thisPlayerFullActivity, otherPlayersFullActivity, 50)

    val topSimilarPlayersActivity: Map[PlayerId, Array[Float]] = otherPlayersFullActivity.filterKeys(topSimilar.keySet)

    (getAveragedGamePlay(allGameTypes) andThen getPlayerUnseenGameTypesSorted(thisPlayerFullActivity))(
      topSimilarPlayersActivity,
    )
  }
}
