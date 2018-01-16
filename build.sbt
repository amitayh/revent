name := "revent"

version := "0.1.2-SNAPSHOT"

scalaVersion := "2.12.2"

homepage := Some(url("https://github.com/amitayh/revent"))
scmInfo := Some(ScmInfo(url("https://github.com/amitayh/revent"), "git@github.com:amitayh/revent.git"))
developers := List(
  Developer(
    "amitayh",
    "Amitay Horwitz",
    "amitayh@gmail.com",
    url("https://github.com/amitayh")))
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
publishMavenStyle := true
publishTo := Some(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Opts.resolver.sonatypeStaging
)

libraryDependencies ++= Seq(
  // Core
  "org.typelevel" %% "cats-core" % "1.0.0-MF",

  // JSON parsing
  "io.circe" %% "circe-core" % "0.8.0",
  "io.circe" %% "circe-generic" % "0.8.0",
  "io.circe" %% "circe-parser" % "0.8.0",

  // EventStore
  "com.geteventstore" %% "eventstore-client" % "4.1.1",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % "2.8.7",

  // Cassandra
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.3.0",

  // Docker
  "com.whisk" %% "docker-testkit-specs2" % "0.9.6" % Test,
  "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.6" % Test,

  "org.specs2" %% "specs2-core" % "3.8.9" % Test,
  "org.specs2" %% "specs2-mock" % "3.8.9" % Test
)

scalacOptions += "-Ypartial-unification"
scalacOptions in Test ++= Seq("-Yrangepos")
