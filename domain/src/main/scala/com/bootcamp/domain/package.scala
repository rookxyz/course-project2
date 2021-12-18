package com.bootcamp.domain

import io.circe.generic.extras
import io.circe.{Codec, Decoder, Encoder}

import java.time.Instant
import scala.util.Try

final case class PlayerId(id: String) extends AnyVal

final case class GameId(id: String) extends AnyVal

final case class TableId(id: String) extends AnyVal

final case class Money(amount: BigDecimal) extends AnyVal {
  def +(other: Money): Money = Money(amount + other.amount)
}

final case class SeqNum(num: Long) extends AnyVal {
  def isNext(other: SeqNum): Boolean = (num + 1) == other.num
}

object CommonCodecs {
  implicit final val playerIdCodec: Codec[PlayerId] =
    extras.semiauto.deriveUnwrappedCodec
  implicit final val gameIdCodec: Codec[GameId] =
    extras.semiauto.deriveUnwrappedCodec
  implicit final val tableIdCodec: Codec[TableId] =
    extras.semiauto.deriveUnwrappedCodec
  implicit final val moneyCodec: Codec[Money] =
    extras.semiauto.deriveUnwrappedCodec
  implicit val encodeInstant: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](_.toString)
  implicit val decodeInstant: Decoder[Instant] =
    Decoder.decodeString.emapTry { str =>
      Try(Instant.parse(str))
    }
  implicit final val seqNumCodec: Codec[SeqNum] =
    extras.semiauto.deriveUnwrappedCodec
}
