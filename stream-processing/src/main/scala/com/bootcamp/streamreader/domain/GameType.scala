package com.bootcamp.streamreader.domain
import io.circe.Codec
import io.circe.generic.extras

sealed trait GameType

object GameType {
  case object Blackjack extends GameType

  case object Roulette extends GameType

  case object Baccarat extends GameType

  case object UltimateWinPoker extends GameType

  case object SpinForeverRoulette extends GameType

  case object NeverLoseBaccarat extends GameType

  case object UnknownGameType extends GameType // TODO Log warning in this case

  implicit val gameTypeCodec: Codec[GameType] =
    extras.semiauto.deriveEnumerationCodec
  // TODO how to assign default UnknownGameType if new game type?
}
