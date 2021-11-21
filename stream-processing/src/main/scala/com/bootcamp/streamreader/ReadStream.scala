package com.bootcamp.streamreader

import cats.effect.{ExitCode, IO, IOApp}
import com.bootcamp.streamreader.domain.{KafkaConfig, Port}
import fs2.kafka._
import pureconfig.ConfigReader.Result

import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    ConfigSource.default.load[KafkaConfig] match {
      case Left(value) => IO.unit.as(ExitCode.Error)
      case Right(config) => {
        val stream = new PlayerDataConsumer(config)
        stream.start.as(ExitCode.Success)
      }
    }


  }
}

class PlayerDataConsumer(kafkaConfig: KafkaConfig) {
  def this() {this(KafkaConfig("127.0.0.1", Port(0), "topic"))}

  def processRecord(record: ConsumerRecord[String, String]): IO[(String, String)] =
    IO.pure(record.key -> record.value)

  def start: IO[Unit] = stream.compile.drain

  val port: Port = kafkaConfig.port

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"${kafkaConfig.host}:${port.value}")
      .withGroupId("group")


  val stream: fs2.Stream[IO, Unit] =
    KafkaConsumer.stream(consumerSettings)
      .subscribeTo(kafkaConfig.topic)
      .records
      .mapAsync(25) { committable =>
        processRecord(committable.record)
          .map { case (key, value) => println(s"$key -> $value")
            //              val record = ProducerRecord("topic", key, value)
            //              ProducerRecords.one(record, committable.offset)
          }
      }
}