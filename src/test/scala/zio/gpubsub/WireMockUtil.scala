package zio.gpubsub
import java.util.concurrent.Semaphore

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import zio.console.{Console, putStrLn}
import zio.{Task, UIO, ZIO, ZManaged}

object WireMockUtil {

  private val mutex = new Semaphore(1)

  def withServer(server: WireMockServer) =
    ZManaged.make[Console, Throwable, WireMockServer](Task {
      mutex.acquire()
      server.resetAll()
      server
    })(
      server =>
        for {
          _ <- putStrLn("Unmatched requests:")
          _ <- ZIO.sequence(server.findAllUnmatchedRequests().toArray().map(r => putStrLn(r.toString())))
          _ <- UIO(mutex.release())
        } yield ()
    )

  def stubToken(server: WireMockServer, jwtValue: String, tokenValue: String) =
    server.stubFor(
      post(urlEqualTo("/token"))
        .withHeader("content-type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(containing(jwtValue.replaceAll("=", "%3D")))
        .willReturn(
          aResponse()
            .withHeader("content-Type", "application/json")
            .withBody(s"""{"access_token":"$tokenValue","token_type": "JWT","expires_in": 3600}""")
        )
    )
}
