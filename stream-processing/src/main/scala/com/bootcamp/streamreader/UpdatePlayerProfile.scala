package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.domain.{PlayerId, PlayerSessionProfile}

trait UpdatePlayerProfile {
  def update(profiles: Seq[PlayerSessionProfile]): IO[Unit]
}

object UpdatePlayerProfile {
  def apply(
    ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]],
    playerRepository: PlayerRepository,
  ): UpdatePlayerProfile =
    new UpdatePlayerProfile {
      def update(playerProfiles: Seq[PlayerSessionProfile]): IO[Unit] =
        for {
          currentState <- ref.get
          _ <- IO(println(s"Current state: $currentState"))
          playerIds = playerProfiles.map(_.playerId)
          playerIdsMissingInState = playerIds.filterNot(k => currentState.keySet.contains(k))
          playerIdsOutOfSequence = playerProfiles.flatMap { profile =>
            for {
              currentProfile <- currentState.get(profile.playerId)
              lastSeqNum = currentProfile.lastSeqNum
              firstSeqNum = profile.firstSeqNum
              if lastSeqNum.isNext(firstSeqNum)
            } yield (profile.playerId)
          }
          repositoryDataForMissing <-
            playerRepository.readByPlayerIds(
              playerIdsMissingInState ++ playerIdsOutOfSequence,
            )
          updatedState <- ref.updateAndGet { state =>
            println(s"Current state: $state")
            println(s"missing players in state: $playerIdsMissingInState")
            println(s"Repository data for missing: $repositoryDataForMissing")
            println(s"Player profiles to be added: $playerProfiles")

            //     ((state -- playerIdsOutOfSequence) ++ repositoryDataForMissing) |+| playerProfiles // Missing on the left side so that cluster data is accurate
            lazy val missingPlayerProfilesMap = repositoryDataForMissing
              .map(i => i.playerId -> i)
              .toMap
            lazy val playerProfilesMap = playerProfiles
              .map(i => i.playerId -> i)
              .toMap
            (state -- playerIdsOutOfSequence) |+| missingPlayerProfilesMap |+| playerProfilesMap
          }
          currentState <- ref.get // TODO remove debug printlns
          _ = println(s"currentState ${currentState}")
          _ = println(s"updatedState ${updatedState}")
          changedProfiles = updatedState
            .filterKeys(k => playerIds.contains(k))
            .values
            .toSeq
          _ <- playerRepository.store(changedProfiles)
        } yield ()
    }
}
