package com.bootcamp.streamreader.domain

import scala.concurrent.duration.FiniteDuration

final case class Port(value: Int) extends AnyVal

final case class KafkaConfig(
  host: String,
  port: Port,
  topic: String,
  groupId: String,
  clientId: String,
  chunkSize: Int,
  chunkTimeout: FiniteDuration,
)
final case class HttpConfig(
  host: String,
  port: Port,
)
final case class DbConfig(
  endpoint: String,
  accessKeyId: String, // TODO need to read this from file system later
  secretAccessKey: String, // TODO need to read this from file system later
  playerProfileTableName: String,
  clusterTableName: String,
)

final case class AppConfig(
  kafka: KafkaConfig,
  http: HttpConfig,
  db: DbConfig,
)
