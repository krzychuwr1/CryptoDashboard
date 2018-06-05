package pl.edu.agh.crypto.dashboard.service

import cats.Functor
import cats.syntax.functor._
import monix.eval.Task
import monix.reactive.Observable
import pl.edu.agh.crypto.dashboard.model.CurrencyName

import scala.reflect.ClassTag
import scala.util.Failure

abstract class CrawlerConnector[F[_], S[_] : Functor, T] {
  protected def group(stream: S[T]): S[(CurrencyName, S[T])]

  protected def build(stream: S[T], currencyName: CurrencyName, sink: F[DataSink[F, T]]): S[Unit]

  protected def invoke(streams: S[S[Unit]]): F[Unit]

  def crawl(crawler: Crawler[S, T], service: DataService[F, T]): F[Unit] = {
    val grouped = group(crawler.stream)
    val defined = grouped.map({ case (c, s) => build(s, c, service.getDataSink(c)) })
    invoke(defined)
  }

}

object CrawlerConnector {

  class MonixInstance[T](
    resumeWith: Throwable => Task[Unit],
    getCurrency: T => CurrencyName
  )(implicit
    ct: ClassTag[T],
  ) extends CrawlerConnector[Task, Observable, T] {

    private val logger = org.log4s.getLogger

    override protected def group(stream: Observable[T]): Observable[(CurrencyName, Observable[T])] =
      stream.groupBy(getCurrency).map(o => o.key -> o)

    override protected def build(stream: Observable[T], currencyName: CurrencyName, sink: Task[DataSink[Task, T]]): Observable[Unit] = {
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

    override protected def invoke(streams: Observable[Observable[Unit]]): Task[Unit] = {
      Task deferFutureAction  { implicit scheduler =>
        streams foreach { obs =>
          obs.runAsyncGetLast andThen {
            case Failure(error) =>
              logger.error(error)("Crawler stream failed")
          }
        }
      }
    }
  }

}


