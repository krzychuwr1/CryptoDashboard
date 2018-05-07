package pl.edu.agh.crypto.dashboard.persistence

import cats.Applicative
import cats.effect.Effect
import cats.instances.ListInstances
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.AqlQueryOptions
import io.circe.Decoder
import pl.edu.agh.crypto.dashboard.persistence.CollectionUtils.DbQueryExtension
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava

import scala.collection.JavaConverters._

trait CollectionUtils {
  implicit def toDbExtension[F[_]: Effect: ApplyFromJava](dbAsync: ArangoDatabaseAsync): DbQueryExtension[F] =
    new DbQueryExtension(dbAsync)
}

object CollectionUtils extends SerializationUtils with ListInstances with ApplyFromJava.Syntax {

  import cats.syntax.either._

  private implicit def defaultEitherInstance[L]: Applicative[Either[L, ?]] = new Applicative[Either[L, ?]] {
    override def pure[A](x: A): Either[L, A] = x.asRight

    override def ap[A, B](ff: Either[L, (A) => B])(fa: Either[L, A]): Either[L, B] = for {
      v <- fa
      f <- ff
    } yield f(v)
  }

  class DbQueryExtension[F[_]](private val dbAsync: ArangoDatabaseAsync) extends AnyVal {

    import cats.syntax.flatMap._
    import cats.syntax.traverse._
    import cats.syntax.monadError._
    import cats.syntax.functor._

    def executeQuery[T: Decoder](
      query: Query,
      options: AqlQueryOptions
    )(implicit
      ef: Effect[F],
      ap: ApplyFromJava[F]
    ): F[List[T]] = {

      dbAsync
        .query(query.code, query.boxParameters.asJava, options, classOf[String])
        .defer
        .flatMap { cursor =>
          ef delay {
            val elems = cursor.iterator.asScala.toList
            elems.traverse(_.deserializeTo[T])
          }
        }.rethrow

    }

    def executeModificationQuery(
      query: Query,
      options: AqlQueryOptions
    )(implicit
      ef: Effect[F],
      ap: ApplyFromJava[F]
    ): F[Unit] = {
      dbAsync
        .query(query.code, query.boxParameters.asJava, options, classOf[String])
        .defer
        .map(_ => ())
    }

  }

}
