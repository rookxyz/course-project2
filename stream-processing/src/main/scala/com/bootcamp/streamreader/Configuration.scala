package com.bootcamp.streamreader

import com.bootcamp.streamreader.domain.AppConfig
import com.typesafe.config.ConfigFactory
import pureconfig.ConfigSource
import pureconfig.ConfigReader.Result
import pureconfig.generic.auto._

trait Configuration[A] {
  def fetchAppConfig: Result[A]
}

object Configuration extends Configuration[AppConfig] {
  override def fetchAppConfig: Result[AppConfig] =
    ConfigSource
      .fromConfig(ConfigFactory.load("application")) // separate service
      .load[AppConfig]
}
