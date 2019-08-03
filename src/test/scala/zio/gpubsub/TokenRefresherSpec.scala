package zio.gpubsub
import java.time.Instant

import org.specs2.Specification
import zio.duration._
import zio.test.mock.MockClock
import zio.{Ref, ZIO}

class TokenRefresherSpec extends Specification with TestRuntime {

  def is = "TokenRefresher".title ^ s2"""
    obtains new token first time it's called $getFirstTime
    returns token when it's fresh $getFreshToken
    obtains new token when current is valid but not within margin $getTokenInMargin
    obtains new token when current is not valid $getTokenInvalid
  """

  def getFirstTime =
    unsafeRun(
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        tokens <- TestGetToken.make(tokenValidUntil5)
        ref <- Ref.make(Option.empty[AccessToken])
        refresher = new TokenRefresher(tokens.getToken, ref, margin10Seconds)
        token <- refresher.getFreshToken.provide(clock)
        allUsed <- tokens.allTokensUsed
      } yield (token, allUsed)
    ) === (tokenValidUntil5, true)

  def getFreshToken =
    unsafeRun(
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        ref <- Ref.make(Option(tokenValidUntil10))
        refresher = new TokenRefresher(ZIO.fail(new Exception("Unexpected invocation")), ref, margin5Seconds)
        token <- refresher.getFreshToken.provide(clock)
      } yield token
    ) === tokenValidUntil10

  def getTokenInMargin =
    unsafeRun(
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        testGetToken <- TestGetToken.make(tokenValidUntil10)
        ref <- Ref.make(Option(tokenValidUntil5))
        refresher = new TokenRefresher(testGetToken.getToken, ref, margin10Seconds)
        token <- refresher.getFreshToken.provide(clock)
        allUsed <- testGetToken.allTokensUsed
      } yield (token, allUsed)
    ) === (tokenValidUntil10, true)

  def getTokenInvalid =
    unsafeRun(
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        _ <- clock.clock.adjust(10.seconds)
        testGetToken <- TestGetToken.make(tokenValidUntil20)
        ref <- Ref.make(Option(tokenValidUntil5))
        refresher = new TokenRefresher(testGetToken.getToken, ref, margin5Seconds)
        token <- refresher.getFreshToken.provide(clock)
        allTokensUsed <- testGetToken.allTokensUsed
      } yield (token, allTokensUsed)
    ) === (tokenValidUntil20, true)

  private val tokenValidUntil5 = new AccessToken("token-5", Instant.ofEpochSecond(5))
  private val tokenValidUntil10 = new AccessToken("token-10", Instant.ofEpochSecond(10))
  private val tokenValidUntil20 = new AccessToken("token-20", Instant.ofEpochSecond(20))

  private val margin5Seconds = 5.seconds
  private val margin10Seconds = 10.seconds

  case class TestGetToken(tokens: Ref[List[AccessToken]]) {
    val getToken = tokens.modify(tokens => (tokens.head, tokens.tail))
    val allTokensUsed = tokens.get.map(_.isEmpty)
  }

  object TestGetToken {
    def make(tokens: AccessToken*) = Ref.make(tokens.toList).map(TestGetToken(_))
  }
}
