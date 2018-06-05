package pl.edu.agh.crypto.dashboard.service

import cats.effect.IO
import cats.{Functor, ~>}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.scalatest.{AsyncFlatSpec, Matchers}
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import pl.edu.agh.crypto.dashboard.service.CrawlerConnector.MonixInstance

import scala.concurrent.Future

class CrawlerConnectorInstanceTest extends AsyncFlatSpec with Matchers {
  implicit val schedule: Scheduler = Scheduler.global

  import org.http4s.implicits._

  private val currencies = Array(
    CurrencyName("USD".ci),
    CurrencyName("EUR".ci),
    CurrencyName("BTC".ci)
  )

  private def currencyOf(numb: Int): CurrencyName = currencies(numb % currencies.length)

  case class A(message: String) extends Throwable(message)

  case class B(message: String) extends Throwable(message)

  class MonixCrawler(
    fs: => Iterator[Int]
  ) extends Crawler[Observable, Int] {
    override def stream: Observable[Int] = {
      Observable.fromIterator(fs)
    }
  }

  class IOCrawler(
    fs: => Iterator[Int]
  ) extends Crawler[fs2.Stream[IO, ?], Int] {
    override def stream: fs2.Stream[IO, Int] = {
      fs2.Stream.fromIterator[IO, Int](fs)
    }
  }

  class TestSink[F[_]: Functor](inst: Int => F[Unit]) extends DataSink[F, Int] {
    import cats.syntax.functor._
    var collected: Set[Int] = Set.empty

    override def saveData(data: Int): F[Unit] = {
      inst(data).map(_ => collected += data)
    }
  }

  class TestService[F[_]](
    lift: Option ~> F,
    val sinks: Map[CurrencyName, TestSink[F]],
  ) extends DataService[F, Int] {
    override def getDataSource(currency: CurrencyName): F[DataSource[F, Int]] = lift(Option.empty)

    override def getDataSink(currency: CurrencyName): F[DataSink[F, Int]] = lift(sinks.get(currency))
  }

  val monixInstance = new MonixInstance[Int](
    resumeWith = {
      case A(_) => Task.unit
      case t => Task.raiseError(t)
    },
    getCurrency = currencyOf
  )

  private def fromOption: Option ~> Task = new (Option ~> Task) {
    override def apply[T](fa: Option[T]): Task[T] = fa match {
      case Some(v) => Task now v
      case None => Task raiseError new NoSuchElementException()
    }
  }

  "MonixInstance" should "consume all elements from crawler" in {
    val service = new TestService(
      fromOption,
      currencies.map(_ -> new TestSink[Task](_ => Task.unit)).toMap
    )

    val elems = 0 until 100
    val crawler = new MonixCrawler(elems.iterator)

    val crawlingAction = monixInstance.crawl(crawler, service).foreach(_ => ())
    Future.traverse(currencies.toList) { c =>
      crawlingAction map { _ =>
        val es = elems.filter(currencyOf(_) == c)
        val collected = service.sinks(c).collected
        collected.forall(es.contains) && es.forall(collected.contains)
      }
    } map { assertions =>
      assertions should contain only true
    }
  }

  it should "recover from error" in {
    val failOn = 10
    val error = A("should recover")
    val service = new TestService(
      fromOption,
      currencies.map(_ -> new TestSink[Task](x => if (x == failOn) Task.raiseError(error) else Task.unit)).toMap
    )
    val firstElems = 0 until 100

    val crawler = new MonixCrawler(firstElems.iterator)

    val crawlingAction = monixInstance.crawl(crawler, service).foreach(_ => ())
    Future.traverse(currencies.toList) { c =>
      crawlingAction map { _ =>
        val elems = firstElems.filter(e => currencyOf(e) == c && e != failOn)
        val collected = service.sinks(c).collected
        collected.forall(elems.contains) && elems.forall(collected.contains)
      }
    } map { assertions =>
      assertions should contain only true
    }
  }

}
