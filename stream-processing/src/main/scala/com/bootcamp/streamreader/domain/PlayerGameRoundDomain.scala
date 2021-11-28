package com.bootcamp.streamreader.domain

import io.circe.generic.JsonCodec
import io.circe.{Decoder, HCursor}
import java.time.Instant

object PlayerGameRoundDomain {
  sealed trait GameType

  object GameType {
    case object Blackjack extends GameType

    case object Roulette extends GameType

    case object Baccarat extends GameType

    case object UltimateWinPoker extends GameType

    case object SpinForeverRoulette extends GameType

    case object NeverLoseBaccarat extends GameType

    case object UnknownGameType  extends GameType // TODO is this good approach?
  }


  final case class PlayerId(id: String) extends AnyVal

  final case class GameId(id: String) extends AnyVal

  final case class Money(amount: BigDecimal) extends AnyVal

  final case class TableId(id: String) extends AnyVal

  final case class PlayerGameRound(
                                    playerId: PlayerId,
                                    gameId: GameId,
                                    tableId: TableId,
                                    gameType: GameType,
                                    stakeEur: Money,
                                    payoutEur: Money,
                                    gameEndedTime: Instant
                                             )



  implicit val playerGameRoundDecoder: Decoder[PlayerGameRound] = (hCursor: HCursor) =>
    for {
      pl <- hCursor.get[String]("playerId")
      g <- hCursor.get[String]("gameId")
      t <- hCursor.get[String]("tableId")
      gt <- hCursor.get[String]("gameType")
      gto = gt.toLowerCase() match {
        case "roulette" => GameType.Roulette
        case "baccarat" => GameType.Baccarat
        case "blackjack" => GameType.Blackjack
        case _ => GameType.UnknownGameType
      }
      s <- hCursor.get[BigDecimal]("stakeEur")
      p <- hCursor.get[BigDecimal]("payoutEur")
      i <- hCursor.get[Instant]("gameEndedTime")

    } yield PlayerGameRound(
      PlayerId(pl),
      GameId(g),
      TableId(t),
      gto,
      Money(s),
      Money(p),
      i
    )

}
//import com.bootcamp.streamreader.domain.PlayerGameRoundDomain.Money
