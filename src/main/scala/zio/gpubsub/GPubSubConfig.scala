package zio.gpubsub

import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser
import zio.system.System
import zio.{Task, ZIO}

/**
  * Configuration required for the client.
  * It can be either "real" config or one for emulator.
  */
case class GPubSubConfig(authenticator: Authenticator, host: String, projectId: String)

object GPubSubConfig {

  val defaultGoogleTokenUrl = "https://www.googleapis.com/oauth2/v4/token"
  val defaultGoogleApisUrl = "https://www.googleapis.com"

  def readFromEnv(): ZIO[System, Throwable, GPubSubConfig] =
    propertyOrEnv("GOOGLE_APPLICATION_CREDENTIALS").flatMap {
      _.map(readGoogleApplicationCredentialsFile)
        .getOrElse {
          propertyOrEnv("PUBSUB_EMULATOR_HOST").zip(propertyOrEnv("PUBSUB_PROJECT_ID")).flatMap {
            case (Some(host), Some(appId)) => ZIO.succeed(GPubSubConfig(EmulatorAuthenticator, "http://" + host, appId))
            case _ =>
              val msg =
                "Either 'GOOGLE_APPLICATION_CREDENTIALS' or 'PUBSUB_EMULATOR_HOST' and 'PUBSUB_PROJECT_ID' have to be set!"
              ZIO.fail(new Exception(msg))
          }
        }
    }

  private def propertyOrEnv(key: String): ZIO[System, Throwable, Option[String]] =
    zio.system.property(key).flatMap {
      case Some(value) => ZIO.succeed(Some(value))
      case None        => zio.system.env(key)
    }

  def readGoogleApplicationCredentialsFile(path: String): Task[GPubSubConfig] =
    Task.effect {
      val content = scala.io.Source.fromFile(path).getLines().mkString
      val json = JsonParser(content).asJsObject.fields
      val privateKey = KeyUtil.parsePrivateRsaKey(json("private_key").fromJson[String])
      val clientEmail = json("client_email").fromJson[String]
      val projectId = json("project_id").fromJson[String]
      GPubSubConfig(
        GCloudAuthenticator(clientEmail, privateKey, defaultGoogleTokenUrl),
        defaultGoogleApisUrl,
        projectId
      )
    }
}
