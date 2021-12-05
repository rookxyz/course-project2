package com.bootcamp.streamreader

import cats.Monoid
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.domain
import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain._
import com.typesafe.config.ConfigFactory
import fs2.kafka._
import io.circe.parser._

import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

import scala.collection.immutable

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    ConfigSource.fromConfig(ConfigFactory.load("application")).load[AppConfig] match {
      case Left(_) => IO.unit.as(ExitCode.Error)
      case Right(config) => {
        val stream = new PlayerDataConsumer(config.kafka)
        stream.start.as(ExitCode.Success)
      }
    }
  }
}
trait PlayerService {
  def apply(data: Seq[PlayerSessionProfile]): IO[Unit]
}

// TODO need to discuss the role of the PlayerService, not sure where to implement each of the points
object PlayerService {
  def empty: PlayerService = (data: Seq[PlayerSessionProfile]) => {
    println(data)
    IO.unit
  }
  def test(expected: Seq[PlayerSessionProfile]): PlayerService = (data: Seq[PlayerSessionProfile]) => {
    if (expected != data) IO.raiseError(new RuntimeException("Player session profiles do not match the expected!"))
    else {println("DATA MATCH!")
      IO.unit}
  }
  val state = Ref.of[IO, Seq[PlayerSessionProfile]](Seq.empty) // TODO should state be in the PlayerService?

  def createPlayerSessionProfile(playerRounds: Seq[(PlayerId, PlayerGameRound)]): Seq[PlayerSessionProfile] = {
    playerRounds.groupBy(_._1).map { case (playerId, playerRecords) =>
      val gamePlay: Map[GameType, GameTypeActivity] = playerRecords
        .map(_._2)
        .groupBy(_.gameType)
        .foldLeft(Map.empty[GameType, GameTypeActivity])((a, c) => {
          def getGameTypeActivity(activity: Seq[PlayerGameRound]): GameTypeActivity = {
            val (rounds, stake, payout) = activity.foldLeft(Tuple3[Long, BigDecimal, BigDecimal](0,0,0))((a, c) => (a._1 + 1, a._2 + c.stakeEur.amount, a._3 + c.payoutEur.amount))
            GameTypeActivity(rounds, Money(stake), Money(payout))
          }
          a + (c._1 -> getGameTypeActivity(c._2))
        })
      // TODO fix cluster from db
      PlayerSessionProfile(playerId, Cluster(0), PlayerGamePlay(gamePlay))
    }.toSeq
  }
  implicit val playerGamePlayAdditionMonoid: Monoid[PlayerGamePlay] = new Monoid[PlayerGamePlay] {
    override def empty: PlayerGamePlay = PlayerGamePlay(Map.empty[GameType, GameTypeActivity])

    override def combine(x: PlayerGamePlay, y: PlayerGamePlay): PlayerGamePlay = ???
  }
}

class PlayerDataConsumer(kafkaConfig: KafkaConfig = KafkaConfig("127.0.0.1", Port(0), "topic")) {

  def start: IO[Unit] = stream.compile.drain

  val port: Port = kafkaConfig.port

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(keyDeserializer = Deserializer[IO, String],
      valueDeserializer = Deserializer[IO, String]) // TODO check if can decode to PlayerGameRound here
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
      .withGroupId("group1") // TODO get from config
      .withClientId("client1") // TODO get from config

  val stream =
    KafkaConsumer.stream(consumerSettings)
      .subscribeTo(kafkaConfig.topic)
      .records
      .groupWithin(25, 2.seconds)  // TODO needs to be configurable default 25, 15
      .map { chunk =>

        // 1) group/handle data into Seq[PlayerSessionProfile] and pass to playerService(players)
        // 2) test it works
        // 3) in mem state (Ref) - 11) init from db if empty.
        //   3.1) last game ids
        // 4) write to db
        // 5) sequenceNumber
        // check sequenceNumber is correct
        // check last sequenceNumber in state
        // restore state from db if there is gap
        val playerRounds: Seq[(PlayerId, PlayerGameRound)] =
          chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) {
            case (a, b) =>
                val playerId = PlayerId(b.record.key)
                val playerGameRound = decode[PlayerGameRound](b.record.value) match {
                  case Left(_) => throw new RuntimeException("Could not create PlayerGameRound from Json") // TODO what should be done here ?
                  case Right(playerGameRound) => playerGameRound
                }
                a :+ (playerId, playerGameRound)
            }
        PlayerService.empty(PlayerService.createPlayerSessionProfile(playerRounds))
        // TODO check this - how should the PlayerService be used and instantiated in Main?
        // Now this is an IO, not sure how to use it further on.

//        // process(players) - insert into db
        chunk.map(_.offset)
      }
      // commit
      // .through(commitBatchWithin(3, 15.seconds))
      .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
}