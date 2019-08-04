name := "zio-gcp-pubsub"

scalaVersion in ThisBuild := "2.12.8"

scalacOptions += "-Ywarn-unused"

val zioVersion = "1.0.0-RC11"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-interop-java" % "1.1.0.0-RC1",
  "io.spray" %% "spray-json" % "1.3.5",
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "org.specs2" %% "specs2-core" % "4.6.0" % Test,
  "com.pauldijou" %% "jwt-core" % "3.1.0" % Test,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.24.0" % Test
)

Compile / run / fork := true
Global / cancelable := true

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
