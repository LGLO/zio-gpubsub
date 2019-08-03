package zio.gpubsub.httpclient

import org.asynchttpclient._
import zio.{Task, ZIO}
import zio.interop.javaconcurrent._
import org.asynchttpclient.ListenableFuture

trait HttpClient {
  val client: HttpClient.Service[Any]
}

object HttpClient {

  trait Service[R] {
    def execute(req: Request): ZIO[R, Throwable, Response]
  }

  class Live(client: AsyncHttpClient) extends Service[Any] {

    def execute(req: Request): ZIO[Any, Throwable, Response] = toTask(client.executeRequest(req))

    private def toTask[A](lf: ListenableFuture[A]): Task[A] = Task.fromCompletionStage(() => lf.toCompletableFuture())
  }
}
