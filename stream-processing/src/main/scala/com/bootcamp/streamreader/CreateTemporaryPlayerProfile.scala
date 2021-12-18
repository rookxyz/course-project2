package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.domain.{
  Cluster,
  GameType,
  GameTypeActivity,
  PlayerGamePlay,
  PlayerGameRound,
  PlayerId,
  PlayerSessionProfile,
}
import com.bootcamp.domain._

trait CreateTemporaryPlayerProfile {
  def apply(playerRounds: Seq[(PlayerId, PlayerGameRound)]): IO[Seq[PlayerSessionProfile]]
}

object CreateTemporaryPlayerProfile {
  def apply: CreateTemporaryPlayerProfile =
    new CreateTemporaryPlayerProfile {
      def apply(playerRounds: Seq[(PlayerId, PlayerGameRound)]): IO[Seq[PlayerSessionProfile]] =
        IO.pure(createPlayerSessionProfile(playerRounds))

      private def createPlayerSessionProfile(
        playerRounds: Seq[(PlayerId, PlayerGameRound)],
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
              PlayerGamePlay(gamePlay),
            )
          }
          .toSeq
    }
}
