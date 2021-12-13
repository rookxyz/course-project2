package com.bootcamp.streamreader

import cats.effect.IO
import cats.effect.kernel.Ref
import com.bootcamp.streamreader.domain.GameType._
import com.bootcamp.streamreader.domain._
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
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, ItemCollection, PrimaryKey, ScanOutcome, Table}
import com.amazonaws.services.dynamodbv2.model.{
  AttributeDefinition,
  CreateTableRequest,
  GlobalSecondaryIndex,
  KeySchemaElement,
  Projection,
  ProjectionType,
  ProvisionedThroughput,
  ProvisionedThroughputDescription,
  ScalarAttributeType,
  TableDescription,
  TableStatus,
}
import org.scalatest.BeforeAndAfter

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

class ReadDynamoDbSpec
    extends munit.CatsEffectSuite
    with Matchers
    with EmbeddedKafka
    with Eventually
    with IntegrationPatience {

  def createDbTables(config: DbConfig)(implicit db: DynamoDB): Map[String, Table] = {
    val attributeCluster = new AttributeDefinition("cluster", ScalarAttributeType.N)
    val attributePlayerId = new AttributeDefinition("playerId", ScalarAttributeType.S)
    //    val attributeProfile = new AttributeDefinition("gzipprofile", ScalarAttributeType.B)

    val createProfilesTableReq = new CreateTableRequest()
      .withTableName(config.playerProfileTableName)
      .withKeySchema(new KeySchemaElement().withKeyType("HASH").withAttributeName("playerId"))
      .withAttributeDefinitions(attributePlayerId, attributeCluster)
      .withGlobalSecondaryIndexes(
        new GlobalSecondaryIndex()
          .withIndexName("ClusterIndex")
          .withKeySchema(
            new KeySchemaElement()
              .withKeyType("HASH")
              .withAttributeName("cluster"),
          )
          .withProjection(new Projection().withProjectionType("ALL"))
          .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L)),
      )
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L))

    val createClustersTableReq = new CreateTableRequest()
      .withTableName(config.clusterTableName)
      .withKeySchema(new KeySchemaElement().withKeyType("HASH").withAttributeName("playerId"))
      .withAttributeDefinitions(attributePlayerId)
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L))

    val t2 = db.createTable(createProfilesTableReq)
    //    println("t1 created")
    val t3 = db.createTable(createClustersTableReq)
    //    println("t2 created")
    t2.waitForActive()
    t3.waitForActive()
    //    t2.delete()
    //    t3.delete()
    Map("profiles" -> t2, "clusters" -> t3)

  }

  def deleteDbTables(implicit db: DynamoDB): Unit = db.listTables().forEach { t =>
    t.delete()
    t.waitForDelete()
  }

  test("List DynamoDB tables") {

    implicit val db: DynamoDB = new DynamoDB(
      AmazonDynamoDBClientBuilder.standard
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-central-1"))
        .build,
    )
    val attributeCluster = new AttributeDefinition("cluster", ScalarAttributeType.N)
    val attributePlayerId = new AttributeDefinition("playerId", ScalarAttributeType.S)
//    val attributeProfile = new AttributeDefinition("gzipprofile", ScalarAttributeType.B)

    val createProfilesTableReq = new CreateTableRequest()
      .withTableName("profiles2")
      .withKeySchema(new KeySchemaElement().withKeyType("HASH").withAttributeName("playerId"))
      .withAttributeDefinitions(attributePlayerId, attributeCluster)
      .withGlobalSecondaryIndexes(
        new GlobalSecondaryIndex()
          .withIndexName("ClusterIndex")
          .withKeySchema(
            new KeySchemaElement()
              .withKeyType("HASH")
              .withAttributeName("cluster"),
          )
          .withProjection(new Projection().withProjectionType("ALL"))
          .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L)),
      )
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L))

    val createClustersTableReq = new CreateTableRequest()
      .withTableName("clusters2")
      .withKeySchema(new KeySchemaElement().withKeyType("HASH").withAttributeName("playerId"))
      .withAttributeDefinitions(attributePlayerId)
      .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L))

