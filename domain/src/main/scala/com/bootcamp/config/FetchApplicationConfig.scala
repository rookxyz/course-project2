package com.bootcamp.config

import cats.effect.IO
import com.bootcamp.config.domain.AppConfig
import com.typesafe.config.ConfigFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource

trait FetchApplicationConfig[A] {
  def apply: IO[A]
}

object FetchApplicationConfig extends FetchApplicationConfig[AppConfig] {
  def apply: IO[AppConfig] = {
    val config = ConfigSource
      .fromConfig(ConfigFactory.load("application/application"))
    println(config.config())
    config.loadF[IO, AppConfig]()
  }
}
