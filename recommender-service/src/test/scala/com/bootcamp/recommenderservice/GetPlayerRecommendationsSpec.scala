package com.bootcamp.recommenderservice

import com.bootcamp.domain.GameType._
import com.bootcamp.domain.{
  Cluster,
  GameType,
  GameTypeActivity,
  Money,
  PlayerGamePlay,
  PlayerId,
  PlayerSessionProfile,
  SeqNum,
}
import munit.FunSuite
import org.scalatest.matchers.should.Matchers

class GetPlayerRecommendationsSpec {}

class MySpec extends FunSuite with Matchers {

  test("Get player game play rounds test") {
    val input = Seq(
      PlayerSessionProfile(
        PlayerId("p1"),
        Cluster(1),
        SeqNum(0L),
        SeqNum(1L),
        PlayerGamePlay(
          Map(
            Baccarat -> GameTypeActivity(
              1,
              Money(111.11),
              Money(222.22),
            ),
            Roulette -> GameTypeActivity(
              1,
              Money(111.11),
              Money(222.22),
            ),
          ),
        ),
      ),
      PlayerSessionProfile(
        PlayerId("p2"),
        Cluster(1),
        SeqNum(0L),
        SeqNum(1L),
        PlayerGamePlay(
          Map(
            Baccarat -> GameTypeActivity(
              1,
              Money(BigDecimal(111.11)),
              Money(BigDecimal(222.22)),
            ),
          ),
        ),
      ),
      PlayerSessionProfile(
        PlayerId("p3"),
        Cluster(1),
        SeqNum(0L),
        SeqNum(1L),
        PlayerGamePlay(
          Map(
            Roulette -> GameTypeActivity(
              1,
              Money(BigDecimal(111.11)),
              Money(BigDecimal(222.22)),
            ),
            SpinForeverRoulette -> GameTypeActivity(
              4,
              Money(111.11),
              Money(222.22),
            ),
          ),
        ),
      ),
    )
    val expected = Seq(
      PlayerId("p1") -> Map(Baccarat -> 1, Roulette -> 1),
      PlayerId("p2") -> Map(Baccarat -> 1),
      PlayerId("p3") -> Map(Roulette -> 1, SpinForeverRoulette -> 4),
    )
    GetPlayerRecommendations.getPlayerGamePlayRounds(input) should contain allElementsOf expected
  }

  test("Get all game types list test") {
    val input: Seq[(PlayerId, Map[GameType, Long])] = Seq(
      PlayerId("p1") -> Map(Baccarat -> 1),
      PlayerId("p2") -> Map(Baccarat -> 2),
      PlayerId("p3") -> Map(Roulette -> 1, SpinForeverRoulette -> 4),
    )
    val expected = List(SpinForeverRoulette, Baccarat, Roulette)

    GetPlayerRecommendations.getAllGameTypes(input) shouldBe expected
  }

  test("Get all player full activity sparse array test") {
    val gameTypes = List(Baccarat, Roulette, SpinForeverRoulette)
    val playerGamePlayRounds: Seq[(PlayerId, Map[GameType, Long])] = Seq(
      PlayerId("p1") -> Map(Baccarat -> 1, Roulette -> 1),
      PlayerId("p2") -> Map(Baccarat -> 1),
      PlayerId("p3") -> Map(Roulette -> 1, SpinForeverRoulette -> 4),
    )
    val expected: Map[PlayerId, Array[Float]] = Map(
      PlayerId("p1") -> Array(1f, 1f, 0f),
      PlayerId("p2") -> Array(1f, 0f, 0f),
      PlayerId("p3") -> Array(0f, 0.25f, 1f),
    )
    GetPlayerRecommendations
      .getPlayerFullActivityMap(
        gameTypes,
        playerGamePlayRounds,
      )
      .mapValues(_.mkString(",")) should contain allElementsOf expected.mapValues(_.mkString(","))

  }

  test("Get top 2 similar players test") {
    val thisPlayerActivity = Array(1f, 0f, 0f)
    val otherPlayerActivity = Map(
      PlayerId("p2") -> Array(1f, 0f, 0f),
      PlayerId("p3") -> Array(0f, 0.25f, 1f),
      PlayerId("p4") -> Array(0.25f, 0f, 1f),
    )
    val expected = Map(
      PlayerId("p2") -> 1f,
      PlayerId("p4") -> 0.24f,
    )
    GetPlayerRecommendations
      .getTopSimilarPlayers(
        thisPlayerActivity,
        otherPlayerActivity,
        2,
      )
      .mapValues(v => math.round(v * 100)) should contain allElementsOf expected.mapValues(v => math.round(v * 100))
  }

  test("Calculate average game play test") {
    val allGameTypes = List(Baccarat, Roulette, SpinForeverRoulette)
    val otherPlayerActivity = Map(
      PlayerId("p4") -> Array(0.25f, 0f, 1f),
      PlayerId("p2") -> Array(1f, 0f, 0f),
    )
    val expected = List((Baccarat, 0.625), (Roulette, 0.0), (SpinForeverRoulette, 0.5))
    GetPlayerRecommendations.getAveragedGamePlay(allGameTypes)(
      otherPlayerActivity,
    ) should contain allElementsOf expected
  }
  test("Get player unseen game types sorted test") {
    val averagedGamePlay: List[(GameType, Float)] =
      List((Baccarat, 0.625f), (SpinForeverRoulette, 0.5f), (UltimateWinPoker, 0.0f), (Roulette, 0.0f))
    val thisPlayerActivity = Array(1f, 0f, 0f, 0f)
    val expected = List(SpinForeverRoulette, Roulette)

    GetPlayerRecommendations.getPlayerUnseenGameTypesSorted(thisPlayerActivity)(averagedGamePlay) shouldBe expected

  }

}
