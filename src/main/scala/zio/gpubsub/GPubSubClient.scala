package zio.gpubsub

import zio.ZIO
import zio.interop.javaconcurrent._
import org.asynchttpclient.AsyncHttpClient
import zio.Task
import org.asynchttpclient.ListenableFuture
import org.asynchttpclient.Response
import zio.gpubsub.ZioGPubSubClient.Created
import zio.gpubsub.ZioGPubSubClient.AlreadyExists
import zio.gpubsub.ZioGPubSubClient.CreationResult

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
    def publish(topic: String, msgs: Seq[PublishMessage]): Task[Seq[String]]
    def pull(subscription: String, maxMessages: Int, returnImmediately: Boolean = false): Task[Seq[ReceivedMessage]]
  }
}
 
class ProdZioGPubSubClient(
    client: AsyncHttpClient,
    config: GPubSubConfig,
    serdes: JsonSerdes
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
    post("/topics/" + topic + ":publish", Some(serdes(msgs))).map { response =>
      serdes.parsePublishResponse(response.getResponseBodyAsBytes()).get.messageIds
    }

  def pull(subscription: String, maxMessages: Int, returnImmediately: Boolean = false) =
    post("/subscriptions/" + subscription + ":pull", Some(serdes(new PullRequest(returnImmediately, maxMessages))))
      .map { response =>
        serdes
          .parsePullResponse(response.getResponseBodyAsBytes)
          .get
          .receivedMessages
          .getOrElse(Seq.empty[ReceivedMessage])
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
    } yield new ProdZioGPubSubClient(client, config, SprayJsonSupport.SprayJsonSerdes)
}
