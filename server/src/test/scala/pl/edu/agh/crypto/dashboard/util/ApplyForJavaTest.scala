package pl.edu.agh.crypto.dashboard.util

import java.util.concurrent.CompletableFuture

import cats.effect.IO
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{AsyncFlatSpec, Matchers}

import scala.concurrent.ExecutionContext

class ApplyForJavaTest extends AsyncFlatSpec with Matchers {

  private implicit val scheduler = Scheduler(implicitly[ExecutionContext])

  "Monix task instance" should "correctly forward value" in {
    lazy val future = CompletableFuture.completedFuture("test")
    val task = ApplyFromJava[Task].deferFuture(future)
    task.runAsync map { _ should equal("test") }
  }

  it should "correctly forward failure" in {
    lazy val future = {
      val f = new CompletableFuture[String]()
      f.completeExceptionally(new Exception("test"))
      f
    }
    val task = ApplyFromJava[Task].deferFuture(future)
    recoverToExceptionIf[Exception](
      task.runAsync
    ) map { _.getMessage should equal("test") }
  }

  it should "report NoSuchElementException in case of null" in {
    lazy val future = CompletableFuture.completedFuture[String](null)
    val task = ApplyFromJava[Task].deferFuture(future)
    recoverToSucceededIf[NoSuchElementException](task.runAsync)
  }

  "IO task instance" should "correctly forward value" in {
    lazy val future = CompletableFuture.completedFuture("test")
    val io = ApplyFromJava[IO].deferFuture(future)
    io.unsafeToFuture() map { _ should equal("test") }
  }

  it should "correctly forward failure" in {
    lazy val future = {
      val f = new CompletableFuture[String]()
      f.completeExceptionally(new Exception("test"))
      f
    }
    val io = ApplyFromJava[IO].deferFuture(future)
    recoverToExceptionIf[Exception](
      io.unsafeToFuture()
    ) map { _.getMessage should equal("test") }
  }

  it should "report NoSuchElementException in case of null" in {
    lazy val future = CompletableFuture.completedFuture[String](null)
    val io = ApplyFromJava[IO].deferFuture(future)
    recoverToSucceededIf[NoSuchElementException](io.unsafeToFuture())
  }

}
