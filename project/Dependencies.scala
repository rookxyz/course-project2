import sbt._

object Dependencies {

  val http4sVersion = "0.23.6"
  val circeVersion = "0.14.1"
  val circeFs2Version = "0.14.0"
  val munitVersion = "0.7.29"
  val logbackVersion = "1.2.6"
  val munitCatsEffectVersion = "1.0.6"
  val catsEffectVersion = "3.2.9"
  val fs2Version = "3.1.6"
  val fs2KafkaVersion = "2.2.0"
  val kafkaVersion = "2.8.1"
  val embeddedKafkaVersion = "2.8.1"
  val pureConfigVersion = "0.17.1"
  val scalaTestVersion = "3.2.10"
  val amazonAwsVersion = "1.11.864"
  val testVersion = "test"

  val globalLibs = Seq(
    "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion,
    "org.typelevel"         %% "cats-effect"            % catsEffectVersion,
  )

  val repositoryLibs = Seq(
    "com.amazonaws" % "aws-java-sdk-dynamodb" % amazonAwsVersion,
  )

  val domainLibs = Seq(
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
  )

  val httpLibs = Seq(
    "org.http4s"    %% "http4s-ember-server" % http4sVersion,
    "org.http4s"    %% "http4s-ember-client" % http4sVersion,
    "org.http4s"    %% "http4s-circe"        % http4sVersion,
    "org.http4s"    %% "http4s-dsl"          % http4sVersion,
    "org.scalameta" %% "munit"               % munitVersion           % Test,
    "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test,
    "ch.qos.logback" % "logback-classic"     % logbackVersion,
  )

  val streamLibs = Seq(
    "com.github.fd4s"       %% "fs2-kafka"     % fs2KafkaVersion,
    "co.fs2"                %% "fs2-core"      % fs2Version,
    "io.circe"              %% "circe-fs2"     % circeFs2Version,
    "org.apache.kafka"       % "kafka-clients" % kafkaVersion,
    "com.github.pureconfig" %% "pureconfig"    % pureConfigVersion,
  )

  val testLibs = Seq(
    "org.scalatest"           %% "scalatest"           % scalaTestVersion       % testVersion,
    "io.github.embeddedkafka" %% "embedded-kafka"      % embeddedKafkaVersion   % Test,
    "org.scalameta"           %% "munit"               % munitVersion           % Test,
    "org.typelevel"           %% "munit-cats-effect-3" % munitCatsEffectVersion % Test,
  )
}
