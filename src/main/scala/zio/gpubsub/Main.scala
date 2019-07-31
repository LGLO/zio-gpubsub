package zio.gpubsub

object Main { //extends App {

  /*val host = "http://localhost:8085"

  val ahc: ZIO[Any, Throwable, AsyncHttpClient] = ZIO.effect(AHC.asyncHttpClient())
  val config = ZIO.effect(new EmulatorConfig(host, "zio"))
  val client: Task[ProdZioGPubSubClient] = ProdZioGPubSubClient(ahc, config)

  val emu1 = (for {
    c <- client
    _ <- c.createTopic("zio1")
    _ <- c.createSubscription("zios1", "zio1")
    q <- new GPubSubSource(c).pull("zios1", 100, 3, Duration(1, TimeUnit.SECONDS))
    _ <- (q.take.flatMap(rn => putStrLn(rn.toString)).forever).fork
    writer <- (c.publish("zio1", Seq(PublishMessage("ZHVwYQo="))).repeat(Schedule.recurs(99))).fork
    _ <- writer.join
    _ <- q.shutdown
  } yield ()).fold(_ => 1, _ => 0)

  val readConfig = (for {
    _ <- GPubSubConfig.readFromEnv(Map("GOOGLE_APPLICATION_CREDENTIALS" -> "/home/lglo/tmp/creds.json"))
  } yield ()).fold({
    th => th.printStackTrace()
    1},_ => 0)

  def run(args: List[String]): ZIO[Console with Clock, Nothing, Int] = readConfig
    
*/
}
