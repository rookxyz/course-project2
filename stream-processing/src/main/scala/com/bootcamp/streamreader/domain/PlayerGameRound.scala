package com.bootcamp.streamreader.domain

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.time.Instant

final case class PlayerGameRound(
    playerId: PlayerId,
    gameId: GameId,
    tableId: TableId,
    gameType: GameType,
    stakeEur: Money,
    payoutEur: Money,
    gameEndedTime: Instant
)

object PlayerGameRound {
  import CommonCodecs._
  implicit val playerGameRoundDecoder: Decoder[PlayerGameRound] =
    deriveDecoder
}