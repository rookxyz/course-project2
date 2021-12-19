package com.bootcamp.playerrepository

import cats.effect.IO
import cats.implicits._
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey, QueryOutcome, Table, TableWriteItems}
import com.bootcamp.config.domain.DbConfig
import com.bootcamp.domain.{Cluster, PlayerGamePlay, PlayerId, PlayerSessionProfile, SeqNumber}
import fs2.Chunk
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.slf4j.Slf4jLogger

import collection.JavaConverters._
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

  def readPlayersByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]]
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
      IO.pure { Some(Cluster(1)) }

    def readPlayersByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]] = IO.pure {
      Seq.empty[PlayerSessionProfile]
    }

  }

  def dynamoDb(config: DbConfig): PlayerRepository = new PlayerRepository {
    implicit val db: DynamoDB = new DynamoDB(
      AmazonDynamoDBClientBuilder.standard
        .withEndpointConfiguration(new EndpointConfiguration(config.endpoint, "eu-central-1"))
        .build,
    )

    val profilesTable: Table = db.getTable(config.playerProfileTableName)
    val clustersTable: Table = db.getTable(config.clusterTableName)

    import com.bootcamp.playerrepository.utilities.CompressString._

    def store(data: Seq[PlayerSessionProfile]): IO[Unit] =
//      data.toList.parTraverse_ { d => // TODO improve further using BatchWriteItem as write overwrites the old values
//        IO(
//          profilesTable.putItem(
//            new Item()
//              .withPrimaryKey(new PrimaryKey().addComponent("playerId", d.playerId.id))
//              .withNumber("cluster", d.playerCluster.value)
//              .withBinary("gzipprofile", compress(d.asJson.noSpaces)),
//          ),
//        )
//      }
      {
        def createDbInsertItem(profile: PlayerSessionProfile): Item = new Item()
          .withPrimaryKey(new PrimaryKey().addComponent("playerId", profile.playerId.id))
          .withNumber("cluster", profile.playerCluster.value)
          .withBinary("gzipprofile", compress(profile.asJson.noSpaces))

        import fs2.Stream
//        import scala.collection.JavaConverters._

        val stream: Stream[IO, Unit] = Stream
          .evalSeq(IO(data))
          .chunkN(25, true)
          .evalMap { chunk =>
            IO {
              val writeItems: Seq[Item] =
                chunk.foldLeft(Seq.empty[Item])((acc, c) => acc ++ Seq(createDbInsertItem(c)))
              val tableWriteItems = new TableWriteItems(profilesTable.getTableName)
                .withItemsToPut(writeItems.asJavaCollection)
              val batchWriteOutcome = db.batchWriteItem(tableWriteItems)
              // how to Info log so that this does not affect the result of the function
//              Slf4jLogger
//                .create[IO]
//                .flatMap(_.info(s"DynamoDB write result: ${batchWriteOutcome.getBatchWriteItemResult.toString}"))
              // TODO retry for failed items
            }
          }
        stream.compile.drain.onError(error => Slf4jLogger.create[IO].flatMap(_.error(error)("DynamoDb write failed")))
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

    def readPlayersByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]] =
      IO {
        println(s"Tables in DB ${db.listTables().toString}")
        val globalIndex = profilesTable.getIndex("ClusterIndex")
        val indexQuery = globalIndex.query("cluster", cluster.value)
        val queryResult = indexQuery.iterator().asScala
        queryResult.toSeq.flatMap(i => decode[PlayerSessionProfile](unCompress(i.getBinary("gzipprofile"))).toOption)
      }
  }
}
