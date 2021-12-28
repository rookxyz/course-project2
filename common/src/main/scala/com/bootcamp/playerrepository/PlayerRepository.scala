package com.bootcamp.playerrepository

import cats.effect.{IO, Resource}
import cats.implicits._
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{
  BatchWriteItemOutcome,
  DynamoDB,
  Item,
  PrimaryKey,
  Table,
  TableWriteItems,
}
import com.bootcamp.config.DbConfig
import com.bootcamp.domain._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import CompressString._

import java.time.{Instant, ZoneId}

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
  def apply(): IO[PlayerRepository] =
    PlayerRepository.inMem

  def apply(config: DbConfig): IO[PlayerRepository] =
    PlayerRepository.dynamoDb(config)

  // Not used, but saved for semi-integration tests
  def inMem: IO[InMemPlayerRepository] = Slf4jLogger.create[IO].map { log =>
    new InMemPlayerRepository {
      val storage: TrieMap[PlayerId, PlayerSessionProfile] =
        TrieMap.empty[PlayerId, PlayerSessionProfile]

      def store(data: Seq[PlayerSessionProfile]): IO[Unit] =
        data.toList.parTraverse_ { d =>
          IO(storage.put(d.playerId, d)) *>
            log.info(s"Stored profile: $d")
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
        IO.pure {
          Some(Cluster(1))
        }

      def readPlayersByCluster(cluster: Cluster): IO[Seq[PlayerSessionProfile]] = IO.pure {
        Seq.empty[PlayerSessionProfile]
      }

    }
  }

  def dynamoDb(config: DbConfig): IO[PlayerRepository] = Slf4jLogger
    .create[IO]
    .map { log =>
      new PlayerRepository {
        implicit val db: DynamoDB = new DynamoDB(
          AmazonDynamoDBClientBuilder.standard
            .withEndpointConfiguration(new EndpointConfiguration(config.endpoint, "eu-central-1"))
            .build,
        )

        val profilesTable: Table = db.getTable(config.playerProfileTableName)
        val clustersTable: Table = db.getTable(config.clusterTableName)

        def store(data: Seq[PlayerSessionProfile]): IO[Unit] = {
          def getTTL =
            Instant.now().atZone(ZoneId.of("UTC")).plusSeconds(config.timeToLiveSeconds.toSeconds).toEpochSecond
          def createDbInsertItem(profile: PlayerSessionProfile): Item = new Item()
            .withPrimaryKey(new PrimaryKey().addComponent("playerId", profile.playerId.id))
            .withNumber("cluster", profile.playerCluster.value)
            .withBinary("gzipprofile", compress(profile.asJson.noSpaces))
            .withNumber("expireAt", getTTL)

          def storeWithRetry(outcome: IO[BatchWriteItemOutcome], maxRetries: Int = 5): IO[BatchWriteItemOutcome] =
            outcome
              .handleErrorWith { err =>
                IO.sleep(config.retryDelayMillis.millis) *> {
                  if (maxRetries <= 0) {
                    IO.raiseError[BatchWriteItemOutcome](err)
                  } else {
                    outcome.flatMap(o =>
                      log.warn("Store player data returned error. Retrying to store player data") *> storeWithRetry(
                        IO(db.batchWriteItemUnprocessed(o.getUnprocessedItems)),
                        maxRetries - 1,
                      ),
                    )
                  }
                }
              }
              .flatMap { u =>
                if (!u.getUnprocessedItems.isEmpty && maxRetries <= 0) {
                  log.error(s"Failed to write items to database after ${config.maxRetries} attempts").as(u)
                } else if (!u.getUnprocessedItems.isEmpty) {
                  storeWithRetry(IO(db.batchWriteItemUnprocessed(u.getUnprocessedItems)), maxRetries - 1)
                } else IO.pure(u)
              }
          data
            .grouped(25)
            .toList
            .parTraverse_ { chunk =>
              val writeItems: Seq[Item] =
                chunk.foldLeft(Seq.empty[Item])((acc, c) => acc ++ Seq(createDbInsertItem(c)))
              val tableWriteItems = new TableWriteItems(profilesTable.getTableName)
                .withItemsToPut(writeItems.asJavaCollection)
              storeWithRetry(IO(db.batchWriteItem(tableWriteItems)), config.maxRetries)
            }
        }

        def readByPlayerId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
          (for {
            response <- Resource.eval(
              IO(
                profilesTable.getItem(new PrimaryKey().addComponent("playerId", playerId.id)),
              ),
            )
            profileString <- unCompressR(response.getBinary("gzipprofile"))
            profile = decode[PlayerSessionProfile](profileString).toOption
          } yield profile).use(IO.pure).handleErrorWith { e =>
            log.info(s"Could not get player $playerId from database") *> IO.pure(none[PlayerSessionProfile])
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
            val globalIndex = profilesTable.getIndex("ClusterIndex")
            val indexQuery = globalIndex.query("cluster", cluster.value)
            val queryResult = indexQuery.iterator().asScala
            queryResult.toSeq
              .flatMap(i => decode[PlayerSessionProfile](unCompress(i.getBinary("gzipprofile"))).toOption)
          }
      }
    }
}
