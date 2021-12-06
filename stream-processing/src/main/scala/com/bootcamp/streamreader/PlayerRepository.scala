package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import com.bootcamp.streamreader.domain._

import scala.collection.concurrent.TrieMap

trait PlayerRepository {
  def store(data: Seq[PlayerSessionProfile]): IO[Unit]

  def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]]

  def readBySeqOfPlayerId(
      playerIds: Seq[PlayerId]
  ): IO[Seq[PlayerSessionProfile]]

  def readClusterByPlayerId(
      playerId: PlayerId
  ): Option[Cluster] // TODO should this be in IO, if so
  // How to call it from within another PlayerRepository function
}

trait InMemPlayerRepository extends PlayerRepository {
  val storage: TrieMap[PlayerId, PlayerSessionProfile]
}

object PlayerRepository {
  def apply(): PlayerRepository = {
    PlayerRepository.inMem // TODO: temporary, use dynamo db
  }

  def empty: PlayerRepository = new PlayerRepository {
    def store(data: Seq[PlayerSessionProfile]): IO[Unit] = IO.unit

    def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
      IO.pure(None)

    def readBySeqOfPlayerId(
        playerIds: Seq[PlayerId]
    ): IO[Seq[PlayerSessionProfile]] =
      IO.pure(Seq.empty[PlayerSessionProfile])

    def readClusterByPlayerId(playerId: PlayerId): Option[Cluster] =
      Option.empty[Cluster]
  }

  def inMem: InMemPlayerRepository = new InMemPlayerRepository {
    val storage: TrieMap[PlayerId, PlayerSessionProfile] =
      TrieMap.empty[PlayerId, PlayerSessionProfile]

    def store(data: Seq[PlayerSessionProfile]): IO[Unit] =
      IO {
        data.foreach { d =>
          storage.put(d.playerId, d)
        }
      }

    def readByPlayerId(
        playerId: PlayerId
    ): IO[Option[PlayerSessionProfile]] = {
      IO {
        storage.get(playerId)
      }
    }

    /*
Read multiple players by Id, if playerId is not in repository,
then get cluster for playerId and create an empty PlayerSessionProfile
     */
    def readBySeqOfPlayerId(
        playerIds: Seq[PlayerId]
    ): IO[Seq[PlayerSessionProfile]] = {
      playerIds.toList
        .traverse((p: PlayerId) => readByPlayerId(p))
        .map(l => playerIds zip l)
        .map(i =>
          i.map {
            case (playerId, None) => {
              val cluster = readClusterByPlayerId(playerId)
              PlayerSessionProfile(
                playerId,
                cluster.getOrElse(Cluster(0)),
                PlayerGamePlay.empty()
              )
            }
            case (_, Some(profile)) => profile
          }
        )

    }

    def readClusterByPlayerId(playerId: PlayerId): Option[Cluster] =
      // TODO replace with actual implementation
      Some(Cluster(1))
  }
}
