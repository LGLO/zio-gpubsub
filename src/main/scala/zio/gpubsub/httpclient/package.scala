package zio.gpubsub

import java.net.http.{HttpClient => JHttpClient, HttpRequest, HttpResponse}
import java.net.http.HttpResponse.BodyHandler

import zio.{UIO, ZIO}

package object httpclient extends HttpClient.Service[HttpClient] {

  def execute[A](req: HttpRequest, bodyHandler: BodyHandler[A]): ZIO[HttpClient, Throwable, HttpResponse[A]] =
    ZIO.accessM[HttpClient](_.client.execute(req, bodyHandler))

  def make: ZIO[Any, Nothing, HttpClient] = UIO {
    new HttpClient {
      val client = new HttpClient.Live(JHttpClient.newBuilder().build())
    }
  }
}
