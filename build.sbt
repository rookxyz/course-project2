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
    version := "1.0",
    organization := "com.bootcamp",
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    libraryDependencies ++= domainLibs ++ repositoryLibs ++ globalLibs ++ testLibs,
  )

lazy val http = (project in file("recommender-service"))
  .settings(
    name := "recommender-service",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= httpLibs ++ globalLibs ++ testLibs,
  )
  .dependsOn(common)

lazy val streams = (project in file("stream-processing"))
  .settings(
    name := "stream-processing",
    libraryDependencies ++= streamLibs ++ globalLibs ++ testLibs,
    Test / fork := true,
  )
  .dependsOn(common)

lazy val integration = project
  .settings(
    name := "integration",
    libraryDependencies ++= httpLibs ++ streamLibs ++ globalLibs ++ testLibs,
    Test / fork := true,
  )
  .dependsOn(http, streams % "compile->compile;test->test")
