package com.bootcamp.streamreader.domain

import cats.Semigroup

final case class GameTypeActivity(
    gameRounds: Long,
    stakeEur: Money,
    payoutEur: Money
)

object GameTypeActivity {
  def apply(playerRounds: Seq[PlayerGameRound]): GameTypeActivity = {
    val (rounds, stake, payout) = playerRounds.foldLeft(
      Tuple3[Long, Money, Money](0, Money(0), Money(0))
    )((a, c) =>
      (
        a._1 + 1,
        a._2 + c.stakeEur,
        a._3 + c.payoutEur
      )
    )
    GameTypeActivity(rounds, stake, payout)
  }
  implicit val gameTypeActivitySemigroup: Semigroup[GameTypeActivity] =
    new Semigroup[GameTypeActivity] {
      def combine(
          x: GameTypeActivity,
          y: GameTypeActivity
      ): GameTypeActivity = {
        GameTypeActivity(
          x.gameRounds + y.gameRounds,
          x.stakeEur + y.stakeEur,
          x.payoutEur + y.payoutEur
        )
      }
    }
}
final case class PlayerGamePlay(gamePlay: Map[GameType, GameTypeActivity])

//object PlayerGamePlay {
//
//  implicit val playerGamePlayAdditionMonoid: Monoid[PlayerGamePlay] =
//    new Monoid[PlayerGamePlay] {
//      override def empty: PlayerGamePlay = PlayerGamePlay(
//        Map.empty[GameType, GameTypeActivity]
//      )
//
//      override def combine(
//          x: PlayerGamePlay,
//          y: PlayerGamePlay
//      ): PlayerGamePlay = { ??? }
//    }
//}

final case class Cluster(value: Int) extends AnyVal

final case class PlayerSessionProfile(
    playerId: PlayerId,
    playerCluster: Cluster,
    gamePlay: PlayerGamePlay
)
