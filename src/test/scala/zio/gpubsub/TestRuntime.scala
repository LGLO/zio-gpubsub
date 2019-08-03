package zio.gpubsub

import zio.Runtime
import zio.blocking.Blocking
import zio.console.Console
import zio.internal.{Platform, PlatformLive}
import zio.random.Random
import zio.system.System

trait TestRuntime extends Runtime[Console with System with Random with Blocking] {
  type Environment = Console with System with Random with Blocking

  val Platform: Platform = PlatformLive.Default
  val Environment: Environment = new Console.Live with System.Live with Random.Live with Blocking.Live
}
