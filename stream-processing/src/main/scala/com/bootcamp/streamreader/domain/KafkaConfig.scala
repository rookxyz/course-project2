package com.bootcamp.streamreader.domain

final case class Port(value: Int) extends AnyVal


final case class KafkaConfig (
                       host: String,
                       port: Port,
                       topic: String
                       )

final case class AppConfig (
                           kafka: KafkaConfig
                           )