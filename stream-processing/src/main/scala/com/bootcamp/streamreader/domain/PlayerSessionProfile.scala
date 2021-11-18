package com.bootcamp.streamreader.domain

// TODO review definitions
final case class GameTypeActivity(
                             gameRounds: Long,
                             stakeEur: Money,
                             payoutEur: Money
                             )
final case class PlayerGamePlay(gamePlay: Map[GameType, GameTypeActivity])

final case class PlayerSessionProfile(
  userId: UserId,
  gamePlay: PlayerGamePlay
                                     )