package com.bootcamp.streamreader.domain

import cats.Semigroup
import cats.implicits._

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

object PlayerGamePlay {
  def empty(): PlayerGamePlay = PlayerGamePlay(
    Map.empty[GameType, GameTypeActivity]
  )

  implicit val playerGamePlayAdditionSemigroup: Semigroup[PlayerGamePlay] =
    new Semigroup[PlayerGamePlay] {
      override def combine(
          x: PlayerGamePlay,
          y: PlayerGamePlay
      ): PlayerGamePlay = {
        PlayerGamePlay(
          Semigroup[Map[GameType, GameTypeActivity]]
            .combine(x.gamePlay, y.gamePlay)
        )
      }
    }
}

final case class Cluster(value: Int) extends AnyVal

final case class PlayerSessionProfile(
    playerId: PlayerId,
    playerCluster: Cluster,
    gamePlay: PlayerGamePlay
)

object PlayerSessionProfile {
  implicit val playerSessionProfileSemigroup: Semigroup[PlayerSessionProfile] =
    new Semigroup[PlayerSessionProfile] {
      override def combine(
          x: PlayerSessionProfile,
          y: PlayerSessionProfile
      ): PlayerSessionProfile =
        PlayerSessionProfile(
          x.playerId,
          x.playerCluster,
          x.gamePlay |+| y.gamePlay
        )
    }
}
