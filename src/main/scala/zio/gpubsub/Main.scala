package zio.gpubsub

import zio.App
import zio.ZIO
import org.asynchttpclient.{Dsl => AHC}
import org.asynchttpclient.AsyncHttpClient
import zio.console._
import zio.Task
object Main extends App {

  val host = "http://localhost:8085"

  val ahc: ZIO[Any, Throwable, AsyncHttpClient] = ZIO.effect(AHC.asyncHttpClient())
  val config = ZIO.effect(new EmulatorConfig(host, "zio"))
  val client: Task[ProdZioGPubSubClient] = ProdZioGPubSubClient(ahc, config)

  def run(args: List[String]): ZIO[zio.console.Console, Nothing, Int] =
    (for {
      c <- client
      _ <- c.createTopic("zio1")
      _ <- c.createSubscription("zios1", "zio1")
      _ <- c.publish("zio1", Seq(PublishMessage("ZHVwYQo=")))
      received <- c.pull("zios1", 100, true)
      _ <- putStrLn(received.toString)
    } yield ()).fold(_ => 1, _ => 0)

}
