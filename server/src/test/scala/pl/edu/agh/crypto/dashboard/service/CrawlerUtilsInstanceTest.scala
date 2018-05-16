package pl.edu.agh.crypto.dashboard.service

import cats.effect.IO
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.scalatest.{AsyncFlatSpec, Matchers}
import pl.edu.agh.crypto.dashboard.service.CrawlerUtils.{IOInstance, MonixInstance}

class CrawlerUtilsInstanceTest extends AsyncFlatSpec with Matchers {
  implicit val schedule: Scheduler = Scheduler.global

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

  class TestSink[F[_]](inst: Int => F[Unit]) extends DataSink[F, Int] {
    var collected: Set[Int] = Set.empty
    override def saveData(data: Int): F[Unit] = {
      collected += data
      inst(data)
    }
  }

  val monixInstance = new MonixInstance[Int]({
    case A(_) => Task.unit
    case t => Task.raiseError(t)
  })

  val iOInstance = new IOInstance[Int]({
    case A(_) => IO.unit
    case t => IO.raiseError(t)
  })

  "MonixInstance" should "consume all elements from crawler" in {
    val sink = new TestSink[Task](_ => Task.unit)
    val elems = 0 until 100
    val crawler = new MonixCrawler(elems.iterator)

    monixInstance.crawl(crawler, sink)
      .foreach(_ => ()) map { _ =>
      sink.collected should contain allElementsOf elems
    }
  }

  "IOInstance" should "consume all elements from crawler" in {
    val sink = new TestSink[IO](_ => IO.unit)
    val elems = 0 until 100
    val crawler = new IOCrawler(elems.iterator)

    (iOInstance.crawl(crawler, sink)
      .compile
      .drain map { _ =>
      sink.collected should contain allElementsOf elems
    }).unsafeToFuture()
  }

  "MonixInstance" should "recover from error" in {
    val failOn = 10
    val error = A("should recover")
    val sink = new TestSink[Task](x => if (x == failOn) Task.raiseError(error) else Task.unit)
    val firstElems = 0 until 100

    val crawler = new MonixCrawler(firstElems.iterator)

    monixInstance.crawl(crawler, sink)
      .foreach(_ => ()) map { _ =>
      sink.collected should contain allElementsOf firstElems.filter(_ != failOn)
    }
  }

  "IOInstance" should "recover from error" in {
    val failOn = 10
    val error = A("should recover")
    val sink = new TestSink[IO](x => if (x == failOn) IO.raiseError(error) else IO.unit)
    val firstElems = 0 until 100
    val crawler = new IOCrawler(firstElems.iterator)

    (iOInstance.crawl(crawler, sink)
      .compile
      .drain map { _ =>
      sink.collected should contain allElementsOf firstElems.filter(_ != failOn)
    }).unsafeToFuture()
  }

}
