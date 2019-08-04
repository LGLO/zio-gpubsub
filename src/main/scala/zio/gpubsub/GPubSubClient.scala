package zio.gpubsub

import java.util.concurrent.TimeUnit

import zio.{Ref, Task, ZIO}
import zio.clock.Clock
import zio.gpubsub.httpclient.HttpClient
import java.net.http.HttpResponse
import java.net.http.HttpRequest
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

trait GPubSubClient {
  def client: GPubSubClient.Service
}

object GPubSubClient {

  sealed trait CreationResult
  case object Created extends CreationResult
  case object AlreadyExists extends CreationResult
  final class NotCreated(val reason: String) extends CreationResult
  trait Service {
    def createTopic(topic: String): ZIO[HttpClient, Throwable, CreationResult]
    def createSubscription(name: String, topic: String): ZIO[HttpClient, Throwable, CreationResult]
    def publish(topic: String, msgs: Seq[PublishMessage]): ZIO[Clock with HttpClient, Throwable, Seq[String]]
    def pull(
        subscription: String,
        maxMessages: Int,
        returnImmediately: Boolean = false
    ): ZIO[Clock with HttpClient, Throwable, Seq[ReceivedMessage]]
  }

  class Live(
      config: GPubSubConfig,
      serdes: JsonSerdes,
      auth: Ref[Option[AccessToken]]
  ) extends GPubSubClient.Service {

    val isEmulated = true //test for URL
    val projectId = config.projectId
    val projectPrefix = "projects/" + projectId
    val urlPrefix = config.host + "/v1/" + projectPrefix

    private def post(path: String, jsonBody: Option[Array[Byte]]): ZIO[HttpClient, Throwable, HttpResponse[String]] = {
      val builder0 = HttpRequest.newBuilder(URI.create(urlPrefix + path))
      val builder1 = jsonBody.fold(builder0.POST(BodyPublishers.noBody)) { body =>
        builder0.header("content-type", "application/json").POST(BodyPublishers.ofByteArray(body))
      }
      val request = builder1.build()
      zio.gpubsub.httpclient.execute(request, BodyHandlers.ofString)
    }

    private def put(path: String, jsonBody: Option[Array[Byte]]): ZIO[HttpClient, Throwable, HttpResponse[String]] = {
      val builder0 = HttpRequest.newBuilder(URI.create(urlPrefix + path))
      val builder1 = jsonBody.fold(builder0.PUT(BodyPublishers.noBody)) { body =>
        builder0.header("content-type", "application/json").PUT(BodyPublishers.ofByteArray(body))
      }
      val request = builder1.build()
      zio.gpubsub.httpclient.execute(request, BodyHandlers.ofString)
    }

    private def responseToCreationCode(result: HttpResponse[String]) =
      if (result.statusCode == 200) Created
      else if (result.statusCode == 409) AlreadyExists
      else new GPubSubClient.NotCreated(s"status code = ${result.statusCode}, body = ${result.body}")

    def createTopic(topic: String): ZIO[HttpClient, Throwable, CreationResult] =
      put(s"/topics/$topic", None).map(responseToCreationCode)

    def createSubscription(name: String, topic: String): ZIO[HttpClient, Throwable, CreationResult] =
      put("/subscriptions/" + name, Some(s"""{"topic":"${projectPrefix}/topics/$topic"}""".getBytes))
        .map(responseToCreationCode)

    def publish(topic: String, msgs: Seq[PublishMessage]) =
      for {
        authHeader <- getAuthHeader
        response <- post("/topics/" + topic + ":publish", Some(serdes(msgs)))
        publishResponse <- Task.effect(serdes.parsePublishResponse(response.body.getBytes).get)
      } yield publishResponse.messageIds

    def pull(subscription: String, maxMessages: Int, returnImmediately: Boolean = false) =
      post("/subscriptions/" + subscription + ":pull", Some(serdes(new PullRequest(returnImmediately, maxMessages))))
        .map { response =>
          serdes
            .parsePullResponse(response.body.getBytes)
            .get
            .receivedMessages
            .getOrElse(Seq.empty[ReceivedMessage])
        }

    private def getAuthHeader: ZIO[Clock with HttpClient, Throwable, Seq[(String, String)]] =
      config.authenticator.getToken match {
        case None => ZIO.succeed(Seq.empty)
        case Some(getAuth) =>
          for {
            currentTime <- zio.clock.currentTime(TimeUnit.SECONDS)
            maybeAuth <- auth.get
            currentAuthIsValid = maybeAuth.exists(_.validUntil.getEpochSecond > currentTime + 60)
            newAuth <- if (currentAuthIsValid) ZIO.succeed(maybeAuth.get) else getAuth
            _ <- auth.set(Some(newAuth))
          } yield Seq("auth-bearer" -> newAuth.value)
      }
  }

  object Live {
    def apply[R, E](
        getConfig: ZIO[R, E, GPubSubConfig]
    ): ZIO[R, E, Live] =
      for {
        config <- getConfig
        auth <- Ref.make(Option.empty[AccessToken])
      } yield new Live(config, SprayJsonSupport.SprayJsonSerdes, auth)
  }
}
