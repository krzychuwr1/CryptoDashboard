package pl.edu.agh.crypto.dashboard.service

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.http4s.dsl.Http4sDsl
import org.joda.time.DateTime
import org.scalatest.{AsyncFlatSpec, Matchers}
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import pl.edu.agh.crypto.dashboard.util.LowPriorityConversion

import scala.concurrent.duration._

class HttpCrawlerSpec extends AsyncFlatSpec with Matchers with Http4sDsl[Task] with LowPriorityConversion[Task] {
  private val interval = 1.seconds

  private val config = CrawlerConfig(
    Set(CurrencyName("BTC".ci), CurrencyName("ETH".ci)),
    Set(CurrencyName("USD".ci)),
    interval,
    "/request",
    DateTime.now().minusDays(1)
  )

  private val crawler = new HttpCrawler[Task, Int](config) {
    override def stream: Observable[Int] = forEveryCurrency { (_, _) =>
      Task eval DateTime.now().getSecondOfDay
    }
  }

  "HttpCrawler" should s"query service in $interval long intervals" in {
    val resultTask = crawler.stream.bufferTumbling(2).map({
      case Seq(f, s) if (s - f).seconds >= interval =>
        true
      case elems =>
        println(s"Wrong subsequent times: $elems")
        false
    }).foldLeftL(true)(_ && _)

    resultTask.map(_ shouldBe true).runAsync
  }

}
