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

  test("Runtime exception on invalid message test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val consumer = new PlayerDataConsumer(kafkaConfig)
    val config = EmbeddedKafkaConfig(
      kafkaPort = consumer.port.value,
      customConsumerProperties = consumer.consumerSettings.properties
    )
    val start = consumer.stream.take(1).compile.toList // read one record and exit

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", "message1")(
        config,
        new StringSerializer,
        new StringSerializer
      )
      an [RuntimeException] should be thrownBy start.unsafeRunTimed(10.seconds).get
    }
  }

  test("Accepts correct Json test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val consumer = new PlayerDataConsumer(kafkaConfig)
    val config = EmbeddedKafkaConfig(
      kafkaPort = consumer.port.value,
      customConsumerProperties = consumer.consumerSettings.properties
    )
    val start = consumer.stream.take(1).compile.toList // read one record and exit

    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"gt1",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer
      )

      start.unsafeRunTimed(10.seconds).get.length shouldBe 1
    }
  }

  test("Aggregates players game play test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val consumer = new PlayerDataConsumer(kafkaConfig)
    val config = EmbeddedKafkaConfig(
      kafkaPort = consumer.port.value,
      customConsumerProperties = consumer.consumerSettings.properties
    )
    val start = consumer.stream.take(1).compile.toList // read one record and exit

    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"gt1",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val message2 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g2",
        |    "tableId":"t1",
        |    "gameType":"gt1",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val message3 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g3",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "p1", message2)(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "p1", message3)(
        config,
        new StringSerializer,
        new StringSerializer
      )

      start.unsafeRunTimed(10.seconds).get.length shouldBe 1
    }
  }
  test("Aggregates multiple player game play test") {
    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic")
    val consumer = new PlayerDataConsumer(kafkaConfig)
    val config = EmbeddedKafkaConfig(
      kafkaPort = consumer.port.value,
      customConsumerProperties = consumer.consumerSettings.properties
    )
    val start = consumer.stream.take(1).compile.toList // read one record and exit

    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"gt1",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val message2 =
      """
        |    {
        |    "playerId": "p2",
        |    "gameId":"g2",
        |    "tableId":"t1",
        |    "gameType":"gt1",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    val message3 =
      """
        |    {
        |    "playerId": "p3",
        |    "gameId":"g3",
        |    "tableId":"t1",
        |    "gameType":"Roulette",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z"
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "p2", message2)(
        config,
        new StringSerializer,
        new StringSerializer
      )
      publishToKafka("topic", "p3", message3)(
        config,
        new StringSerializer,
        new StringSerializer
      )

      start.unsafeRunTimed(10.seconds).get.length shouldBe 1
    }
  }
}