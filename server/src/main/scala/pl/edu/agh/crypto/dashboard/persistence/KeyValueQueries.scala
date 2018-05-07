package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.{AqlQueryOptions, DocumentReadOptions}
import io.circe.jawn.decode
import io.circe.{Decoder, Encoder, KeyEncoder}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava


abstract class KeyValueQueries[F[_]](
  dbAsync: ArangoDatabaseAsync
) extends QueryInterpolation with CollectionUtils with QueryParameter.Syntax with ApplyFromJava.Syntax {
  import cats.syntax.either._
  import cats.syntax.flatMap._
  import cats.syntax.monadError._

  private def lift[T <: Throwable](t: T): Throwable = t

  def put[T: Encoder, ID: KeyEncoder](collection: String)(
    key: ID,
    elem: T
  )(implicit
    ef: Effect[F],
    ap: ApplyFromJava[F]
  ): F[Unit] = {
    dbAsync.executeModificationQuery(
      aql"""UPSERT { '_key': ${bindKey(key)} }
        |INSERT ${bind(elem)}
        |UPDATE ${bind(elem)} IN ${bindCollection(collection)}
      """.stripMargin,
      new AqlQueryOptions()
    )
  }

  def get[T: Decoder, ID: KeyEncoder](collection: String)(
    key: ID
  )(implicit
    ef: Effect[F],
    ap: ApplyFromJava[F]
  ): F[T] = {
    dbAsync
      .collection(collection)
      .getDocument(KeyEncoder[ID].apply(key), classOf[String], new DocumentReadOptions().catchException(true))
      .defer
      .flatMap(s => ef.pure(decode[T](s).leftMap(lift)))
      .rethrow
  }

  def delete[ID: KeyEncoder](collection: String)(
    key: ID
  )(implicit
    ef: Effect[F],
    ap: ApplyFromJava[F]
  ): F[Unit] = {
    import cats.syntax.functor._
    dbAsync
      .collection(collection)
      .deleteDocument(KeyEncoder[ID].apply(key))
      .defer
      .map(_ => ())
  }

  def update[T: Encoder, ID: KeyEncoder](collection: String)(
    key: ID,
    elem: T
  )(implicit
    ef: Effect[F],
    ap: ApplyFromJava[F]
  ): F[Unit] = {
    import cats.syntax.functor._
    dbAsync
      .collection(collection)
      .updateDocument(KeyEncoder[ID].apply(key), Encoder[T].apply(elem).noSpaces)
      .defer
      .map(_ => ())
  }

}
