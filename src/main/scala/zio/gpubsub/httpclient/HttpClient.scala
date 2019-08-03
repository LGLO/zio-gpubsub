package zio.gpubsub.httpclient

import java.net.http.{HttpClient => JHttpClient, HttpRequest, HttpResponse}

import zio.{Task, ZIO}
import zio.interop.javaconcurrent._
import java.net.http.HttpResponse.BodyHandler

trait HttpClient {
  val client: HttpClient.Service[Any]
}

object HttpClient {

  trait Service[R] {
    def execute[A](req: HttpRequest, bodyHandler: BodyHandler[A]): ZIO[R, Throwable, HttpResponse[A]]
  }

  class Live(client: JHttpClient) extends Service[Any] {

    def execute[A](req: HttpRequest, bodyHandler: BodyHandler[A]): ZIO[Any, Throwable, HttpResponse[A]] =
      Task.fromCompletionStage(() => client.sendAsync(req, bodyHandler))
  }

}
