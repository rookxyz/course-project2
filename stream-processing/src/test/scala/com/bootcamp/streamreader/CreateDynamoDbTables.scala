package com.bootcamp.streamreader

import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey, Table}
import com.amazonaws.services.dynamodbv2.model.{
  AttributeDefinition,
  CreateTableRequest,
  GlobalSecondaryIndex,
  KeySchemaElement,
  Projection,
  ProvisionedThroughput,
  ScalarAttributeType,
}
import com.bootcamp.config.DbConfig

object CreateDynamoDbTables {
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

    val t3 = db.createTable(createClustersTableReq)

    t2.waitForActive()
    t3.waitForActive()

    Map("profiles" -> t2, "clusters" -> t3)

  }

  def fillClustersTable(clustersTable: Table, playersN: Int, clusters: Int): Unit =
    (1 to playersN).map { i =>
      val cluster = scala.util.Random.nextInt(clusters)
      clustersTable
        .putItem(
          new Item()
            .withPrimaryKey(new PrimaryKey().addComponent("playerId", s"p$i"))
            .withNumber("cluster", cluster),
        )
        .ensuring(true)
    }

  def deleteDbTables(implicit db: DynamoDB): Unit = db.listTables().forEach { t =>
    t.delete()
    t.waitForDelete()

  }

}
