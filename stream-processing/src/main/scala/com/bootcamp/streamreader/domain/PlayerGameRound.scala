package com.bootcamp.streamreader.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant

final case class PlayerGameRound(
  playerId: PlayerId,
  gameId: GameId,
  tableId: TableId,
  gameType: GameType,
  stakeEur: Money,
  payoutEur: Money,
  gameEndedTime: Instant,
  seqNr: Int, // TODO change reated usages
)

object PlayerGameRound {
  import CommonCodecs._
  implicit val playerGameRoundDecoder: Decoder[PlayerGameRound] =
    deriveDecoder
  implicit val playerGameRoundEncoder: Encoder[PlayerGameRound] =
    deriveEncoder
}
