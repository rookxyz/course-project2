import sbt._

object Dependencies {

  val Http4sVersion = "0.23.6"
  val CirceVersion = "0.14.1"
  val MunitVersion = "0.7.29"
  val LogbackVersion = "1.2.6"
  val MunitCatsEffectVersion = "1.0.6"
  val catsEffectVersion = "3.2.9"
  val fs2Version = "3.1.6"
  val kafkaVersion = "2.8.1"
  val embeddedKafkaVersion = "2.8.1"
  val pureConfigVersion = "0.17.0"

  val globalLibs = Seq(
    "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  )
  val httpLibs = Seq(
    "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
    "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
    "org.http4s"      %% "http4s-circe"        % Http4sVersion,
    "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
    "io.circe"        %% "circe-generic"       % CirceVersion,
    "org.scalameta"   %% "munit"               % MunitVersion           % Test,
    "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
    "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
  )


    val streamLibs = Seq(
      "com.github.fd4s" %% "fs2-kafka" % "2.2.0",
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.apache.kafka" % "kafka-clients" % kafkaVersion,
      "com.github.pureconfig" %% "pureconfig" % pureConfigVersion,
      "org.slf4j" % "slf4j-api" % "1.7.5",  // TODO perhaps this can be removed
      "org.slf4j" % "slf4j-simple" % "1.7.5"  // TODO perhaps this can be removed
    )

  val testLibs = Seq(
    "org.scalatest" %% "scalatest" % "3.2.10" % "test",
    "io.github.embeddedkafka" %% "embedded-kafka" % embeddedKafkaVersion % Test,
    "org.scalameta"   %% "munit"               % MunitVersion           % Test,
    "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
  )
}
