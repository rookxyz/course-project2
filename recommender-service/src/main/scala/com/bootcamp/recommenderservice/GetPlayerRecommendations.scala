package com.bootcamp.recommenderservice

import com.bootcamp.domain.{GameType, PlayerId, PlayerSessionProfile}
import com.bootcamp.recommenderservice.CalculateSimilarity.CalculateCosineSimilarity

object GetPlayerRecommendations {
  val playerFullActivityMap: (List[GameType], Seq[(PlayerId, Map[GameType, Long])]) => Map[PlayerId, Array[
    Long,
  ]] = (x, y) =>
    y.map(i =>
      (i._1 -> x
        .map(gt => (gt -> i._2.getOrElse(gt, 0L)))
        .sortBy(_._1)
        .map(_._2)
        .toArray),
    ).toMap

  val topSimilarPlayers: (Array[Long], Map[PlayerId, Array[Long]], Int) => Map[PlayerId, Float] = (x, y, n) =>
    y
      .map(a => a._1 -> CalculateCosineSimilarity.apply(a._2, x).getOrElse(0L))
      .toList
      .sortBy(i => i._2)(Ordering[Float].reverse)
      .take(n)
      .toMap

  def averagedGamePlay(allGameTypes: List[GameType]): Map[PlayerId, Array[Long]] => List[(GameType, Float)] =
    topSimilarPlayersActivity =>
      allGameTypes.map { gt =>
        val indx = allGameTypes.indexOf(gt)
        val n = topSimilarPlayersActivity.size.toFloat
        val avgRating = topSimilarPlayersActivity.map { i =>
          val arr = CalculateSimilarity.normalize[Long](i._2)
          arr(indx)
        }.sum / n
        println(s"Caclulation N = $n, to sum ${topSimilarPlayersActivity.map(_._2(indx))}")
        gt -> avgRating
      }.toList

  def playerUnseenGameTypesSorted(x: Array[Long]): List[(GameType, Float)] => List[GameType] =
    _.zip(x.toList)
      .filter(_._2 == 0)
      .sortBy(_._1._2)(Ordering[Float].reverse)
      .map(_._1._1)
      .toList

  def x(playerId: PlayerId, playersWithCluster: Seq[PlayerSessionProfile]): Unit = {

    val playerGamePlayRounds: Seq[(PlayerId, Map[GameType, Long])] = playersWithCluster
      .map(profile => (profile.playerId -> profile.gamePlay.gamePlay.map(game => (game._1 -> game._2.gameRounds))))

    val allGameTypes: List[GameType] = playerGamePlayRounds
      .foldLeft(Set.empty[GameType])((acc, c) => acc ++ c._2.keySet)
      .toList

    val allPlayerFullActivity = playerFullActivityMap(allGameTypes, playerGamePlayRounds)

    val thisPlayerFullActivity: Array[Long] = allPlayerFullActivity(playerId) // TODO use sparse vector here

    val otherPlayersFullActivity: Map[PlayerId, Array[Long]] = allPlayerFullActivity - playerId

    val topSimilar = topSimilarPlayers(thisPlayerFullActivity, otherPlayersFullActivity, 50)

    val topSimilarPlayersActivity: Map[PlayerId, Array[Long]] = otherPlayersFullActivity.filterKeys(topSimilar.keySet)

    (averagedGamePlay(allGameTypes) andThen playerUnseenGameTypesSorted(thisPlayerFullActivity))(
      topSimilarPlayersActivity,
    )
  }
}
