package com.bootcamp.streamreader.domain

import pureconfig._
import pureconfig.generic.auto._

case class Port(number: Int) extends AnyVal

case class KafkaConfig (
                       host: String,
                       port: Port,
                       topic: String
                       )