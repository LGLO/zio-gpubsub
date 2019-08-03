package zio.gpubsub

import java.util.concurrent.TimeUnit

import org.asynchttpclient.{Dsl => AHC, Response}
import zio.{Ref, Task, ZIO}
import zio.clock.Clock
import zio.gpubsub.ZioGPubSubClient.{AlreadyExists, Created, CreationResult}
import zio.gpubsub.httpclient.HttpClient

trait ZioGPubSubClient {
  def client: ZioGPubSubClient.Service
}

object ZioGPubSubClient {

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
}

class ProdZioGPubSubClient(
    config: GPubSubConfig,
    serdes: JsonSerdes,
    auth: Ref[Option[AccessToken]]
) extends ZioGPubSubClient.Service {

  val isEmulated = true //test for URL
  val projectId = config.projectId
  val projectPrefix = "projects/" + projectId
  val urlPrefix = config.host + "/v1/" + projectPrefix

  // private def get(url: String): ZIO[Any, Throwable, String] = {
  //   Task
  //     .fromCompletionStage(ZIO.effect(client.prepareGet(url).execute().toCompletableFuture()))
  //     .map(_.getResponseBody())
  // }

  private def post(path: String, jsonBody: Option[Array[Byte]]): ZIO[HttpClient, Throwable, Response] = {
    val builder0 = AHC.post(urlPrefix + path)
    val builder1 = jsonBody.fold(builder0) { body =>
      builder0.addHeader("content-type", Seq("application/json")).setBody(body)
    }
    val request = builder1.build()
    zio.gpubsub.httpclient.execute(request)
  }

  private def put(path: String, jsonBody: Option[Array[Byte]]): ZIO[HttpClient, Throwable, Response] = {
    val builder0 = AHC.put(urlPrefix + path)
    val builder1 = jsonBody.fold(builder0) { body =>
      builder0.addHeader("content-type", Seq("application/json")).setBody(body)
    }
    val request = builder1.build
    zio.gpubsub.httpclient.execute(request)
  }

  private def responseToCreationCode(result: Response) =
    if (result.getStatusCode() == 200) Created
    else if (result.getStatusCode() == 409) AlreadyExists
    else new ZioGPubSubClient.NotCreated(s"status code = ${result.getStatusCode}, body = ${result.getResponseBody()}")

  def createTopic(topic: String): ZIO[HttpClient, Throwable, CreationResult] =
    put(s"/topics/$topic", None).map(responseToCreationCode)

  def createSubscription(name: String, topic: String): ZIO[HttpClient, Throwable, CreationResult] =
    put("/subscriptions/" + name, Some(s"""{"topic":"${projectPrefix}/topics/$topic"}""".getBytes))
      .map(responseToCreationCode)

  def publish(topic: String, msgs: Seq[PublishMessage]) =
    for {
      authHeader <- getAuthHeader
      response <- post("/topics/" + topic + ":publish", Some(serdes(msgs)))
      publishResponse <- Task.effect(serdes.parsePublishResponse(response.getResponseBodyAsBytes()).get)
    } yield publishResponse.messageIds

  def pull(subscription: String, maxMessages: Int, returnImmediately: Boolean = false) =
    post("/subscriptions/" + subscription + ":pull", Some(serdes(new PullRequest(returnImmediately, maxMessages))))
      .map { response =>
        serdes
          .parsePullResponse(response.getResponseBodyAsBytes)
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

object ProdZioGPubSubClient {
  def apply[R, E](
      getConfig: ZIO[R, E, GPubSubConfig]
  ): ZIO[R, E, ProdZioGPubSubClient] =
    for {
      config <- getConfig
      auth <- Ref.make(Option.empty[AccessToken])
    } yield new ProdZioGPubSubClient(config, SprayJsonSupport.SprayJsonSerdes, auth)
}
