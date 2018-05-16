package pl.edu.agh.crypto.dashboard.service

import cats.effect.IO
import monix.eval.Task
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

abstract class CrawlerUtils[F[_], S[_], T](run: S[T] => (T => F[Unit]) => S[Unit]) {

  def crawl(crawler: Crawler[S, T], sink: DataSink[F, T]): S[Unit] =  {
    run(crawler.stream)(sink.saveData)
  }

}

object CrawlerUtils {

  class MonixInstance[T](
    resumeWith: Throwable => Task[Unit]
  ) extends CrawlerUtils[Task, Observable, T](
    obs => fun => obs.mapTask(fun.andThen(_.attempt)).mapTask(_.fold(resumeWith, _ => Task.unit))
  )

  class IOInstance[T](
    resumeWith: Throwable => IO[Unit]
  ) extends CrawlerUtils[IO, fs2.Stream[IO, ?], T]({ stream => fun =>
    stream.evalMap(fun).attempt.evalMap(_.fold(resumeWith, _ => IO.unit))
  })

}
