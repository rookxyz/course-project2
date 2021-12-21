package com.bootcamp.config

import cats.effect.{IO, Sync}
import com.typesafe.config.ConfigFactory
import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource

import scala.reflect.ClassTag

//trait FetchApplicationConfig[A] {
//  def apply: IO[A]
//}

object FetchStreamingConfig extends {
//  def apply: IO[AppConfig] = {
//    val config = ConfigSource
//      .fromConfig(ConfigFactory.load("application/application"))
//    println(config.config())
//    config.loadF[IO, AppConfig]()
//  }
  implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
  def apply: IO[StreamConfig] =
    IO.delay(ConfigSource.default.at("application").loadOrThrow[StreamConfig])
}

object FetchRecommenderHttpConfig extends {
  implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
  def apply: IO[RecommenderHttpConfig] =
    IO.delay(ConfigSource.default.at("application").loadOrThrow[RecommenderHttpConfig])
}
