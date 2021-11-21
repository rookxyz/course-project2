package com.bootcamp.streamreader

import com.bootcamp.streamreader.domain.{KafkaConfig, Port}
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class MySpec extends munit.CatsEffectSuite with Matchers with EmbeddedKafka {

  test("Basic Kafka stream test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val consumer = new PlayerDataConsumer(kafkaConfig)
    val config = EmbeddedKafkaConfig(
      kafkaPort = consumer.port.value,
      customConsumerProperties = consumer.consumerSettings.properties
    )
    val start = consumer.stream.take(1).compile.toList // read one record and exit

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "key", "message1")(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "key", "message2")(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "key", "message3")(
        config,
        new StringSerializer,
        new StringSerializer
      )

      start.unsafeRunTimed(10.seconds).get.length shouldBe 1
    }
  }
}