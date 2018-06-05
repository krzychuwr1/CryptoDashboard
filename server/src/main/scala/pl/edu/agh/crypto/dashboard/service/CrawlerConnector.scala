package pl.edu.agh.crypto.dashboard.service

import cats.Functor
import monix.eval.Task
import monix.reactive.Observable
import pl.edu.agh.crypto.dashboard.model.CurrencyName

import scala.reflect.ClassTag

abstract class CrawlerConnector[F[_], S[_] : Functor, T] {
  def crawl(crawler: Crawler[S, T], service: DataService[F, T]): F[Unit]
}

object CrawlerConnector {

  class MonixInstance[T](
    resumeWith: Throwable => Task[Unit],
    getCurrency: T => CurrencyName
  )(implicit
    ct: ClassTag[T],
  ) extends CrawlerConnector[Task, Observable, T] {
    private val logger = org.log4s.getLogger

    private def build(stream: Observable[T], currencyName: CurrencyName, sink: Task[DataSink[Task, T]]): Observable[Unit] = {
      logger.info(s"Defining crawler operations for ${currencyName.name}, type: ${ct.runtimeClass.getSimpleName}")
      stream mapTask { data =>
        sink flatMap { s =>
          s saveData data
        } onErrorRecover {
          case error =>
            logger.warn(error)(s"Failed to persist data ${ct.runtimeClass.getSimpleName} for currency: $currencyName, crawling continues")
        }
      }
    }

    def crawl(crawler: Crawler[Observable, T], service: DataService[Task, T]): Task[Unit] = {
      crawler.stream
        .groupBy(getCurrency)
        .switchMap(s => build(s, s.key, service.getDataSink(s.key)))
        .lastOptionL
        .map(_ => ())
    }
  }

}


