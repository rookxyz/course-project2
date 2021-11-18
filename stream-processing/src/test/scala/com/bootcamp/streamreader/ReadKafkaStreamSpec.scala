package com.bootcamp.streamreader

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike



class MySpec extends munit.CatsEffectSuite with Matchers with EmbeddedKafka {

  test("Basic Kafka stream test") {
    val customConsumerConfig = Map("max.partition.fetch.bytes" -> "2000000",
                                            "log.cleaner.enable" -> "false",
                                            "log.dirs" -> "./target/embedded-kafka/exampleService")
    val customBrokerConfig = Map("replica.fetch.max.bytes" -> "2000000",
      "message.max.bytes" -> "2000000",
    "log.dirs" -> "./target/embedded-kafka/exampleService")
            val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0,
              zooKeeperPort = 0,
              customConsumerProperties = customConsumerConfig,
              customBrokerProperties = customBrokerConfig)

    withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
      // now a kafka broker is listening on actualConfig.kafkaPort
      val stream = new PlayerDataConsumer

      publishStringMessageToKafka("topic", "message")
      consumeFirstStringMessageFrom("topic") shouldBe "message"
      stream.start.unsafeRunSync()
    }
  }
}
////
//class MySpec extends AnyWordSpecLike with Matchers with BeforeAndAfter with EmbeddedKafka {
//
//  "runs with embedded kafka on arbitrary available ports" should {
//
//    "work" in {
//
//      val customConsumerConfig = Map("max.partition.fetch.bytes" -> "2000000",
//                                      "log.cleaner.enable" -> "false",
//                                      "log.dir" -> "target/embedded-kafka/exampleService")
//      val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0, customConsumerProperties = customConsumerConfig)
//      withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
//        // now a kafka broker is listening on actualConfig.kafkaPort
//        println("I am HEREEE xxxxxxxxxxxxxxxxxxx")
//        publishStringMessageToKafka("topic", "message")
//        consumeFirstStringMessageFrom("topic") shouldBe "message"
//      }
//    }
//  }
//
//  "runs with embedded kafka" should {
//
//    "work" in {
//      EmbeddedKafka.start()
//
//      // ... code goes here
//
//      EmbeddedKafka.stop()
//    }
//    after {
//      EmbeddedKafka.stop()
//    }
//  }
//}


