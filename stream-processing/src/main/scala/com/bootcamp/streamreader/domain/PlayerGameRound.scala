package com.bootcamp.streamreader.domain

import java.time.Instant


sealed trait GameType

object GameType {
  case object Blackjack extends GameType
  case object Roulette extends GameType
  case object Baccarat extends GameType
  case object UltimateWinPoker extends GameType
  case object SpinForeverRoulette extends GameType
  case object NeverLoseBaccarat extends GameType
}

// TODO confirm which do indeed need to be final
final case class UserId(id: String) extends AnyVal
final case class GameId(id: String) extends AnyVal
case class Money(amount: Double) extends AnyVal
final case class TableId(id: String) extends AnyVal

class PlayerGameRound(
  userId: UserId,
  gameId: GameId, // TODO not sure I will need this
  tableId: TableId, // TODO not sure I will need this
  gameType: GameType,
  stakeEur: Money,
  payoutEur: Money,
  gameEndedTime: Instant  // TODO not sure I will need this
                     )
