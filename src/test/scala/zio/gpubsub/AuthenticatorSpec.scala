package zio.gpubsub

import java.time.Instant
import java.net.http.{HttpClient => JHttpClient}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.specs2.Specification
import org.specs2.specification.{AfterAll, BeforeAll}
import zio.clock.Clock
import zio.gpubsub.httpclient.HttpClient
import zio.test.mock.MockClock
import zio.{Task, UIO, ZIO}

import scala.io.Source

class AuthenticatorSpec extends Specification with BeforeAll with AfterAll with TestRuntime {

  private val port = 3335
  private val server = new WireMockServer(new WireMockConfiguration().port(port))

  private val privateKey = KeyUtil.parsePrivateRsaKey(
    Source.fromInputStream(getClass.getResourceAsStream("/rsa_private.pem")).getLines.mkString("\n")
  )

  private val gcloudAuthenticator = GCloudAuthenticator("john@test.com", privateKey, s"http://localhost:$port/token")

  /**
    {"alg": "RS256","typ": "JWT"}
    {"scope": "https://www.googleapis.com/auth/pubsub","aud": "http://localhost:3335/token","iss": "john@test.com","exp": 2234571490,"iat": 2234567890}
    */
  private val expectedJwtForLocalhost3335Token =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzY29wZSI6Imh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvcHVic3ViIiwiYXVkIjoiaHR0cDovL2xvY2FsaG9zdDozMzM1L3Rva2VuIiwiaXNzIjoiam9obkB0ZXN0LmNvbSIsImV4cCI6MjIzNDU3MTQ5MCwiaWF0IjoyMjM0NTY3ODkwfQ==.eL0cMSY9fCW_yAVpRZx3J6dfvypsO61rLcXtoMPJSVgu9FPjFgeZN0Tn--TYfIr_yDtXntghIfiwF3SNgpQSNe7nm-p4P4Ksd3mPJgnVDRtaYFIzq5yv7ujS3NZCZZPSSGNxKcr9fX8-tKrhzUfWUeUoy1osk8AYSgYLNSDRkpWUNcthVh7-Luc5-ouwMx14laB85G2JCI3wJiM12j0N-gyGAH2_DUYhb37Fzg3DNH1CnKDNbznTkxdcXqwVz1zvPqfB6YaYsOLTP0gpHbfbYTcOI-Jh9gO6hzif_kZC9BUZz2P2e5DEIEhzNlcHwF8RLXRT2uD3F4OmsMC3dUbR3Q=="

  def is = "GCloudPubSubConfig".title ^ s2"""
    EmulatorAuthenticator returns None $emulatorAuthenticator
    GCloudAuthenticator:
      passes authentication happy-path $authHappyPath
      returns failure if body is not correct $authWrongBody
      returns failure if status is not 200 $authWrongStatus
    """

  override def beforeAll(): Unit = {
    server.start()
  }

  override def afterAll(): Unit = {
    server.stop()
  }
  private def getAuthWithGCloudAuthenticator =
    for {
      dependencies <- makeDeps(2234567890L)
      auth <- gcloudAuthenticator.getToken match {
        case Some(auth) => auth.provide(dependencies)
        case None       => ZIO.fail(new Exception("Expected Some(auth)"))
      }
    } yield auth

  def emulatorAuthenticator = {
    EmulatorAuthenticator.getToken must beEmpty
  }

  def authHappyPath =
    unsafeRun(
      WireMockUtil.withServer(server).use { server =>
        for {
          _ <- UIO(WireMockUtil.stubToken(server, expectedJwtForLocalhost3335Token, "test-token-value"))
          maybeAuth <- getAuthWithGCloudAuthenticator
        } yield maybeAuth
      }
    ) === AccessToken("test-token-value", Instant.ofEpochSecond(2234567890L + 3600))

  def authWrongBody =
    unsafeRun(
      WireMockUtil
        .withServer(server)
        .use { server =>
          for {
            _ <- UIO(
              server.stubFor(
                post(urlEqualTo("/token"))
                  .willReturn(
                    aResponse()
                      .withHeader("content-Type", "application/json")
                      .withBody("42")
                  )
              )
            )
            result <- getAuthWithGCloudAuthenticator
          } yield result
        }
        .either
    ) isLeft

  def authWrongStatus =
    unsafeRun(
      WireMockUtil
        .withServer(server)
        .use { server =>
          for {
            _ <- UIO(
              server.stubFor(
                post(urlEqualTo("/token"))
                  .willReturn(aResponse().withStatus(500))
              )
            )
            result <- getAuthWithGCloudAuthenticator
          } yield result
        }
        .either
    ).fold(_.getMessage, _ => "wrong") === "Invalid response code 500, expected 200."

  def makeDeps(currentClockSeconds: Long): Task[Clock with HttpClient] =
    for {
      clockMock <- MockClock.makeMock(MockClock.DefaultData.copy(currentTimeMillis = currentClockSeconds * 1000))
      httpClient <- Task.effect(JHttpClient.newBuilder.build)
    } yield new HttpClient with MockClock {
      val clock: MockClock.Service[Any] = clockMock
      val client: HttpClient.Service[Any] = new HttpClient.Live(httpClient)
    }
}
