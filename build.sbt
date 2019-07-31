name := "zio-gcp-pubsub"
scalaVersion in ThisBuild := "2.12.8"

scalacOptions += "-Ywarn-unused"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.0-RC10-1",
  "org.asynchttpclient" % "async-http-client" % "2.10.1",
  "dev.zio" %% "zio-interop-java" % "1.1.0.0-RC1",
  "io.spray" %% "spray-json" % "1.3.5",
  "org.specs2" %% "specs2-core" % "4.6.0" % Test,
  "dev.zio" %% "zio-testkit" % "1.0.0-RC10-1" % Test,
  "com.pauldijou" %% "jwt-core" % "3.1.0" % Test,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.24.0" % Test
)

Compile / run / fork := true
Global / cancelable := true
