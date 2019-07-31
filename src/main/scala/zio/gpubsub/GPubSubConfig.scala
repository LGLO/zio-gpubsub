package zio.gpubsub

import scala.sys
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser
import zio.{Task, ZIO}

/**
  * Configuration required for the client.
  * It can be either "real" config or one for emulator.
  */
case class GPubSubConfig(authenticator: Authenticator, host: String, projectId: String)

object GPubSubConfig {

  val defaultGoogleTokenUrl = "https://www.googleapis.com/oauth2/v4/token"
  val defaultGoogleApisUrl = "https://www.googleapis.com"

  def readFromEnv(): Task[GPubSubConfig] = readFromEnv(sys.props.toMap ++ sys.env)

  def readFromEnv(env: Map[String, String]): Task[GPubSubConfig] =
    ZIO.effect {
      env
        .get("GOOGLE_APPLICATION_CREDENTIALS")
        .map(readGoogleApplicationCredentialsFile)
        .orElse {
          (env.get("PUBSUB_EMULATOR_HOST").zip(env.get("PUBSUB_PROJECT_ID")).headOption).map {
            case (host, appId) => ZIO.succeed(GPubSubConfig(EmulatorAuthenticator, "http://" + host, appId))
          }
        }
        .getOrElse {
          val msg =
            "Either 'GOOGLE_APPLICATION_CREDENTIALS' or 'PUBSUB_EMULATOR_HOST' and 'PUBSUB_PROJECT_ID' have to be set!"
          ZIO.fail(new Exception(msg))
        }
    }.flatten

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
