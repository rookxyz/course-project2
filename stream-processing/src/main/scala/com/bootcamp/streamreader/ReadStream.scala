package com.bootcamp.streamreader

import cats.effect.{ExitCode, IO, IOApp}
import fs2.kafka._
import scala.concurrent.duration._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val stream = new PlayerDataConsumer
    stream.start.as(ExitCode.Success)

  }
}

class PlayerDataConsumer {
  def processRecord(record: ConsumerRecord[String, String]): IO[(String, String)] =
    IO.pure(record.key -> record.value)

  def start = stream.compile.drain

  val consumerSettings =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:9001")
      .withGroupId("group")

  val stream: fs2.Stream[IO, Unit] =
    KafkaConsumer.stream(consumerSettings)
      .subscribeTo("topic")
      .records
      .mapAsync(25) { committable =>
        processRecord(committable.record)
          .map { case (key, value) => println(s"$key -> $value")
            //              val record = ProducerRecord("topic", key, value)
            //              ProducerRecords.one(record, committable.offset)
          }
      }
}