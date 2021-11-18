import Dependencies._

lazy val global = project
  .in(file("."))
  .aggregate(
    http,
    streams
  )
  .settings(
    scalaVersion := "2.12.10",
    name := "course-project2",
    version := "0.1",
    organization := "com.bootcamp",
    libraryDependencies ++= globalLibs
  )

lazy val http = (project in file("recommender-service"))
  .settings(
    name := "recommender-service",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= httpLibs
  )

lazy val streams = (project in file("stream-processing"))
  .settings(
    name := "stream-processing",
    libraryDependencies ++= streamLibs ++ testLibs

  )