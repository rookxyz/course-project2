val Http4sVersion = "0.23.6"
val CirceVersion = "0.14.1"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.2.6"
val MunitCatsEffectVersion = "1.0.6"
val catsEffectVersion = "3.2.9"
val fs2Version = "3.1.6"
val kafkaVersion = "2.8.1"

lazy val global = project
  .in(file("."))
  .aggregate(
    http,
    streams
  )
  .settings(
    scalaVersion := "2.12.10",
    name := "course-project2",
    version := "0.1"
  )

lazy val http = (project in file("recommender-service"))
  .settings(
    organization := "com.bootcamp",
    name := "recommender-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.10",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.0" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val streams = (project in file("stream-processing"))
  .settings(
    organization := "com.bootcamp",
    name := "stream-processing",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.10",
    libraryDependencies += "com.github.fd4s" %% "fs2-kafka" % "2.2.0",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.apache.kafka" % "kafka-clients" % kafkaVersion,
      "io.github.embeddedkafka" %% "embedded-kafka" % "3.0.0" % Test

    ),
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.10",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test"

  )