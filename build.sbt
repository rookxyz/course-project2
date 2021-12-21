import Dependencies._

lazy val global = project
  .in(file("."))
  .aggregate(
    http,
    streams,
  )
  .settings(
    scalaVersion := "2.12.10",
    name := "course-project2",
    version := "0.1",
    organization := "com.bootcamp",
//    libraryDependencies ++= globalLibs,
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    libraryDependencies ++= domainLibs ++ globalLibs,
  )

lazy val playerRepository = (project in file("player-repository"))
  .settings(
    name := "player-repository",
    libraryDependencies ++= repositoryLibs ++ globalLibs,
  )
  .dependsOn(common)

lazy val http = (project in file("recommender-service"))
  .settings(
    name := "recommender-service",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= httpLibs ++ globalLibs,
  )
  .dependsOn(common, playerRepository)

lazy val streams = (project in file("stream-processing"))
  .settings(
    name := "stream-processing",
    libraryDependencies ++= streamLibs ++ globalLibs ++ testLibs,
    Test / fork := true,
  )
  .dependsOn(common, playerRepository)

lazy val integration = project
  .settings(name := "integration")
  .dependsOn(http, streams)
