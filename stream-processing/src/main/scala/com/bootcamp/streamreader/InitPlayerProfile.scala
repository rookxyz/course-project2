package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.streamreader.domain._

trait InitPlayerProfile {
  def apply(playerRounds: Seq[(PlayerId, PlayerGameRound)]): IO[Unit]
}

object InitPlayerProfile {
  def apply(updatePlayerState: UpdatePlayerProfile): InitPlayerProfile =
    new InitPlayerProfile {
      def apply(playerRounds: Seq[(PlayerId, PlayerGameRound)]): IO[Unit] =
        updatePlayerState.update(createPlayerSessionProfile(playerRounds))

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
              // TODO perhaps cluster should be an Option, so that no need to fill it here
              minSeqNum,
              maxSeqNum,
              PlayerGamePlay(gamePlay),
            )
          }
          .toSeq
    }
}
