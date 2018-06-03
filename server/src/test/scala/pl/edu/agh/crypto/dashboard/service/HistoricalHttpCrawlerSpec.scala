package pl.edu.agh.crypto.dashboard.service

import io.circe.Decoder
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.HttpService
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.joda.time.DateTime
import org.scalatest.{AsyncFlatSpec, Matchers}
import pl.edu.agh.crypto.dashboard.model.CurrencyName

import scala.concurrent.duration._

class HistoricalHttpCrawlerSpec extends AsyncFlatSpec with Matchers with Http4sDsl[Task] {
  private implicit val df: (CurrencyName, CurrencyName) => Decoder[Int] = { (_, _) => Decoder.decodeInt }

  private case class Param(name: String) extends QueryParamDecoderMatcher[String](name)
  private object Fsym extends QueryParamDecoderMatcher[String]("fsym")
  private object Tsym extends QueryParamDecoderMatcher[String]("tsym")
  private object Limit extends QueryParamDecoderMatcher[String]("limit")

  private def testService = HttpService[Task] {
    case GET -> Root / "request" :? Fsym(f) +& Tsym(t) +& Limit(l) =>
      Ok(s"${DateTime.now().getSecondOfMinute}")
  }

  private val client = Client.fromHttpService(testService)

  private val config = HistoricalHttpCrawler.CrawlerConfig(
    Set(CurrencyName("BTC".ci), CurrencyName("ETH".ci)),
    Set(CurrencyName("USD".ci)),
    5.seconds,
    "/request",
    DateTime.now().minusDays(100)
  )

  private val crawler = new HistoricalHttpCrawler[Task, Int](client, config)

  "HistoricalHttpCrawler" should "query service in 5 second long intervals" in {
    crawler.stream.runAsyncGetLast map { _ => true shouldBe true }
  }

}
