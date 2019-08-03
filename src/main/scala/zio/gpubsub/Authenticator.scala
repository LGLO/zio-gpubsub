package zio.gpubsub

import java.security.{PrivateKey, Signature}
import java.time.Instant
import java.util.concurrent.TimeUnit

import org.asynchttpclient.{Dsl => AHC, Response}
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
    val data = jwtHeader ++ dot ++ urlEncoder.encode(claim.getBytes("UTF-8"))
    sign(data).map(signature => data ++ dot ++ signature)
  }

  private def sign(data: Array[Byte]) = ZIO.effect {
    val signer = Signature.getInstance("SHA256withRSA", "SunRsaSign")
    signer.initSign(privateKey)
    signer.update(data)
    urlEncoder.encode(signer.sign)
  }

  private def requestToken(jwt: Array[Byte]): ZIO[HttpClient, Throwable, Response] =
    zio.gpubsub.httpclient.execute(
      AHC
        .post(authUrl)
        .addFormParam("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        .addFormParam("assertion", new String(jwt))
        .build()
    )

  private def responseToAccessToken(response: Response, currentTimeSeconds: Long) =
    Task.effect {
      if (response.getStatusCode() == 200) {
        val authResponse = JsonParser(response.getResponseBody).fromJson[AuthResponse]
        val auth =
          new AccessToken(
            authResponse.access_token,
            Instant.ofEpochSecond(currentTimeSeconds + authResponse.expires_in)
          )
        Task.succeed(auth)
      } else Task.fail(new Exception(s"Invalid response code ${response.getStatusCode()}, expected 200."))
    }.flatten

}

object GCloudAuthenticator {
  private val jwtScope = "https://www.googleapis.com/auth/pubsub"
  private val urlEncoder = java.util.Base64.getUrlEncoder()
  private val jwtHeader = urlEncoder.encode("""{"alg":"RS256","typ":"JWT"}""".getBytes("UTF-8"))
  private val dot = ".".getBytes("UTF-8")

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
