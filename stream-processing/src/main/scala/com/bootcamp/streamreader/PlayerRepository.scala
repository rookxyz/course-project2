package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey, Table}
import com.bootcamp.streamreader.domain._
import io.circe.syntax.EncoderOps
import io.circe.parser.decode

import CompressString._
import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

trait PlayerRepository {
  def store(data: Seq[PlayerSessionProfile]): IO[Unit]

  def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]]

  def readByPlayerIds(
    playerIds: Seq[PlayerId],
  ): IO[Seq[PlayerSessionProfile]]

  def readClusterByPlayerId(
    playerId: PlayerId,
  ): IO[Option[Cluster]]
}

trait InMemPlayerRepository extends PlayerRepository {
  val storage: TrieMap[PlayerId, PlayerSessionProfile]
}

object PlayerRepository {
  def apply(): PlayerRepository =
    PlayerRepository.inMem // TODO: temporary, use dynamo db

  def apply(config: DbConfig): PlayerRepository =
    PlayerRepository.dynamoDb(config)

  def inMem: InMemPlayerRepository = new InMemPlayerRepository {
    val storage: TrieMap[PlayerId, PlayerSessionProfile] =
      TrieMap.empty[PlayerId, PlayerSessionProfile]

    def store(data: Seq[PlayerSessionProfile]): IO[Unit] =
      IO {
        data.foreach { d =>
          storage.put(d.playerId, d)
        }
      }

    def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
      IO(storage.get(playerId))

    def readByPlayerIds(
      playerIds: Seq[PlayerId],
    ): IO[Seq[PlayerSessionProfile]] =
      playerIds.toList.traverse { playerId =>
        for {
          playerProfile <- readByPlayerId(playerId)
          playerCluster <- readClusterByPlayerId(playerId)
        } yield PlayerSessionProfile(
          playerId,
          playerCluster.getOrElse(Cluster.Default),
          playerProfile.map(_.firstSeqNum).getOrElse(SeqNumber.Default),
          playerProfile.map(_.lastSeqNum).getOrElse(SeqNumber.Default),
          playerProfile.map(_.gamePlay).getOrElse(PlayerGamePlay.Empty),
        )
      }

    def readClusterByPlayerId(playerId: PlayerId): IO[Option[Cluster]] =
      // TODO replace with actual implementation
      IO.pure { Some(Cluster(1)) }
  }

  def dynamoDb(config: DbConfig): PlayerRepository = new PlayerRepository {
    implicit val db: DynamoDB = new DynamoDB(
      AmazonDynamoDBClientBuilder.standard
        .withEndpointConfiguration(new EndpointConfiguration(config.endpoint, "eu-central-1"))
        .build,
    )

    val profilesTable: Table = db.getTable(config.playerProfileTableName)
    val clustersTable: Table = db.getTable(config.clusterTableName)

    def store(data: Seq[PlayerSessionProfile]): IO[Unit] = // TODO use batch put item to improve performance
      IO {
        data.foreach { d =>
          profilesTable.putItem(
            new Item()
              .withPrimaryKey(new PrimaryKey().addComponent("playerId", d.playerId.id))
              .withNumber("cluster", d.playerCluster.value)
              .withBinary("gzipprofile", compress(d.asJson.noSpaces)),
          )
        }
      }

    def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
      IO {
        val item = profilesTable.getItem(new PrimaryKey().addComponent("playerId", playerId.id))
        Try(unCompress(item.getBinary("gzipprofile"))) match {
          case Failure(_) =>
            None
          case Success(value) =>
            decode[PlayerSessionProfile](value).toOption
        }
      }

    def readByPlayerIds( // TODO use batch get item
      playerIds: Seq[PlayerId],
    ): IO[Seq[PlayerSessionProfile]] =
      playerIds.toList.traverse { playerId =>
        for {
          playerProfile <- readByPlayerId(playerId)
          playerCluster <- readClusterByPlayerId(playerId)
        } yield PlayerSessionProfile(
          playerId,
          playerCluster.getOrElse(Cluster.Default),
          playerProfile.map(_.firstSeqNum).getOrElse(SeqNumber.Default),
          playerProfile.map(_.lastSeqNum).getOrElse(SeqNumber.Default),
          playerProfile.map(_.gamePlay).getOrElse(PlayerGamePlay.Empty),
        )
      }

    def readClusterByPlayerId(playerId: PlayerId): IO[Option[Cluster]] =
      IO {
        val item = clustersTable.getItem(new PrimaryKey().addComponent("playerId", playerId.id))
        Try(item.get("cluster").toString) match {
          case Failure(_)     => None
          case Success(value) => decode[Cluster](value).toOption
        }

      }

    // TODO the below function is needed for http part of the service

//    def readProfilesByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]] =
//      IO(
//        PlayerProfileTable.query
//          .filter { t =>
//            t.cluster -> DynamoDBCondition.eq(cluster.value) :: Nil
//          }
//          .list[PlayerSessionProfile]
//          .toSeq,
//      )
  }
}
