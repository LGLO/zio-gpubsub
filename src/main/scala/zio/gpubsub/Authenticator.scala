package zio.gpubsub

import java.net.http.{HttpRequest, HttpResponse}
import java.net.http.HttpRequest.BodyPublishers
import java.net.{URI, URLEncoder}
import java.security.{PrivateKey, Signature}
import java.time.Instant
import java.util.concurrent.TimeUnit

import spray.json.DefaultJsonProtocol._
import spray.json.{JsonParser, RootJsonFormat}
import zio.clock.Clock
import zio.duration.Duration
import zio.gpubsub.httpclient._
import zio.{Ref, Task, ZIO}
import zio.gpubsub.httpclient.HttpClient

sealed trait Authenticator {
  def getToken: Option[ZIO[Clock with HttpClient, Throwable, AccessToken]]
}

case object EmulatorAuthenticator extends Authenticator {
  def getToken: Option[ZIO[Clock with HttpClient, Throwable, AccessToken]] = None
}

case class GCloudAuthenticator(clientEmail: String, privateKey: PrivateKey, authUrl: String) extends Authenticator {

  import GCloudAuthenticator._

  def getToken: Option[ZIO[Clock with HttpClient, Throwable, AccessToken]] =
    Some(
      for {
        currentTime <- zio.clock.currentTime(TimeUnit.SECONDS)
        signed <- makeJwt(currentTime)
        response <- requestToken(signed)
        auth <- responseToAccessToken(response, currentTime)
      } yield auth
    )

  private def makeJwt(currentTimeSeconds: Long) = {
    val iat = currentTimeSeconds
    val exp = iat + 3600
    val claim = s"""{"scope":"$jwtScope","aud":"$authUrl","iss":"$clientEmail","exp":$exp,"iat":$iat}"""
    val data = jwtHeader ++ dot ++ base64UrlEncoder.encode(claim.getBytes("UTF-8"))
    sign(data).map(signature => data ++ dot ++ signature)
  }

  private def sign(data: Array[Byte]) = ZIO.effect {
    val signer = Signature.getInstance("SHA256withRSA", "SunRsaSign")
    signer.initSign(privateKey)
    signer.update(data)
    base64UrlEncoder.encode(signer.sign)
  }

  private def requestToken(jwt: Array[Byte]): ZIO[HttpClient, Throwable, HttpResponse[String]] =
    zio.gpubsub.httpclient.execute(
      HttpRequest
        .newBuilder(URI.create(authUrl))
        .POST(BodyPublishers.ofString(bodyConstantPart + URLEncoder.encode(new String(jwt))))
        .header("content-type", "application/x-www-form-urlencoded")
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def responseToAccessToken(response: HttpResponse[String], currentTimeSeconds: Long) =
    Task.effect {
      if (response.statusCode == 200) {
        val authResponse = JsonParser(response.body).fromJson[AuthResponse]
        val auth =
          new AccessToken(
            authResponse.access_token,
            Instant.ofEpochSecond(currentTimeSeconds + authResponse.expires_in)
          )
        Task.succeed(auth)
      } else Task.fail(new Exception(s"Invalid response code ${response.statusCode}, expected 200."))
    }.flatten

}

object GCloudAuthenticator {
  private val jwtScope = "https://www.googleapis.com/auth/pubsub"
  private val base64UrlEncoder = java.util.Base64.getUrlEncoder()
  private val jwtHeader = base64UrlEncoder.encode("""{"alg":"RS256","typ":"JWT"}""".getBytes("UTF-8"))
  private val dot = ".".getBytes("UTF-8")
  private val bodyConstantPart =
    URLEncoder.encode("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=")

  private[gpubsub] case class AuthResponse(access_token: String, token_type: String, expires_in: Int)
  private implicit val oAuthResponseJsonFormat: RootJsonFormat[AuthResponse] = jsonFormat3(AuthResponse)
}

case class AccessToken(value: String, validUntil: Instant) {
  override def equals(other: Any): Boolean = other match {
    case that: AccessToken =>
      value == that.value && validUntil == that.validUntil
    case _ => false
  }

  override def hashCode: Int = java.util.Objects.hash(value, validUntil)

  override def toString: String =
    "AccessToken(value=" + value + ",validUntil=" + validUntil + ")"
}

class TokenRefresher(
    getToken: ZIO[Clock, Throwable, AccessToken],
    tokenRef: Ref[Option[AccessToken]],
    margin: Duration
) {

  val getFreshToken =
    for {
      currentTime <- zio.clock.currentTime(TimeUnit.MILLISECONDS)
      tokenOpt <- tokenRef.get
      validToken <- tokenOpt.fold(getToken)(
        token =>
          if (isValidForLongEnough(token, currentTime)) ZIO.succeed(token)
          else getToken
      )
      _ <- tokenRef.set(Some(validToken))
    } yield validToken

  private def isValidForLongEnough(token: AccessToken, currentTimeMillis: Long) =
    token.validUntil.toEpochMilli > currentTimeMillis + margin.toMillis

}
