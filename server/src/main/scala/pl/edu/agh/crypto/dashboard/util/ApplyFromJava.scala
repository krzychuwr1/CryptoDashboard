package pl.edu.agh.crypto.dashboard.util

import java.util.concurrent.CompletableFuture

import cats.effect.IO
import monix.eval.Task
import monix.execution.Cancelable

import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
  * Transform java completable future to the given effect,
  * Null value is reported as 'new NoSuchElementException', courtesy of terrible java standard library,
  * To enable correct null-checking class-tag is present in signature
  * It also tries to enrich stack trace with the information about the location of the exception in code
  * @tparam F target effect
  */
trait ApplyFromJava[F[_]] {
  def deferFuture[T: ClassTag](
    arg: => CompletableFuture[T]
  )(implicit
    enclosing: sourcecode.Enclosing,
    name: sourcecode.Name,
    file: sourcecode.File,
    line: sourcecode.Line
  ): F[T]
}

object ApplyFromJava extends ExceptionUtils {

  def apply[F[_]](implicit ap: ApplyFromJava[F]): ApplyFromJava[F] = ap

  trait Syntax {
    implicit def toOps[F[_]: ApplyFromJava, T: ClassTag](f: => CompletableFuture[T]): ApplyFromJavaOps[F, T] =
      new ApplyFromJavaOps(f)
  }

  class ApplyFromJavaOps[F[_]: ApplyFromJava, T: ClassTag](f: => CompletableFuture[T]) {
    def defer(implicit
      ev: ApplyFromJava[F],
      enclosing: sourcecode.Enclosing,
      name: sourcecode.Name,
      file: sourcecode.File,
      line: sourcecode.Line
    ): F[T] = ev.deferFuture(f)
  }

  implicit val taskFromJava: ApplyFromJava[Task] = new ApplyFromJava[Task] {
    override def deferFuture[T: ClassTag](
      arg: => CompletableFuture[T]
    )(implicit
      enclosing: sourcecode.Enclosing,
      name: sourcecode.Name,
      file: sourcecode.File,
      line: sourcecode.Line
    ): Task[T] = {
      Task async { (_, cb) =>
        try {
          arg whenComplete {
            case (v: T, _) =>
              cb.onSuccess(v)
            case (_, e: Throwable) =>
              cb.onError(enrichedStackTrace(e))
            case (null, null) =>
              cb.onError(new NoSuchElementException("Value returned from future was NULL"))
          }
        } catch {
          case NonFatal(t) =>
            cb.onError(new IllegalStateException("Passed completable future evaluated with exception", t))
        }
        Cancelable(() => arg.cancel(true))
      }
    }
  }

  implicit val ioFromJava: ApplyFromJava[IO] = new ApplyFromJava[IO] {
    override def deferFuture[T: ClassTag](
      arg: => CompletableFuture[T]
    )(implicit
      enclosing: sourcecode.Enclosing,
      name: sourcecode.Name,
      file: sourcecode.File,
      line: sourcecode.Line
    ): IO[T] =
      IO async { cb =>
        import cats.syntax.either._
        try {
          arg whenComplete {
            case (v: T, _) =>
              cb(v.asRight)
            case (_, e: Throwable) =>
              cb(enrichedStackTrace(e).asLeft)
            case (null, null) =>
              cb(new NoSuchElementException("Value returned from future was NULL").asLeft)
          }
        } catch {
          case NonFatal(error) =>
            cb(new IllegalStateException("Passed completable future evaluated with exception", error).asLeft)
        }
      }
  }

}
