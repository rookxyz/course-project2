package com.bootcamp.streamreader.domain

import com.bootcamp.streamreader.domain.PlayerGameRoundDomain.{GameType, Money, PlayerId}


final case class GameTypeActivity(
                             gameRounds: Long,
                             stakeEur: Money,
                             payoutEur: Money
                             )
final case class PlayerGamePlay(gamePlay: Map[GameType, GameTypeActivity])
final case class Cluster(value: Int) extends AnyVal

final case class PlayerSessionProfile(
                                       userId: PlayerId,
                                       playerCluster: Cluster,
                                       gamePlay: PlayerGamePlay
                                     )