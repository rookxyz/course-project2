package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.domain.{PlayerId, PlayerSessionProfile}

// TODO change to trait + Object

class UpdatePlayerProfile(
  ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]],
  playerRepository: PlayerRepository,
) {
  /*
  Put updates the internal state and writes changed players to the repository
   */
  // TODO write changes to repository only periodically
  def apply(playerProfiles: Seq[PlayerSessionProfile]): IO[Unit] =
    for {
      currentState <- ref.get
      playerIds = playerProfiles.map(_.playerId)
      missing = playerIds.filterNot(k => currentState.keySet.contains(k))
      repositoryDataForMissing <-
        playerRepository.readByPlayerIds(
          missing,
        )
      updatedState <- ref.updateAndGet { state =>
        state |+| (repositoryDataForMissing ++ playerProfiles) // Missing on the left side so that cluster data is accurate
          .map(item => (item.playerId -> item))
          .toMap
      }
      currentState <- ref.get
      _ = println(s"currentState ${currentState}")
      _ = println(s"updatedState ${updatedState}")
      changedProfiles = updatedState
        .filterKeys(k => playerIds.contains(k))
        .values
        .toSeq
      _ <- playerRepository.store(changedProfiles)
    } yield ()

  // get
}
