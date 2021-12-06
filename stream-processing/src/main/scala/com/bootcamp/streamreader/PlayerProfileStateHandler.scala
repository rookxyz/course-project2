package com.bootcamp.streamreader

import cats.effect.IO
import com.bootcamp.streamreader.domain._

trait PlayerProfileStateHandler {
  val state: PlayerState // TODO Check if this value needs to be defined
  def processNewPlayerProfiles(profiles: Seq[PlayerSessionProfile]): IO[Unit]
  def createPlayerSessionProfile(
      playerRounds: Seq[(PlayerId, PlayerGameRound)]
  ): IO[Seq[PlayerSessionProfile]]
}

object PlayerProfileStateHandler {
  def apply(other: PlayerState): PlayerProfileStateHandler =
    new PlayerProfileStateHandler {
      override val state: PlayerState = other // TODO check if this is OK

      override def processNewPlayerProfiles(
          playerProfiles: Seq[PlayerSessionProfile]
      ): IO[Unit] = {
        state.put(playerProfiles)
      }

      def createPlayerSessionProfile(
          playerRounds: Seq[(PlayerId, PlayerGameRound)]
      ): IO[Seq[PlayerSessionProfile]] = IO {
        playerRounds
          .groupBy(_._1)
          .map { case (playerId, playerRecords) =>
            val gamePlay: Map[GameType, GameTypeActivity] = playerRecords
              .map(_._2)
              .groupBy(_.gameType)
              .foldLeft(Map.empty[GameType, GameTypeActivity])((a, c) => {
                a + (c._1 -> GameTypeActivity(c._2))
              })
            PlayerSessionProfile(
              playerId,
              Cluster(5),
              // TODO perhaps cluster should be an Option, so that no need to fill it here
              PlayerGamePlay(gamePlay)
            )
          }
          .toSeq
      }
    }

}
