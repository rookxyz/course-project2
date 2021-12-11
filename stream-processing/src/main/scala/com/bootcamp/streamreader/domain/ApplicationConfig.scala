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
  host: String,
  port: Port,
  accessKeyId: String,
  secretAccessKey: String, // TODO need to read this from system env variable
)

final case class AppConfig(
  kafka: KafkaConfig,
  http: HttpConfig,
  db: DbConfig,
)
