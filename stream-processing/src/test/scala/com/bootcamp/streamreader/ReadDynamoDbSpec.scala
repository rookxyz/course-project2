package com.bootcamp.streamreader

import awscala.Region
import cats.effect.IO
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.PlayerDataConsumer
import com.bootcamp.streamreader.domain.GameType._
import com.bootcamp.streamreader.domain._
import io.circe
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import com.amazonaws._
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.dynamodbv2.util.TableUtils
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, ItemCollection, PrimaryKey, ScanOutcome}
import com.amazonaws.services.dynamodbv2.model.{ProvisionedThroughputDescription, TableDescription, TableStatus}

import java.time.Instant
import scala.concurrent.duration._
import scala.util.Try

class ReadDynamoDbSpec
    extends munit.CatsEffectSuite
    with Matchers
    with EmbeddedKafka
    with Eventually
    with IntegrationPatience {

  test("List DynamoDB tables") {

    implicit val db: DynamoDB = new DynamoDB(
      AmazonDynamoDBClientBuilder.standard
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-central-1"))
        .build,
    )
    val t = db.getTable("profiles")
    println(t.getTableName)
//    t.putItem(
//      new Item()
//        .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p6"))
//        .withNumber("cluster", 3),
//    )
//    val result = t.getItem(new PrimaryKey().addComponent("playerId", "p6"))
    val result = t.getItem("playerId", "p6", "cluster", 3)
//    val r2 = t.getItem(new PrimaryKey().addComponent("playerId", "p6"))

//    val x: ItemCollection[ScanOutcome] = t.scan()
//    println(x.forEach(a => a.get("cluster")))
    println(result.get("cluster"))

//    println(db.listTables())
//    val table = db.table("clusters").get
//    table.put("p1", 3)
//    val table = db.table("profiles")
//    table.isEmpty
  }

//  test("List DynamoDB tables") {
//    implicit val db: DynamoDB = {
//      val client = DynamoDB("", "")(Region.Frankfurt)
//      client.setEndpoint("http://localhost:8000")
//      client
//    }
////    db.deleteTable("profiles4")
//    println(s"DynamoDb tables: ${db.listTables().toString}")
//    val profileTableMeta: TableMeta = db.createTable(
//      name = "profiles2",
//      hashPK = "playerId" -> AttributeType.String,
//    )
//    TableUtils.waitUntilActive(db, "profiles")
//    println("")
//    println(s"Created DynamoDB tables have been activated.")
//    println(s"DynamoDb tables: ${db.listTables().toString}")
//  }
//
//  test("Test DynamoDb repository is updated") {
//
//    val expected = Some(
//      PlayerSessionProfile(
//        PlayerId("p1"),
//        Cluster(1),
//        SeqNum(0L),
//        SeqNum(1L),
//        PlayerGamePlay(
//          Map(Baccarat -> GameTypeActivity(1, Money(111.11), Money(222.22))),
//        ),
//      ),
//    )
//
//    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
//    val dbConfig = DbConfig("localhost", Port(8000), "aaa", "bbbb", "profiles", "clusters")
//
//    // DB setup
////    implicit val db = DynamoDB.local()
////    Try(db.deleteTable(dbConfig.clusterTableName))
////    Try(db.deleteTable(dbConfig.playerProfileTableName))
////    println(s"Starting DB setup")
////    val profileTableMeta: TableMeta = db.createTable(
////      name = dbConfig.playerProfileTableName,
////      hashPK = "playerId" -> AttributeType.String,
////      rangePK = "cluster" -> AttributeType.Number,
////      otherAttributes = Seq(),
//////        "firstSeqNum" -> AttributeType.Number,
//////        "lastSeqNum" -> AttributeType.Number,
//////        "profile" -> AttributeType.String,
//////      ),
////      indexes = Seq(),
////    )
////    println(s"Creating Table: ${profileTableMeta.name}")
//
////    val clusterTableMeta: TableMeta = db.createTable(
////      name = dbConfig.clusterTableName,
////      hashPK = "playerId" -> AttributeType.String,
////      rangePK = "cluster" -> AttributeType.Number,
////      otherAttributes = Seq(),
////      indexes = Seq(),
////    )
////    println(s"Creating Table: ${clusterTableMeta.name}")
//
////    println(s"Waiting for DynamoDB table activation...")
////    TableUtils.waitUntilActive(db, profileTableMeta.name)
////    TableUtils.waitUntilActive(db, clusterTableMeta.name)
////    println("")
////    println(s"Created DynamoDB tables have been activated.")
//
//    // end of DB setup
//    val config = EmbeddedKafkaConfig(
//      kafkaPort = kafkaConfig.port.value,
//    )
//    val repository = PlayerRepository(dbConfig)
//    val rref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty)
//    val program =
//      rref.flatMap { ref =>
//        val state = UpdatePlayerProfile(ref, repository)
//        val service = InitPlayerProfile(state)
//        val consumer = new PlayerDataConsumer(kafkaConfig, service)
//        consumer.stream.take(1).compile.toList // read one record and exit
//      }
//    val message1 =
//      """
//        |    {
//        |    "playerId": "p1",
//        |    "gameId":"g1",
//        |    "tableId":"t1",
//        |    "gameType":"Baccarat",
//        |    "stakeEur":111.11,
//        |    "payoutEur":222.22,
//        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
//        |    "seqNum": 1
//        |    }
//        |""".stripMargin
//
//    withRunningKafkaOnFoundPort(config) { implicit config =>
//      publishToKafka("topic", "p1", message1)(
//        config,
//        new StringSerializer,
//        new StringSerializer,
//      )
//
//      program.unsafeRunTimed(10.seconds)
//      repository
//        .readByPlayerId(PlayerId("p1"))
//        .unsafeRunSync() shouldBe expected
//    }
////
////    Try(db.deleteTable(dbConfig.clusterTableName))
////    Try(db.deleteTable(dbConfig.playerProfileTableName))
//  }
//
}
