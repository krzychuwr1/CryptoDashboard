package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import cats.syntax.applicativeError._
import io.circe.Decoder
import monix.eval.Task
import monix.reactive.Observable
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.joda.time.{DateTime, Days}
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, DailyTradingInfo}
import pl.edu.agh.crypto.dashboard.service.HistoricalHttpCrawler._
import pl.edu.agh.crypto.dashboard.util.LowPriorityConversion

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class HistoricalHttpCrawler[F[_] : Effect, T](
  client: Client[F],
  config: CrawlerConfig
)(implicit
  decoderFactory: (CurrencyName, CurrencyName) => Decoder[T]
) extends Crawler[Observable, T] with LowPriorityConversion[F] {
  private val log = org.log4s.getLogger

  import config._

  private def forEveryCurrency[R](code: (CurrencyName, CurrencyName) => F[R]): Observable[R] = {
    val obs = for {
      f <- Observable.fromIterable(currencyFrom)
      t <- Observable.fromIterable(currencyTo)
    } yield f -> t
    obs.mapEval(code.tupled(_).attempt).flatMap({
      case Right(t) =>
        Observable.now(t)
      case Left(t) =>
        log.warn(t)("Api request failed with")
        Observable.empty[R]
    }).delayOnNext(interval)
  }

  private val catchupHistory: Observable[T] = forEveryCurrency {
    case (l@CurrencyName(f), r@CurrencyName(t)) =>
      implicit val decoder: Decoder[T] = decoderFactory(l, r)
      val days = Days.daysBetween(startFrom, DateTime.now()).getDays
      client.expect[HistoricalResponse[T]](s"$path?fsym=$f&tsym=$t&limit=$days")
  } flatMap { r => Observable.fromIterable(r.data) }

  private val realTimeData: Observable[T] = Observable.intervalAtFixedRate(1.day, 1.day) flatMap { _ =>
    forEveryCurrency {
      case (l@CurrencyName(f), r@CurrencyName(t)) =>
        implicit val decoder: Decoder[T] = decoderFactory(l, r)
        client.expect[HistoricalResponse[T]](s"$path?fsym=$f&tsym=$t&limit=0")
    }
  } flatMap {
    case HistoricalResponse(elems) if elems.nonEmpty =>
      Observable.now(elems.last)
    case r =>
      log.warn(s"Wrong shape of the input: $r")
      Observable.empty
  }

  override def stream: Observable[T] = catchupHistory ++ realTimeData
}


object HistoricalHttpCrawler {

  case class HistoricalResponse[T](
    data: List[T]
  )

  object HistoricalResponse {
    implicit def decoder[T: Decoder]: Decoder[HistoricalResponse[T]] = Decoder.forProduct1("Data")(HistoricalResponse.apply)
  }

  case class CrawlerConfig(
    currencyFrom: Set[CurrencyName],
    currencyTo: Set[CurrencyName],
    interval: FiniteDuration,
    path: String,
    startFrom: DateTime
  )

  def main(args: Array[String]): Unit = {
    import monix.execution.Scheduler.Implicits.global
    import org.http4s.implicits._
    val log = org.log4s.getLogger

    val config = CrawlerConfig(
      Set(CurrencyName("BTC".ci), CurrencyName("ETH".ci)),
      Set(CurrencyName("USD".ci)),
      2.second,
      "https://min-api.cryptocompare.com/data/histoday",
      DateTime.now() minusDays 100
    )

    val res = for {
      client <- Http1Client[Task]()
      crawler = new HistoricalHttpCrawler[Task, DailyTradingInfo](client, config)
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