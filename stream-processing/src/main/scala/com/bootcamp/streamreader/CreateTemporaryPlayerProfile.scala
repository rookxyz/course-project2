package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import com.bootcamp.domain.{
  Cluster,
  GameType,
  GameTypeActivity,
  PlayerGamePlay,
  PlayerGameRound,
  PlayerId,
  PlayerSessionProfile,
}

import java.time.{Instant, ZoneId}

trait CreateTemporaryPlayerProfile {
  def apply(playerRounds: Seq[(PlayerId, PlayerGameRound)]): IO[Seq[PlayerSessionProfile]]
}

object CreateTemporaryPlayerProfile {
  def apply(time: IO[Option[Instant]]): CreateTemporaryPlayerProfile =
    new CreateTemporaryPlayerProfile {
      def apply(
        playerRounds: Seq[(PlayerId, PlayerGameRound)],
      ): IO[Seq[PlayerSessionProfile]] =
        for {
          nowTime <- time
          now <- IO.realTimeInstant.map(t => nowTime.getOrElse(t))
        } yield createPlayerSessionProfile(playerRounds, now)

      private def createPlayerSessionProfile(
        playerRounds: Seq[(PlayerId, PlayerGameRound)],
        now: Instant,
      ): Seq[PlayerSessionProfile] =
        playerRounds
          .groupBy(_._1)
          .map { case (playerId, playerRecords) =>
            val minSeqNum = playerRecords.map(_._2.seqNum).minBy(_.num)
            val maxSeqNum = playerRecords.map(_._2.seqNum).maxBy(_.num)
            val gamePlay: Map[GameType, GameTypeActivity] = playerRecords
              .map(_._2)
              .groupBy(_.gameType)
              .foldLeft(Map.empty[GameType, GameTypeActivity]) { (a, c) =>
                a + (c._1 -> GameTypeActivity(c._2))
              }
            PlayerSessionProfile(
              playerId,
              Cluster.Default,
              minSeqNum,
              maxSeqNum,
              now.atZone(ZoneId.of("UTC")).toEpochSecond,
              PlayerGamePlay(gamePlay),
            )
          }
          .toSeq
    }
}
