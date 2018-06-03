package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import io.circe.{Decoder, Encoder}
import monix.eval.Task
import monix.reactive.Observable
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, RawTradingInfo, TradingInfo}
import pl.edu.agh.crypto.dashboard.service.RealTimeCrawler.MultiplexData
import pl.edu.agh.crypto.dashboard.util.LowPriorityConversion

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class RealTimeCrawler[F[_]: Effect, Raw: Decoder, T](
  client: Client[F],
  config: CrawlerConfig,
  convert: Raw => T
) extends HttpCrawler[F, T](config) with LowPriorityConversion[F] {
  import config._

  override def stream: Observable[T] = Observable.intervalAtFixedRate(interval, interval) mapEval { _ =>
    val fsyms = currencyFrom.map(_.name).mkString(",")
    val tsyms = currencyTo.map(_.name).mkString(",")
    client.expect[MultiplexData[Raw]](s"$path?fsyms=$fsyms&tsyms=$tsyms")
  } flatMap { entries =>
    lazy val iter = for {
      map <- entries.data.valuesIterator
      raw <- map.valuesIterator
    } yield convert(raw)
    Observable fromIterator iter
  }
}

object RealTimeCrawler {

  case class MultiplexData[T](
    data: Map[CurrencyName, Map[CurrencyName, T]]
  )

  object MultiplexData extends CurrencyName.KeyCodecs {
    implicit def decoder[T: Decoder]: Decoder[MultiplexData[T]] = Decoder.forProduct1("RAW")(MultiplexData.apply[T])
  }

  def main(args: Array[String]): Unit = {
    import monix.execution.Scheduler.Implicits.global
    import org.http4s.implicits._
    val log = org.log4s.getLogger

    val config = CrawlerConfig(
      Set(CurrencyName("BTC".ci), CurrencyName("ETH".ci)),
      Set(CurrencyName("USD".ci)),
      5.second,
      "https://min-api.cryptocompare.com/data/pricemultifull",
      DateTime.now() minusDays 100
    )

    val res = for {
      client <- Http1Client[Task]()
      crawler = new RealTimeCrawler[Task, RawTradingInfo, TradingInfo](client, config, _.toTradingInfo(DateTime.now()))
      res <- crawler.stream.lastOptionL
    } yield res

    Await.ready(res.runAsync andThen {
      case Success(f) =>
        log.info(s"Finished with: $f")
      case Failure(t) =>
        log.error(t)("Stream failed")
    }, Duration.Inf)
  }

}
