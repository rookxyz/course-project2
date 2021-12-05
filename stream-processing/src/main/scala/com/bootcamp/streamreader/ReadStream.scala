package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._
import com.typesafe.config.ConfigFactory
import fs2.Chunk
import fs2.kafka._
import io.circe.parser._

import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

import scala.collection.concurrent.TrieMap
import scala.collection.immutable

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    ConfigSource
      .fromConfig(ConfigFactory.load("application"))
      .load[AppConfig] match {
      case Left(_) => IO.unit.as(ExitCode.Error)
      case Right(config) => {
        val repository = PlayerRepository()
        Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty) flatMap {
          ref =>
            val state = new PlayerState(ref, repository)
            val service = PlayerProfileStateHandler(state)
            val stream = new PlayerDataConsumer(config.kafka, service)
            stream.start.as(ExitCode.Success)
        }
      }
    }
  }

  // interface - > 1 implementation
  // class - 1 implementation
  // state abstraction
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
          playerRepository.readBySeqOfPlayerId(missing)
        ss <- ref.updateAndGet(state => {
          state |+| (repositoryDataForMissing ++ playerProfiles) // Mission on the left side so that cluster data is accurate
            .map(item => (item.playerId -> item))
            .toMap
        })
        changedProfiles = ss
          .filterKeys(k => p.contains(k))
          .values
          .toSeq
        _ <- playerRepository.store(changedProfiles)
      } yield ()
    }

    // get
  }

  // interface
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
          println(playerProfiles)
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
                Cluster(
                  0
                ), // TODO perhaps cluster should be an Option, so that no need to fill it here
                PlayerGamePlay(gamePlay)
              )
            }
            .toSeq
        }
      }

  }

  class PlayerDataConsumer(
      kafkaConfig: KafkaConfig = KafkaConfig("127.0.0.1", Port(0), "topic"),
      playerProfileHandler: PlayerProfileStateHandler
  ) {

    def start: IO[Unit] = stream.compile.drain

    val port: Port = kafkaConfig.port

    val consumerSettings: ConsumerSettings[IO, String, String] =
      ConsumerSettings(
        keyDeserializer = Deserializer[IO, String],
        valueDeserializer = Deserializer[IO, String]
      ) // TODO check if can decode to PlayerGameRound here
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
        .withGroupId("group1") // TODO get from config
        .withClientId("client1") // TODO get from config

    val stream =
      KafkaConsumer
        .stream(consumerSettings)
        .subscribeTo(kafkaConfig.topic)
        .records
        .groupWithin(
          25,
          2.seconds
        ) // TODO needs to be configurable default 25, 15
//        .evalTap(i => IO { println(i) })
        .evalMap {
          chunk: Chunk[CommittableConsumerRecord[IO, String, String]] =>
            // 1) group/handle data into Seq[PlayerSessionProfile] and pass to playerService(players)
            // 2) test it works
            // 3) in mem state (Ref) - 11) init from db if empty.
            //   3.1) last game ids
            // 4) write to db
            // TODO 5) sequenceNumber
            // TODO check sequenceNumber is correct
            // TODO check last sequenceNumber in state
            // TODO restore state from db if there is gap
            val playerRounds: Seq[(PlayerId, PlayerGameRound)] = {
              chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) {
                case (a, b) =>
                  val playerId = PlayerId(b.record.key)
                  val playerGameRound = {
                    decode[PlayerGameRound](b.record.value) match {
                      case Left(e) =>
                        throw new RuntimeException(
                          s"Error: Could not create PlayerGameRound from Json. $e"
                        ) // TODO Log unsuccessful decode
                      case Right(playerGameRound) => playerGameRound
                    }
                  }
                  a :+ (playerId, playerGameRound)
              }
            }

            // TODO why none of the code below seems to be running?
            val y = createPlayerSessionProfile2(playerRounds)
            val x = for {
              _ <- playerProfileHandler
                .processNewPlayerProfiles(y)
            } yield ()
            x.map(_ => chunk.map(_.offset))
        }
        .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)

    // TODO remove once understood why IO code is not running
    def createPlayerSessionProfile2(
        playerRounds: Seq[(PlayerId, PlayerGameRound)]
    ): Seq[PlayerSessionProfile] = {
      println(playerRounds)
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
            Cluster(
              0
            ), // TODO perhaps cluster should be an Option, so that no need to fill it here
            PlayerGamePlay(gamePlay)
          )
        }
        .toSeq
    }
  }
}
