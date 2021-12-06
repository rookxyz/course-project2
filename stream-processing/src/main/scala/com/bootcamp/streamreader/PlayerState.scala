package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.domain.{PlayerId, PlayerSessionProfile}

class PlayerState(
    ref: Ref[IO, Map[PlayerId, PlayerSessionProfile]],
    playerRepository: PlayerRepository
) {
  /*
  Put updates the internal state and writes changed players to the repository
   */
  // TODO write changes to repository only periodically
  def put(playerProfiles: Seq[PlayerSessionProfile]): IO[Unit] = {
    for {
      p <- IO { playerProfiles.map(_.playerId) }
      s <- ref.get
      missing <- IO { p.filterNot(k => s.keySet.contains(k)) }
      repositoryDataForMissing <-
        playerRepository.readBySeqOfPlayerId(
          missing
        ) // TODO there could be an update while reading
      ss <- ref.updateAndGet(state => {
        state |+| (repositoryDataForMissing ++ playerProfiles) // Missing on the left side so that cluster data is accurate
          .map(item => (item.playerId -> item))
          .toMap
      })
      ns <- ref.get
      _ <- IO { println(ns) }
      changedProfiles = ss
        .filterKeys(k => p.contains(k))
        .values
        .toSeq
      _ <- playerRepository.store(changedProfiles)
    } yield ()
  }

  // get
}
