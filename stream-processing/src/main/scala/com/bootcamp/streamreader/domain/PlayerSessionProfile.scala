package com.bootcamp.streamreader.domain

import cats.Monoid

final case class GameTypeActivity(
                             gameRounds: Long,
                             stakeEur: Money,
                             payoutEur: Money
                             )
final case class PlayerGamePlay(gamePlay: Map[GameType, GameTypeActivity])

object PlayerGamePlay {

  implicit val playerGamePlayAdditionMonoid: Monoid[PlayerGamePlay] =
    new Monoid[PlayerGamePlay] {
      override def empty: PlayerGamePlay = PlayerGamePlay(
        Map.empty[GameType, GameTypeActivity]
      )

      override def combine(
                            x: PlayerGamePlay,
                            y: PlayerGamePlay
                          ): PlayerGamePlay = ???
    }
}

final case class Cluster(value: Int) extends AnyVal

final case class PlayerSessionProfile(
                                       userId: PlayerId,
                                       playerCluster: Cluster,
                                       gamePlay: PlayerGamePlay
                                     )