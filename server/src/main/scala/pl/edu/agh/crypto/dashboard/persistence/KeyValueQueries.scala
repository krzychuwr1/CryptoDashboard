package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import com.arangodb.{ArangoDBException, ArangoDatabaseAsync}
import com.arangodb.model.{AqlQueryOptions, DocumentReadOptions}
import io.circe.jawn.decode
import io.circe.{Decoder, Encoder, KeyEncoder}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava


abstract class KeyValueQueries[F[_]](
  dbAsync: ArangoDatabaseAsync
)(implicit
  ef: Effect[F],
  ap: ApplyFromJava[F]
) extends QueryInterpolation with CollectionUtils with QueryParameter.Syntax with ApplyFromJava.Syntax {
  import cats.syntax.either._
  import cats.syntax.flatMap._
  import cats.syntax.monadError._

  private def lift[T <: Throwable](t: T): Throwable = t

  def put[T: Encoder, ID: KeyEncoder](collection: String)(
    key: ID,
    elem: T
  ): F[Unit] = {
    println(key)
    println(bind(elem))
    dbAsync.executeModificationQuery(
      aql"""UPSERT { '_key': ${bindKey(key)} }
        |INSERT MERGE(${bind(elem)}, {'_key': ${bindKey(key)}})
        |REPLACE MERGE(${bind(elem)}, {'_key': ${bindKey(key)}}) IN ${bindCollection(collection)}
      """.stripMargin,
      new AqlQueryOptions()
    )
  }

  def get[T: Decoder, ID: KeyEncoder](collection: String)(
    key: ID
  ): F[T] = {
    getRaw[T](collection)(KeyEncoder[ID].apply(key))
  }

  def getRaw[T: Decoder](collection: String)(
    key: String
  ): F[T] = {
    dbAsync
      .collection(collection)
      .getDocument(key, classOf[String], new DocumentReadOptions().catchException(true))
      .defer
      .flatMap(s => ef.pure(decode[T](s).leftMap(lift)))
      .rethrow
  }

  def delete[ID: KeyEncoder](collection: String)(
    key: ID
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
  ): F[Unit] = {
    import cats.syntax.functor._
    dbAsync
      .collection(collection)
      .updateDocument(KeyEncoder[ID].apply(key), Encoder[T].apply(elem).noSpaces)
      .defer
      .map(_ => ())
  }

}
