package zio.gpubsub

import java.time.Instant

/*
 * Classes for models of Google Cloud PubSub HTTP API.
 * PubSubMessage model is change for sending: renamed to PublishMessage with removed messageId and publishTime.
 * For receiving it's renamed to PullMessage.
 */

// Acknowleding requests

final class AckId(val value: String) extends AnyVal {
  override def toString = value
}

final class AcknowledgeRequest(val ackIds: Seq[AckId]) {

  override def equals(other: Any): Boolean = other match {
    case that: AcknowledgeRequest => ackIds == that.ackIds
    case _                        => false
  }

  override def hashCode: Int = ackIds.hashCode

  override def toString: String = "AcknowledgeRequest(" + ackIds.mkString("[", ",", "]") + ")"
}

object AcknowledgeRequest {
  def of(ackIds: AckId*): AcknowledgeRequest = new AcknowledgeRequest(ackIds.toList)
}

//Models for publishing

/**
  * Message to publish.
  * `data` has to be Byte64 encoded.
  * Either `data` should be present or `attributes` be non empty.
  */
final class PublishMessage(val data: Option[String], val attributes: Map[String, String] = Map.empty) {
  override def toString: String = "PublishMessage(data=" + data + ",attributes=" + attributes.toString + ")"

  override def equals(other: Any): Boolean = other match {
    case that: PublishMessage => data == that.data && attributes == that.attributes
    case _                    => false
  }

  override def hashCode: Int = java.util.Objects.hash(data, attributes)
}

object PublishMessage {
  def apply(data: String, attributes: Map[String, String]) = new PublishMessage(Some(data), attributes)
  def apply(data: String) = new PublishMessage(Some(data))
  def apply(attributes: Map[String, String]) = new PublishMessage(None, attributes)
}

final class PublishRequest(val messages: Seq[PublishMessage]) {

  override def equals(other: Any): Boolean = other match {
    case that: PublishRequest => messages == that.messages
    case _                    => false
  }

  override def hashCode: Int = messages.hashCode

  override def toString: String = "PublishRequest(" + messages.mkString("[", ",", "]") + ")"
}

final class PublishResponse(val messageIds: Seq[String]) {

  override def equals(other: Any): Boolean = other match {
    case that: PublishResponse => messageIds == that.messageIds
    case _                     => false
  }

  override def hashCode: Int = messageIds.hashCode

  override def toString: String = "PublishResponse(" + messageIds.mkString("[", ",", "]") + ")"
}

//Models for pulling messages

final class PullRequest(val returnImmediately: Boolean, val maxMessages: Int) {
  override def toString: String =
    "PullRequest(returnImmediately=" + returnImmediately + ",maxMessage=" + maxMessages + ")"
  override def equals(other: Any): Boolean = other match {
    case that: PullRequest => returnImmediately == that.returnImmediately && maxMessages == that.maxMessages
    case _                 => false
  }

  override def hashCode: Int = java.util.Objects.hashCode(returnImmediately, maxMessages)
}

/** If there are no message, server tends to return '{}' */
final class PullResponse(val receivedMessages: Option[Seq[ReceivedMessage]]) {

  override def equals(other: Any): Boolean = other match {
    case that: PullResponse => receivedMessages == that.receivedMessages
    case _                  => false
  }

  override def hashCode: Int = receivedMessages.hashCode

  override def toString: String = "PullResponse(" + receivedMessages.map(_.mkString("[", ",", "]")) + ")"
}

final class ReceivedMessage(val ackId: AckId, val message: PullMessage) {

  override def equals(other: Any): Boolean = other match {
    case that: ReceivedMessage => ackId == that.ackId && message == that.message
    case _                     => false
  }

  override def hashCode: Int = java.util.Objects.hash(ackId.value, message)

  override def toString: String = "ReceivedMessage(ackId=" + ackId.toString + ",message=" + message.toString + ")"
}

final class PullMessage(
    val messageId: String,
    val publishTime: Instant,
    val data: Option[String],
    val attributes: Option[Map[String, String]]
) {
  override def equals(other: Any): Boolean = other match {
    case that: PullMessage =>
      messageId == that.messageId && publishTime == that.publishTime && data == that.data && attributes == that.attributes
    case _ => false
  }

  override def hashCode: Int = java.util.Objects.hash(messageId, publishTime, data, attributes)

  override def toString: String =
    "PullMessage(messageId=" + messageId + ",publishTime=" + publishTime + ",data=" + data + ",attributes=" + attributes + ")"
}
