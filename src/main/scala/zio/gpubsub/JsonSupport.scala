package zio.gpubsub

import scala.util.Try
import java.time.Instant

/** Implementation of this trait is required for ... */
trait JsonSerdes {

  def apply(msgs: Seq[PublishMessage]): Array[Byte]

  def apply(pr: PullRequest): Array[Byte]

  def parsePublishResponse(bytes: Array[Byte]): Try[PublishResponse]

  def parsePullResponse(bytes: Array[Byte]): Try[PullResponse]

}

object SprayJsonSupport {

  import spray.json._
  import DefaultJsonProtocol._

  private implicit val instantFormat = new JsonFormat[Instant] {
    override def read(jsValue: JsValue): Instant = jsValue match {
      case JsString(time) => Instant.parse(time)
      case _              => deserializationError("Instant required as a string of RFC3339 UTC Zulu format.")
    }
    override def write(instant: Instant): JsValue = JsString(instant.toString)
  }

  private implicit val publishMessageWriter: RootJsonWriter[PublishMessage] = m =>
    JsObject(m.data.map(data => "data" -> JsString(data)).toSeq ++ m.attributes.map(a => "attributes" -> a.toJson): _*)

  private implicit def seqWriter[T](implicit tw: JsonWriter[T]): JsonWriter[Seq[T]] =
    ts => JsArray(ts.map(_.toJson): _*)

  private implicit def seqReader[T](implicit tr: JsonReader[T]): JsonReader[Seq[T]] =
    js => js.convertTo[Seq[JsValue]].map(_.convertTo[T])

  private implicit val publishRequestWriter: RootJsonWriter[PublishRequest] =
    pr => JsObject("messages" -> pr.messages.toJson)

  private implicit val pullRequestWriter: RootJsonWriter[PullRequest] =
    pr => JsObject("returnImmediately" -> JsBoolean(pr.returnImmediately), "maxMessages" -> JsNumber(pr.maxMessages))

  private implicit val publishResponseReader: RootJsonReader[PublishResponse] =
    js => new PublishResponse(js.asJsObject.fields("messageIds").convertTo[Seq[String]])

  private implicit val pullResponse: JsonReader[PullMessage] = { js =>
    val fields = js.asJsObject.fields
    val messageId = fields("messageId").convertTo[String]
    val publishTime = fields("publishTime").convertTo[Instant]
    val data: Option[String] = fields.get("data").map(_.convertTo[String])
    val attributes = fields.get("attributes").map(_.convertTo[Map[String, String]])
    new PullMessage(messageId, publishTime, data, attributes)
  }

  private implicit val receivedMessageReader: JsonReader[ReceivedMessage] = { js =>
    val fields = js.asJsObject.fields
    new ReceivedMessage(new AckId(fields("ackId").convertTo[String]), fields("message").convertTo[PullMessage])
  }

  private implicit val pullResponseReader: RootJsonReader[PullResponse] = 
    js => new PullResponse(js.asJsObject.fields.get("receivedMessages").map(_.convertTo[Seq[ReceivedMessage]]))
  implicit object SprayJsonSerdes extends JsonSerdes {
    override def apply(msgs: Seq[PublishMessage]): Array[Byte] =
      new PublishRequest(msgs).toJson.compactPrint.getBytes

    override def apply(pr: PullRequest): Array[Byte] =
      pr.toJson.compactPrint.getBytes

    override def parsePublishResponse(bytes: Array[Byte]) =
      Try(JsonParser(bytes).fromJson[PublishResponse])

    override def parsePullResponse(bytes: Array[Byte]): Try[PullResponse] =
      Try(JsonParser(bytes).fromJson[PullResponse])


  }
}
