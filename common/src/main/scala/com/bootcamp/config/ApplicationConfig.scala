package com.bootcamp.config

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
  maxRetries: Int,
  retryDelayMillis: Int,
  accessKeyId: String, // TODO need to read this from file system later
  secretAccessKey: String, // TODO need to read this from file system later
  playerProfileTableName: String,
  clusterTableName: String,
)
trait ApplicationConfig

final case class StreamConfig(
  kafka: KafkaConfig,
  db: DbConfig,
) extends ApplicationConfig

final case class RecommenderHttpConfig(
  db: DbConfig,
  http: HttpConfig,
) extends ApplicationConfig
