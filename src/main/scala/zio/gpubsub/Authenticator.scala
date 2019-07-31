package zio.gpubsub

import java.security.{PrivateKey, Signature}
import java.time.Instant
import java.util.concurrent.TimeUnit

import org.asynchttpclient.{AsyncHttpClient, ListenableFuture, Response}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonParser, RootJsonFormat}
import zio.{Task, ZIO}
import zio.clock.Clock
import zio.interop.javaconcurrent._

sealed trait Authenticator {
  def apply(client: AsyncHttpClient): Option[ZIO[Clock, Throwable, Auth]]
}

case object EmulatorAuthenticator extends Authenticator {
  def apply(client: AsyncHttpClient): Option[ZIO[Clock, Throwable, Auth]] = None
}

case class GCloudAuthenticator(clientEmail: String, privateKey: PrivateKey, authUrl: String) extends Authenticator {

  import GCloudAuthenticator._

  def apply(client: AsyncHttpClient): Option[ZIO[Clock, Throwable, Auth]] =
    Some(
      for {
        clock <- ZIO.environment[Clock]
        currentTime <- clock.clock.currentTime(TimeUnit.SECONDS)
        signed <- makeJwt(currentTime)
        response <- requestToken(signed)(client)
        auth <- responseToAuth(response, currentTime)
      } yield auth
    )

  private def makeJwt(currentTimeSeconds: Long) = {
    val iat = currentTimeSeconds
    val exp = iat + 3600
    val claim = s"""{"scope":"$jwtScope","aud":"$authUrl","iss":"$clientEmail","exp":$exp,"iat":$iat}"""
    val data = jwtHeader ++ dot ++ urlEncoder.encode(claim.getBytes("UTF-8"))
    sign(data).map(signature => data ++ dot ++ signature)
  }

  private def sign(data: Array[Byte]) = ZIO.effect {
    val signer = Signature.getInstance("SHA256withRSA", "SunRsaSign")
    signer.initSign(privateKey)
    signer.update(data)
    urlEncoder.encode(signer.sign)
  }
  //TODO: ZIO[ZHttpClient,...,...]
  private def requestToken(jwt: Array[Byte])(httpClient: AsyncHttpClient): ZIO[Any, Throwable, Response] = {
    ZIO
      .effect {
        httpClient
          .preparePost(authUrl)
          .addFormParam("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
          .addFormParam("assertion", new String(jwt))
          .execute()
      }
      .flatMap(toTask)
  }

  private def responseToAuth(response: Response, currentTimeSeconds: Long) =
    Task.effect {
      if (response.getStatusCode() == 200) {
        val authResponse = JsonParser(response.getResponseBody).fromJson[AuthResponse]
        val auth =
          new Auth(authResponse.access_token, Instant.ofEpochSecond(currentTimeSeconds + authResponse.expires_in))
        Task.succeed(auth)
      } else Task.fail(new Exception(s"Invalid response code ${response.getStatusCode()}, expected 200."))
    }.flatten

  private def toTask[R](lf: ListenableFuture[R]): Task[R] = Task.fromCompletionStage(() => lf.toCompletableFuture())
}

object GCloudAuthenticator {
  private val jwtScope = "https://www.googleapis.com/auth/pubsub"
  private val urlEncoder = java.util.Base64.getUrlEncoder()
  private val jwtHeader = urlEncoder.encode("""{"alg":"RS256","typ":"JWT"}""".getBytes("UTF-8"))
  private val dot = ".".getBytes("UTF-8")

  private[gpubsub] case class AuthResponse(access_token: String, token_type: String, expires_in: Int)
  private implicit val oAuthResponseJsonFormat: RootJsonFormat[AuthResponse] = jsonFormat3(AuthResponse)
}

case class Auth(accessToken: String, validUntil: Instant)
