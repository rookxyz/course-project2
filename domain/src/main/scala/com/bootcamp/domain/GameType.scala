package com.bootcamp.domain

import io.circe.generic.extras
import io.circe.{Decoder, Encoder}

sealed trait GameType

object GameType {
  case object Blackjack extends GameType

  case object Roulette extends GameType

  case object Baccarat extends GameType

  case object UltimateWinPoker extends GameType

  case object SpinForeverRoulette extends GameType

  case object NeverLoseBaccarat extends GameType

  case object UnknownGameType extends GameType // TODO Log warning in this case

  implicit val gameTypeDecoder: Decoder[GameType] =
    extras.semiauto
      .deriveEnumerationDecoder[GameType]
      .handleErrorWith(_ => Decoder.const(GameType.UnknownGameType))

  implicit val gameTypeEncoder: Encoder[GameType] =
    extras.semiauto.deriveEnumerationEncoder
}
