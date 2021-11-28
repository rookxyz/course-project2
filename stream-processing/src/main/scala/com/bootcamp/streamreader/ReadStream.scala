package com.bootcamp.streamreader

import com.bootcamp.streamreader.domain.PlayerGameRoundDomain._

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

object PlayerService {
  def empty: PlayerService = (data: Seq[PlayerSessionProfile]) => IO.unit
  def test(expected: Seq[PlayerSessionProfile]): PlayerService = (data: Seq[PlayerSessionProfile]) => {
    if (expected != data) IO.raiseError(new RuntimeException)
    else IO.unit
  }
}

class PlayerDataConsumer(kafkaConfig: KafkaConfig = KafkaConfig("127.0.0.1", Port(0), "topic")) {


  def processRecord(record: ConsumerRecord[String, String]): IO[(String, String)] =
    IO.pure(record.key -> record.value)

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
//      .mapAsync(25) { committable =>
//        processRecord(committable.record)
//          .map { case (key, value) => println(s"$key -> $value")
//            //              val record = ProducerRecord("topic", key, value)
//            //              ProducerRecords.one(record, committable.offset)
//          }
//      }
      .groupWithin(25, 2.seconds)  // TODO needs to be configurable default 25, 15
      .map { chunk =>

        // 1) group/handle data into Seq[PlayerSessionProfile] and pass to playerService(players)
        // 2) test it works

        // 3) in mem state (Ref) - 11) init from db if empty.
        //   3.1) last game ids
        // 4) write to db
        val players: Seq[(PlayerId, PlayerGameRound)] =
          chunk.foldLeft(Seq.empty[(PlayerId, PlayerGameRound)]) {
            case (a, b) =>
              {
                val playerId = PlayerId(b.record.key)
                val playerGameRound = decode[PlayerGameRound](b.record.value) match {
                  case Left(_) => throw new RuntimeException("Could not create PlayerGameRound from Json") // TODO what should be done here ?
                  case Right(playerGameRound) => playerGameRound
                }
                a :+ (playerId, playerGameRound)
              }
            }

        // 5) sequenceNumber
        val output = players.groupBy(_._1).map { case (playerId, playerRecords) =>
          // check sequenceNumber is correct
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
          // check last sequenceNumber in state
          // restore state from db if there is gap
        }

        println(players)
        println(output)
//        playerService(players)
//        // process(players) - insert into db
        chunk.map(_.offset)
      }
      // commit
      // .through(commitBatchWithin(3, 15.seconds))
      .evalMap(x => CommittableOffsetBatch.fromFoldable(x).commit)
}