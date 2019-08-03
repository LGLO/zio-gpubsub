package zio.gpubsub

import org.asynchttpclient.{Dsl => AHC, Request, Response}
import zio.{UIO, ZIO}

package object httpclient extends HttpClient.Service[HttpClient] {

  def execute(req: Request): ZIO[HttpClient, Throwable, Response] = ZIO.accessM[HttpClient](_.client.execute(req))

  def make: ZIO[Any, Nothing, HttpClient] = UIO {
    new HttpClient {
      val client = new HttpClient.Live(AHC.asyncHttpClient())
    }
  }
}
