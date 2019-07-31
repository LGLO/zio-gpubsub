package zio.gpubsub

import org.specs2.Specification
import org.specs2.specification.BeforeAll
import scala.io.Source
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

class GPubSubConfigSpec extends Specification with BeforeAll with TestRuntime {

  def is = "GPubSubConfig".title ^ s2"""
    reads emulator config from env variables $emulatorConfig
    reads Google Cloud credentials JSON file $credentialsFile
    """

  override def beforeAll(): Unit = {
    val lines = readResource("/credentials.json")
    val file = new File(credentialsFilePath)
    val fos = new PrintWriter(new FileOutputStream(file))
    lines.foreach(fos.println)
    fos.close()
  }

  def emulatorConfig = {
    val env = Map("PUBSUB_EMULATOR_HOST" -> "192.168.8.1:12340", "PUBSUB_PROJECT_ID" -> "test-project")
    val config = unsafeRun(GPubSubConfig.readFromEnv(env))
    config === GPubSubConfig(EmulatorAuthenticator, "http://192.168.8.1:12340", "test-project")
  }

  def credentialsFile = {
    val config = unsafeRun(GPubSubConfig.readFromEnv(Map("GOOGLE_APPLICATION_CREDENTIALS" -> credentialsFilePath)))
    val authenticator =
      GCloudAuthenticator("johnny.b.goode@test.com", expectedPrivateKey, "https://www.googleapis.com/oauth2/v4/token")
    config === GPubSubConfig(authenticator, "https://www.googleapis.com", "my-project")
  }

  private def readResource(path: String): List[String] =
    Source.fromInputStream(getClass.getResourceAsStream(path)).getLines().toList

  private val credentialsFilePath = "target/credentials.json"
  private val expectedPrivateKey = KeyUtil.parsePrivateRsaKey(
    "-----BEGIN PRIVATE KEY-----\nMIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDaU2RKcVoVlVHk\nIfYRUEFqJ/kDjVFYZseAVAXtWiWDKD+ABPju/YNgmG37AwaN7jDBE/a/P3b8hM0S\nUPIfm9kRN77W3YpJS8VSu9zVXqVbEXIR1cdZIT2rpyYBm2pXRspbLfY7xZXLjBuX\nux7TBQWhD0YoeBfOhRVH/54BeUwF9BDQM7BDfcWjUbmf+5jlhZB2mxrctOng8OGJ\n422oKosJlHUw/vcWYR5cIShk3Unx155xsS5dpg/I23aGjw3wkQU5+A+n8T2BwQ0L\nGnpPjwT7t8ZmoBa+Zz9YbYPuzxXicRGH3m3fNzCifKL9M7yU7tJDKlgAOKD1/+/r\n7ntP3gnTAgMBAAECggEACLfVxGc8WqpIx9xXUB+tTQjll6+39Ni91LEGP5Ee+FrO\nFlWcfyaebWgI1EItq9M8HSmn68EkBjdjXPzgfkSh6V8Zfsj/8adV7NTVn6uU3MsY\ni//GYxfBt64IBX5oQ63u/K8cKwzby3H/1BJDy9difyj1+z24baXA0MwzSgnG8GHY\nr1HOy59scApEZC7uqFPMB4tTlEWwPura2EY6ujGoWXTzd1u0OJdsEX0UmnfO0SQA\nC+8VacLRXFkaH7m1mpQMDOQIOWoig4yuGLp9gBeqEdbMeXxOMYgItUz1LHr4tCWw\nxLNEQr+ONdrTYOPSu+s9FV42+Ut9BAvbVBzIeBZa4QKBgQD+g9Nq5DD0TgGmifTq\n+bRfg1mMdHxqh/+F+/FYC9icOo4YJ6+P5g0DiGJEOr+yPezEWvxKfDk0poBFgRrz\nWlgoq8gJXNKTVTW0PUCjwoe3zuwSVVu8szDPby9XBv6m6tlAdQTye41MNi7twt/f\nOzfg7kIHMPqnQ4dmQn7T2l2Q8QKBgQDbmYJmS8/kqBlZklN8M8UGwW4NnWjeXv93\nneIjp9NMULUUtziNzK7RqkEuW5LBYUaXL1hREKaPHD74/HhgNTeHQsJB0sOAb1Z1\nuCzEycKETBySSoOshg4DZNMcn82UILzNOGjki+LtL9iXnZjdq00AVj9xCnobAIuT\ni33hFrrHAwKBgQD6DoC4I4xpav66Jg6okj8CHGXt1kCq1gVaGUdsbu8Zx3n2B3+4\nzJh6oROS33mZ4Ldvz8jSUaxOq0uZfhgBggDdrLCzaixFrtE9zXRlsGlxUO6lsJGa\nGx/Yne+P/IJTcqeSaaY7quIPP6jp1q3ngCINb2kV8axCi9lKwggjkwacsQKBgQCO\n5oMzvEvJmRX98rl/DlU9QywxkehHJHyLw2MAHtriQ3bCx0P3CmYMrAUEfLSwQHPm\nN/n5rqMkZ5YXAnv250p+K5Qrr1JnRox1yGbhnAWHf9vr2q962jVOQhMbAmaN6QvT\nY8zmRJ1kCYmhh/2M6kcOXBGmptG0tpMdbV151weuLwKBgQCW0G4D8CNgzkO9Jt96\nbG8GMFGdr2GGr8aEfa/+FTsbvHdjAKFXm9VAau7LHJ9UZkLBY1Ty/MJ1ktc6aWJY\nG2uENXUzj7A4UuXty9AZegJHIdz4SUGf4vPRD/rj1ObiiicLplXTlHbxYx85fnyC\nKt06XrWu4MR7L5/iSq+NxDM0SQ==\n-----END PRIVATE KEY-----"
  )

}
