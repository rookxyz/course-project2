package com.bootcamp.streamreader

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike



//class MySpec extends munit.CatsEffectSuite with Matchers with EmbeddedKafka {
//
//  test("Basic Kafka stream test") {
//    val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)
//
//    withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
//      // now a kafka broker is listening on actualConfig.kafkaPort
////      val stream = new PlayerDataConsumer
//
//      publishStringMessageToKafka("topic", "message")
//      consumeFirstStringMessageFrom("topic") shouldBe "message"
////      stream.start.unsafeRunSync()
//    }
//  }
//}

class MySpec extends AnyWordSpecLike with Matchers with EmbeddedKafka {

  "runs with embedded kafka on arbitrary available ports" should {

    "work" in {
      val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

      withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
        // now a kafka broker is listening on actualConfig.kafkaPort
        println("I am HEREEE xxxxxxxxxxxxxxxxxxx")
        publishStringMessageToKafka("topic", "message")
        consumeFirstStringMessageFrom("topic") shouldBe "message2"
      }
    }
  }
}


