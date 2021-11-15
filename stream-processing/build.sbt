val catsEffectVersion = "3.2.9"
val fs2Version = "3.1.6"
val kafkaVersion = "2.8.1"


lazy val streams = (project in file("."))
  .settings(
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