//    val t2 = db.createTable(createProfilesTableReq)
//    println("t1 created")
//    val t3 = db.createTable(createClustersTableReq)
//    println("t2 created")
//    t2.waitForActive()
//    t3.waitForActive()
//    t2.delete()
//    t3.delete()
    val t = db.getTable("profiles2")
    val profile = PlayerSessionProfile(
      PlayerId("p1"),
      Cluster(1),
      SeqNum(0L),
      SeqNum(1L),
      PlayerGamePlay(
        Map(Baccarat -> GameTypeActivity(1, Money(333.11), Money(222.22))),
      ),
    )

    println(t.getTableName)
    t.putItem(
      new Item()
        .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p6"))
        .withNumber("cluster", 3)
        .withString("profile", profile.asJson.toString()),
    )
    val p = PlayerId("p1")
    val result = t.getItem(new PrimaryKey().addComponent("playerId", p.id))
//    val result = t.getItem("playerId", "p6", "cluster", 3)
//    val r2 = t.getItem(new PrimaryKey().addComponent("playerId", "p6"))

    val globalIndex = t.getIndex("ClusterIndex")

    val indexQuery = globalIndex.query("cluster", 0)
    indexQuery.forEach(i => println(decode[PlayerSessionProfile](unCompress(i.getBinary("gzipprofile")))))

//    val x: ItemCollection[ScanOutcome] = t.scan()
//    println(x.forEach(a => a.get("cluster")))
    def compress(str: String): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val gzipOut = new GZIPOutputStream(baos)
      gzipOut.write(str.getBytes("UTF-8"))
      gzipOut.close()
      baos.toByteArray
    }
    println(compress(profile.asJson.toString()))

    def unCompress(compressed: Array[Byte]): String = {
      import java.io.ByteArrayInputStream
      import java.util.zip.GZIPInputStream
      val bis = new ByteArrayInputStream(compressed)
      val gis = new GZIPInputStream(bis)
      val res = Source.fromInputStream(gis, "UTF-8").getLines.take(1).toList.head
      gis.close
      res
    }

    println(unCompress(result.getBinary("gzipprofile")).toString)

//    println(db.listTables())
//    val table = db.table("clusters").get
//    table.put("p1", 3)
//    val table = db.table("profiles")
//    table.isEmpty
  }
  test("Test DynamoDb repository is updated") {
    Ref
    val expected = Some(
      PlayerSessionProfile(
        PlayerId("p1"),
        Cluster(1),
        SeqNum(0L),
        SeqNum(1L),
        PlayerGamePlay(
          Map(Baccarat -> GameTypeActivity(1, Money(111.11), Money(222.22))),
        ),
      ),
    )

    val kafkaConfig = KafkaConfig("localhost", Port(16001), "topic", "group1", "client1", 25, 2.seconds)
    val dbConfig = DbConfig("http://localhost:8000", "aaa", "bbbb", "profiles2", "clusters2")
    ////// DB setup
    implicit val db: DynamoDB = new DynamoDB(
      AmazonDynamoDBClientBuilder.standard
        .withEndpointConfiguration(new EndpointConfiguration(dbConfig.endpoint, "eu-central-1"))
        .build,
    )
    deleteDbTables
    val tablesMap = createDbTables(dbConfig)
    val clustersTable = tablesMap.get("clusters")
    clustersTable.get
      .putItem(
        new Item()
          .withPrimaryKey(new PrimaryKey().addComponent("playerId", "p1"))
          .withNumber("cluster", 1),
      )
      .ensuring(true)
    ////// end of DB setup
    val config = EmbeddedKafkaConfig(
      kafkaPort = kafkaConfig.port.value,
    )
    val repository = PlayerRepository(dbConfig)
    val rref = Ref.of[IO, Map[PlayerId, PlayerSessionProfile]](Map.empty)
    val program =
      rref.flatMap { ref =>
        val state = UpdatePlayerProfile(ref, repository)
        val service = InitPlayerProfile(state)
        val consumer = new PlayerDataConsumer(kafkaConfig, service)
        consumer.stream.take(1).compile.toList // read one record and exit
      }
    val message1 =
      """
        |    {
        |    "playerId": "p1",
        |    "gameId":"g1",
        |    "tableId":"t1",
        |    "gameType":"Baccarat",
        |    "stakeEur":111.11,
        |    "payoutEur":222.22,
        |    "gameEndedTime":"2021-11-28T14:14:34.257Z",
        |    "seqNum": 1
        |    }
        |""".stripMargin

    withRunningKafkaOnFoundPort(config) { implicit config =>
      publishToKafka("topic", "p1", message1)(
        config,
        new StringSerializer,
        new StringSerializer,
      )

      program.unsafeRunTimed(10.seconds)
      repository
        .readByPlayerId(PlayerId("p1"))
        .unsafeRunSync() shouldBe expected
    }

  }

}
