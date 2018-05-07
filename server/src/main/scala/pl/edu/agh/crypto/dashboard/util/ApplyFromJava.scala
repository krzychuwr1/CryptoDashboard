package pl.edu.agh.crypto.dashboard.util

import java.util.concurrent.CompletableFuture

import cats.effect.IO
import monix.eval.Task
import monix.execution.Cancelable

import scala.reflect.ClassTag

/**
  * Transform java completable future to the given effect,
  * Null value is reported as 'new NoSuchElementException', courtesy of terrible java standard library,
  * To enable correct null-checking class-tag is present in signature
  * @tparam F target effect
  */
trait ApplyFromJava[F[_]] {
  def deferFuture[T: ClassTag](arg: => CompletableFuture[T]): F[T]
}

object ApplyFromJava {

  def apply[F[_]](implicit ap: ApplyFromJava[F]): ApplyFromJava[F] = ap

  trait Syntax {
    implicit def toOps[F[_]: ApplyFromJava, T: ClassTag](f: => CompletableFuture[T]): ApplyFromJavaOps[F, T] =
      new ApplyFromJavaOps(f)
  }

  class ApplyFromJavaOps[F[_]: ApplyFromJava, T: ClassTag](f: => CompletableFuture[T]) {
    def defer(implicit ev: ApplyFromJava[F]): F[T] = ev.deferFuture(f)
  }

  implicit val taskFromJava: ApplyFromJava[Task] = new ApplyFromJava[Task] {
    override def deferFuture[T: ClassTag](arg: => CompletableFuture[T]): Task[T] = {
      Task async { (_, cb) =>
        arg whenComplete {
          case (v: T, _) =>
            cb.onSuccess(v)
          case (_, e: Throwable) =>
            cb.onError(e)
          case (null, null) =>
            cb.onError(new NoSuchElementException("Value returned from future was NULL"))
        }
        Cancelable(() => arg.cancel(true))
      }
    }
  }

  implicit val ioFromJava: ApplyFromJava[IO] = new ApplyFromJava[IO] {
    override def deferFuture[T: ClassTag](arg: => CompletableFuture[T]): IO[T] =
      IO async { cb =>
        import cats.syntax.either._
        arg whenComplete {
          case (v: T, _) =>
            cb(v.asRight)
          case (_, e: Throwable) =>
            cb(e.asLeft)
          case (null, null) =>
            cb(new NoSuchElementException("Value returned from future was NULL").asLeft)
        }
      }
  }

}
