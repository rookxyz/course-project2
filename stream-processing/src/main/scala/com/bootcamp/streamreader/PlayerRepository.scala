package com.bootcamp.streamreader

import cats.effect.IO
import cats.implicits._
import com.bootcamp.streamreader.domain._

import scala.collection.concurrent.TrieMap
import jp.co.bizreach.dynamodb4s.{DynamoAttribute, DynamoHashKey, DynamoTable, IntDynamoType, DynamoRangeKey}
import awscala.dynamodbv2.{DynamoDB, DynamoDBCondition}

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
    implicit val db = DynamoDB.local() // TODO need to check if this is needed
//    println("Here")
//    val createdTableMeta: TableMeta = db.createTable(name = "profiles", hashPK = "playerId" -> AttributeType.String)
//    val createdTableMeta2: TableMeta = db.createTable(name = "clusters", hashPK = "playerId" -> AttributeType.String)
//    TableUtils.waitUntilActive(db, createdTableMeta.name)
//    TableUtils.waitUntilActive(db, createdTableMeta2.name)
//    println(s"Tables in DynamoDB: ${db.tableNames}")
    println(s"Starting to create Table objects")
    object PlayerProfileTable extends DynamoTable {
      val table = config.playerProfileTableName
      val playerId = DynamoHashKey[String]("playerId")
      val cluster = DynamoRangeKey[Int]("cluster")
      val firstSeqNum = DynamoAttribute[Int]("firstSeqNum")
      val lastSeqNum = DynamoAttribute[Int]("lastSeqNum")
      val gamePlay = DynamoAttribute[String]("profile")
//      object clusterIndex extends DynamoTable.SecondaryIndex {
//        val index = "clusterIndex"
//        val cluster = DynamoHashKey[Int]("cluster")
//      }
    }
    println(s"Starting to create Table objects2")
    object PlayerClusterTable extends DynamoTable {
      val table = config.clusterTableName
      val playerId = DynamoHashKey[String]("playerId")
      val cluster = DynamoRangeKey[Int]("cluster")
    }
//    object ClusterPlayerTable extends DynamoTable {
//      val table = config.clusterTableName
//      val cluster = DynamoHashKey[String]("cluster")
//      val playerId = DynamoAttribute[Int]("playerId")
//    }

//    val playerProfileTable: Table = db.table(config.playerProfileTableName).get
//    val playerClusterTable: Table = db.table(config.clusterTableName).get

    def store(data: Seq[PlayerSessionProfile]): IO[Unit] =
      IO {
        data.foreach { d =>
          println(s"Trying to store: $d")
          PlayerProfileTable.put(d)
        }
      }

    def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
      IO {
        println(s"Trying to read $playerId from DynamoDB")
        PlayerProfileTable.query
          .filter { t =>
            t.playerId -> DynamoDBCondition.eq(playerId) :: Nil
          }
          .list[PlayerSessionProfile]
          .headOption

      }

    def readByPlayerIds(
      playerIds: Seq[PlayerId],
    ): IO[Seq[PlayerSessionProfile]] =
      playerIds.toList.traverse { playerId =>
        println(s"Inside readByPlayerIds")
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
      IO(
        PlayerClusterTable.query
          .select { t => t.cluster :: Nil }
          .filter { t =>
            t.playerId -> DynamoDBCondition.eq(playerId) :: Nil
          }
          .map { (t, x) =>
            Cluster(x.get(t.cluster))
          }
          .headOption,
      )

    def readProfilesByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]] =
      IO(
        PlayerProfileTable.query
          .filter { t =>
            t.cluster -> DynamoDBCondition.eq(cluster) :: Nil
          }
          .list[PlayerSessionProfile]
          .toSeq,
      )
  }
}
