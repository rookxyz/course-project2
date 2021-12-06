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
        updatePlayerState(createPlayerSessionProfile(playerRounds))

      private def createPlayerSessionProfile(
        playerRounds: Seq[(PlayerId, PlayerGameRound)],
      ): Seq[PlayerSessionProfile] =
        playerRounds
          .groupBy(_._1)
          .map { case (playerId, playerRecords) =>
            val gamePlay: Map[GameType, GameTypeActivity] = playerRecords
              .map(_._2)
              .groupBy(_.gameType)
              .foldLeft(Map.empty[GameType, GameTypeActivity]) { (a, c) =>
                a + (c._1 -> GameTypeActivity(c._2))
              }
            PlayerSessionProfile(
              playerId,
              Cluster(0),
              // TODO perhaps cluster should be an Option, so that no need to fill it here
              PlayerGamePlay(gamePlay),
            )
          }
          .toSeq
    }
}
