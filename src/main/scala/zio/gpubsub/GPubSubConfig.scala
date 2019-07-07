package zio.gpubsub

/**
  * Configuration required for the client.
  * It can be either "real" config or one for emulator.
  */
sealed trait GPubSubConfig {
  def getAuth: Option[Unit]
  def getHost: String
  def projectId: String
}

final class EmulatorConfig(host: String, val projectId: String) extends GPubSubConfig {
  def getAuth = None
  def getHost = host
}
