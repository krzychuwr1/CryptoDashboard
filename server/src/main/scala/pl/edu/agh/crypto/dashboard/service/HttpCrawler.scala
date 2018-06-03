package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import cats.syntax.applicativeError._
import io.circe.Decoder
import monix.eval.Task
import monix.reactive.Observable
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import pl.edu.agh.crypto.dashboard.util.LowPriorityConversion

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class HttpCrawler[F[_]: Effect, T: Decoder](
  client: Client[F],
  interval: FiniteDuration
) extends Crawler[Observable, T] with LowPriorityConversion[F] {
  private val log = org.log4s.getLogger

  override def stream: Observable[T] = Observable intervalAtFixedRate interval mapEval { _ =>
    client.expect[T]("http://my-super-uri.com/my/super/path?my=&great=&query=").attempt
  } flatMap {
    case Right(t) =>
      Observable.now(t)
    case Left(t) =>
      log.warn(t)("Api request failed with")
      Observable.empty[T]
  }
}


object HttpCrawler {

  def main(args: Array[String]): Unit = {
    import monix.execution.Scheduler.Implicits.global
    val log = org.log4s.getLogger

    val res = for {
      client <- Http1Client[Task]()
      crawler = new HttpCrawler[Task, String](client, 10.seconds)
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