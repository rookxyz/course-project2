package com.bootcamp.streamreader

import cats.effect.kernel.Ref
import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._
import com.typesafe.config.ConfigFactory
import fs2.kafka._
import io.circe.parser._

import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

import scala.collection.concurrent.TrieMap

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
            val service = new PlayerProfileStateHandler(state)
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
    // put
    // get
  }

  // interface
  trait PlayerRepository {
    def store(data: Seq[PlayerSessionProfile]): IO[Unit]

    def readByUserId(userId: PlayerId): IO[Option[PlayerSessionProfile]]
  }

  trait InMemPlayerRepository extends PlayerRepository {
    val storage: TrieMap[PlayerId, PlayerSessionProfile]
  }

  // service, handler - bad naming

  object PlayerRepository {
    def apply(): PlayerRepository = {
      PlayerRepository.inMem // TODO: temporary, use dynamo db
    }

    def empty: PlayerRepository = new PlayerRepository {
      def store(data: Seq[PlayerSessionProfile]): IO[Unit] = IO.unit

      def readByUserId(userId: PlayerId): IO[Option[PlayerSessionProfile]] =
        IO.pure(None)
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

      def readByUserId(playerId: PlayerId): IO[Option[PlayerSessionProfile]] =
        IO {
          storage.get(playerId)
        }
    }
  }

  //// OOOLD version
  trait PlayerProfileStateHandler {
    val state: PlayerState
    def processNewPlayerProfiles(profiles: Seq[PlayerSessionProfile]): IO[Unit]
    def createPlayerSessionProfile(
        playerRounds: Seq[(PlayerId, PlayerGameRound)]
    ): Seq[PlayerSessionProfile]
  }

  // TODO need to discuss the role of the PlayerService, not sure where to implement each of the points
  object PlayerProfileStateHandler {
    def apply(other: PlayerState): PlayerProfileStateHandler =
      new PlayerProfileStateHandler {
        override val state: PlayerState = other

        override def processNewPlayerProfiles(
            profiles: Seq[PlayerSessionProfile]
        ): IO[Unit] = ???

        def createPlayerSessionProfile(
            playerRounds: Seq[(PlayerId, PlayerGameRound)]
        ): Seq[PlayerSessionProfile] = {
          playerRounds
            .groupBy(_._1)
            .map { case (playerId, playerRecords) =>
              val gamePlay: Map[GameType, GameTypeActivity] = playerRecords
                .map(_._2)
                .groupBy(_.gameType)
                .foldLeft(Map.empty[GameType, GameTypeActivity])((a, c) => {
                  a + (c._1 -> GameTypeActivity(c._2))
                })
              // TODO fix initially get cluster from db
              PlayerSessionProfile(
                playerId,
                Cluster(0),
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
        .evalMap { chunk =>
          // 1) group/handle data into Seq[PlayerSessionProfile] and pass to playerService(players)
          // 2) test it works
          // 3) in mem state (Ref) - 11) init from db if empty.
          //   3.1) last game ids
          // 4) write to db
          // 5) sequenceNumber
          // check sequenceNumber is correct
          // check last sequenceNumber in state
          // restore state from db if there is gap
          val playerRounds: Seq[(PlayerId, PlayerGameRound)] = {
            chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) {
              case (a, b) =>
                val playerId = PlayerId(b.record.key)
                val playerGameRound =
                  decode[PlayerGameRound](b.record.value) match {
                    case Left(_) =>
                      throw new RuntimeException(
                        "Could not create PlayerGameRound from Json"
                      ) // TODO Log unsuccessful decode
                    case Right(playerGameRound) => playerGameRound
                  }
                a :+ (playerId, playerGameRound)
            }
          }
          playerProfileHandler
            .createPlayerSessionProfile(playerRounds)
            .map(playerProfileHandler.processNewPlayerProfiles)
            .map(_ => chunk.map(_.offset))
        }
        .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
  }
}
