package zio.gpubsub

import zio.ZIO
import zio.Queue
import zio.duration.Duration
import zio.clock.Clock

class GPubSubSource(client: ZioGPubSubClient.Service) {
  def pull(
      subscription: String,
      queueSize: Int,
      pullMaxMessages: Int,
      backoff: Duration
  ): ZIO[Clock, Throwable, Queue[ReceivedMessage]] =
    for {
      q <- Queue.bounded[ReceivedMessage](queueSize)
      _ <- (client
        .pull(subscription, pullMaxMessages, returnImmediately = true)
        .flatMap { msgs =>
          if (msgs.nonEmpty) q.offerAll(msgs).map(_ => ())
          else ZIO.unit.delay(backoff)
        }
        .forever)
        .fork
    } yield q

}
