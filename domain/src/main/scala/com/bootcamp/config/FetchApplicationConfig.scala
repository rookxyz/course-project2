package com.bootcamp.config

import cats.effect.{IO, Sync}
import com.bootcamp.config.domain.AppConfig
import com.typesafe.config.ConfigFactory
import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource

//trait FetchApplicationConfig[A] {
//  def apply: IO[A]
//}

object FetchApplicationConfig extends {
//  def apply: IO[AppConfig] = {
//    val config = ConfigSource
//      .fromConfig(ConfigFactory.load("application/application"))
//    println(config.config())
//    config.loadF[IO, AppConfig]()
//  }
  implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
  def apply[F[_]: Sync]: F[AppConfig] =
    Sync[F].delay(ConfigSource.default.at("application").loadOrThrow[AppConfig])

}
