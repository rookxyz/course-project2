package com.bootcamp.streamreader.domain

import cats.Semigroup
import cats.implicits._
import io.circe.{Codec, Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.extras
import io.circe.parser.decode
import io.circe.syntax.EncoderOps

final case class GameTypeActivity(
  gameRounds: Long,
  stakeEur: Money,
  payoutEur: Money,
)

object GameTypeActivity {
  def apply(playerRounds: Seq[PlayerGameRound]): GameTypeActivity = {
    val (rounds, stake, payout) = playerRounds.foldLeft(
      Tuple3[Long, Money, Money](0, Money(0), Money(0)),
    )((a, c) =>
      (
        a._1 + 1,
        a._2 + c.stakeEur,
        a._3 + c.payoutEur,
      ),
    )
    GameTypeActivity(rounds, stake, payout)
  }
  implicit val gameTypeActivitySemigroup: Semigroup[GameTypeActivity] =
    new Semigroup[GameTypeActivity] {
      def combine(
        x: GameTypeActivity,
        y: GameTypeActivity,
      ): GameTypeActivity =
        GameTypeActivity(
          x.gameRounds + y.gameRounds,
          x.stakeEur + y.stakeEur,
          x.payoutEur + y.payoutEur,
        )
    }

  import CommonCodecs._
  implicit val gameTypeActivityDecoder: Decoder[GameTypeActivity] =
    deriveDecoder
  implicit val gameTypeActivityEncoder: Encoder[GameTypeActivity] =
    deriveEncoder
}
final case class PlayerGamePlay(gamePlay: Map[GameType, GameTypeActivity])

object PlayerGamePlay {
  val Empty: PlayerGamePlay = PlayerGamePlay(
    Map.empty[GameType, GameTypeActivity],
  )

  implicit val playerGamePlayAdditionSemigroup: Semigroup[PlayerGamePlay] =
    new Semigroup[PlayerGamePlay] {
      override def combine(
        x: PlayerGamePlay,
        y: PlayerGamePlay,
      ): PlayerGamePlay =
        PlayerGamePlay(
          Semigroup[Map[GameType, GameTypeActivity]]
            .combine(x.gamePlay, y.gamePlay),
        )
    }
  import GameType._
  import GameTypeActivity._
  implicit val gameTypeKeyDecoder = new KeyDecoder[GameType] {
    override def apply(key: String): Option[GameType] = decode[GameType](key).toOption
  }
  implicit val gameTypeKeyEncoder = new KeyEncoder[GameType] {
    override def apply(key: GameType): String = key.asJson.toString()
  }

  implicit val playerGamePlayDecoder: Decoder[PlayerGamePlay] =
    deriveDecoder

  implicit val playerGamePlayEncoder: Encoder[PlayerGamePlay] =
    deriveEncoder
}

final case class Cluster(value: Int) extends AnyVal

object Cluster {
  val Default: Cluster = Cluster(0)
  implicit final val ClusterCodec: Codec[Cluster] =
    extras.semiauto.deriveUnwrappedCodec
}

final case class PlayerSessionProfile(
  playerId: PlayerId,
  playerCluster: Cluster,
  firstSeqNum: SeqNum,
  lastSeqNum: SeqNum,
  gamePlay: PlayerGamePlay,
)

object PlayerSessionProfile {
  implicit val playerSessionProfileSemigroup: Semigroup[PlayerSessionProfile] =
    new Semigroup[PlayerSessionProfile] {
      override def combine(
        x: PlayerSessionProfile,
        y: PlayerSessionProfile,
      ): PlayerSessionProfile =
        PlayerSessionProfile(
          x.playerId,
          x.playerCluster,
          x.firstSeqNum,
          y.lastSeqNum,
          x.gamePlay |+| y.gamePlay,
        )
    }

  import Cluster._
  import PlayerGamePlay._
  import GameTypeActivity._
  import CommonCodecs._

  implicit val playerSessionProfileDecoder: Decoder[PlayerSessionProfile] =
    deriveDecoder
  implicit val playerSessionProfileEncoder: Encoder[PlayerSessionProfile] =
    deriveEncoder
}
