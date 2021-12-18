package com.bootcamp.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import CommonCodecs._

import java.time.Instant

final case class PlayerGameRound(
  playerId: PlayerId,
  gameId: GameId,
  tableId: TableId,
  gameType: GameType,
  stakeEur: Money,
  payoutEur: Money,
  gameEndedTime: Instant,
  seqNum: SeqNum,
)

object SeqNumber {
  val Default: SeqNum = SeqNum(0L)
}

object PlayerGameRound {
  implicit val playerGameRoundDecoder: Decoder[PlayerGameRound] =
    deriveDecoder
  implicit val playerGameRoundEncoder: Encoder[PlayerGameRound] =
    deriveEncoder
}
