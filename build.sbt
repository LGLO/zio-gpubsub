name := "zio-gcp-pubsub"
scalaVersion in ThisBuild := "2.12.8"

scalacOptions += "-Ywarn-unused"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.0-RC8-12",
  "org.asynchttpclient" % "async-http-client" % "2.10.1",
  "dev.zio" %% "zio-interop-java" % "1.1.0.0-RC1",
  "io.spray" %% "spray-json" % "1.3.5",
  "org.specs2" %% "specs2-core" % "4.6.0" % Test
)

Compile / run / fork := true
Global / cancelable := true
