package zio.gpubsub

import java.util.concurrent.TimeUnit

import org.asynchttpclient.{AsyncHttpClient, ListenableFuture, Response}
import zio.{Ref, Task, ZIO}
import zio.clock.Clock
import zio.gpubsub.ZioGPubSubClient.{AlreadyExists, Created, CreationResult}
import zio.interop.javaconcurrent._

trait ZioGPubSubClient {
  def client: ZioGPubSubClient.Service
}

object ZioGPubSubClient {

  sealed trait CreationResult
  case object Created extends CreationResult
  case object AlreadyExists extends CreationResult
  final class NotCreated(val reason: String) extends CreationResult
  trait Service {
    def createTopic(topic: String): Task[CreationResult]
    def createSubscription(name: String, topic: String): Task[CreationResult]
    def publish(topic: String, msgs: Seq[PublishMessage]): ZIO[Clock, Throwable, Seq[String]]
    def pull(subscription: String, maxMessages: Int, returnImmediately: Boolean = false): Task[Seq[ReceivedMessage]]
  }
}

class ProdZioGPubSubClient(
    client: AsyncHttpClient,
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

  private def toTask[R](lf: ListenableFuture[R]): Task[R] = Task.fromCompletionStage(() => lf.toCompletableFuture())

  private def post(path: String, jsonBody: Option[Array[Byte]]): ZIO[Any, Throwable, Response] =
    ZIO
      .effect {
        val builder0 = client.preparePost(urlPrefix + path)
        val builder1 = jsonBody.fold(builder0) { body =>
          builder0.addHeader("content-type", Seq("application/json")).setBody(body)
        }
        builder1.execute()
      }
      .flatMap(toTask)

  private def put(path: String, jsonBody: Option[Array[Byte]]): ZIO[Any, Throwable, Response] =
    ZIO
      .effect {
        val builder0 = client.preparePut(urlPrefix + path)
        val builder1 = jsonBody.fold(builder0) { body =>
          builder0.addHeader("content-type", Seq("application/json")).setBody(body)
        }
        builder1.execute()
      }
      .flatMap(toTask)

  private def responseToCreationCode(result: Response) =
    if (result.getStatusCode() == 200) Created
    else if (result.getStatusCode() == 409) AlreadyExists
    else new ZioGPubSubClient.NotCreated(s"status code = ${result.getStatusCode}, body = ${result.getResponseBody()}")

  def createTopic(topic: String): Task[CreationResult] = put(s"/topics/$topic", None).map(responseToCreationCode)

  def createSubscription(name: String, topic: String): Task[CreationResult] =
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

  private def getAuthHeader: ZIO[Clock, Throwable, Seq[(String, String)]] =
    config.authenticator(client) match {
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
      getClient: ZIO[R, E, AsyncHttpClient],
      getConfig: ZIO[R, E, GPubSubConfig]
  ): ZIO[R, E, ProdZioGPubSubClient] =
    for {
      client <- getClient
      config <- getConfig
      auth <- Ref.make(Option.empty[AccessToken])
    } yield new ProdZioGPubSubClient(client, config, SprayJsonSupport.SprayJsonSerdes, auth)
}
