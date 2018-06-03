package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import cats.syntax.applicativeError._
import monix.reactive.Observable
import pl.edu.agh.crypto.dashboard.model.CurrencyName

abstract class HttpCrawler[F[_]: Effect, T](
  config: CrawlerConfig
) extends Crawler[Observable, T] {
  private val log = org.log4s.getLogger
  import config._

  protected def forEveryCurrency[R](code: (CurrencyName, CurrencyName) => F[R]): Observable[R] = {
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
}
