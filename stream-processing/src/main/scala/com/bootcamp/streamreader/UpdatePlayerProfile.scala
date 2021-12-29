package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import cats.effect.kernel.Ref
import com.bootcamp.domain.{PlayerId, PlayerSessionProfile}
import com.bootcamp.playerrepository.PlayerRepository
import com.bootcamp.domain.PlayerSessionProfile

trait UpdatePlayerProfile {
  def apply(profiles: Seq[PlayerSessionProfile]): IO[Unit]
}

object UpdatePlayerProfile {
  def apply(
    ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]],
    playerRepository: PlayerRepository,
  ): UpdatePlayerProfile =
    new UpdatePlayerProfile {
      def apply(playerProfiles: Seq[PlayerSessionProfile]): IO[Unit] =
        for {
          currentState <- ref.get
          playerIds = playerProfiles.map(_.playerId)
          playerIdsMissingInState = playerIds.filterNot(k => currentState.keySet.contains(k))
          playerIdsOutOfSequence = playerProfiles.flatMap { profile =>
            for {
              currentProfile <- currentState.get(profile.playerId)
              lastSeqNum = currentProfile.lastSeqNum
              firstSeqNum = profile.firstSeqNum
              if !lastSeqNum.isNext(firstSeqNum)
            } yield (profile.playerId)
          }
          // TODO player list for players with outdated state (last update too long ago)
          repositoryDataForMissing <-
            playerRepository.readByPlayerIds(
              playerIdsMissingInState ++ playerIdsOutOfSequence,
            )
          updatedState <- ref.updateAndGet { state =>
            lazy val missingPlayerProfilesMap = repositoryDataForMissing
              .map(i => i.playerId -> i)
              .toMap
            lazy val playerProfilesMap = playerProfiles
              .map(i => i.playerId -> i)
              .toMap
            (state -- playerIdsOutOfSequence) |+| missingPlayerProfilesMap |+| playerProfilesMap
          }
          changedProfiles = updatedState
            .filterKeys(k => playerIds.contains(k))
            .values
            .toSeq
          _ <- playerRepository.store(changedProfiles)
        } yield ()
    }
}
