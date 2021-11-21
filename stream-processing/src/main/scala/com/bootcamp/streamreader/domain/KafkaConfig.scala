package com.bootcamp.streamreader.domain

case class Port(value: Int) extends AnyVal

case class KafkaConfig (
                       host: String,
                       port: Port,
                       topic: String
                       )