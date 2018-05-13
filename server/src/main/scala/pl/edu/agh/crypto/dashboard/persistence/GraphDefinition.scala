package pl.edu.agh.crypto.dashboard.persistence

import java.util.Collections

import cats.effect.Effect
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.entity.EdgeDefinition
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import shapeless.PolyDefns.~>

case class GraphDefinition[From, To](
  name: String,
  edgeCollection: String,
  fromCollection: String,
  toCollection: String,
  fromKey: From => String,
  toKey: To => String
) {

  def fromID(doc: From): String = s"$fromCollection/${fromKey(doc)}"
  def toID(doc: To): String = s"$toCollection/${toKey(doc)}"
}

object GraphDefinition extends ApplyFromJava.Syntax {

  import cats.syntax.applicative._
  import cats.syntax.functor._
  import cats.syntax.flatMap._

  def create[F[_]: Effect: ApplyFromJava, From, To](
    dbAsync: ArangoDatabaseAsync,
    memoization: F ~> F
  )(
    name: String,
    edgeCollection: String,
    fromCollection: String,
    toCollection: String,
    fromKey: From => String,
    toKey: To => String
  ): F[GraphDefinition[From, To]] = {

    def createCollection(collections: Set[String])(name: String): F[Unit] = {
      if (collections contains name) ().pure[F]
      else dbAsync.createCollection(name).defer.map(_ => ())
    }

    def createGraph(graphs: Set[String]): F[Unit] = {
      if (graphs contains name) ().pure[F]
      else {
        val ed = new EdgeDefinition().collection(edgeCollection).from(fromCollection).to(toCollection)
        dbAsync.createGraph(name, Collections.singletonList(ed))
          .defer
          .map(_ => ())
      }
    }
    import scala.collection.JavaConverters._

    val rawF = for {
      jcol <- dbAsync.getCollections.defer
      collections = jcol.asScala.map(_.getName).toSet
      _ <- createCollection(collections)(fromCollection)
      _ <- createCollection(collections)(toCollection)
      _ <- createCollection(collections)(edgeCollection)
      jgraphs <- dbAsync.getGraphs.defer
      graphs = jgraphs.asScala.map(_.getName).toSet
      _ <- createGraph(graphs)
    } yield GraphDefinition(
      name = name,
      edgeCollection = edgeCollection,
      fromCollection = fromCollection,
      toCollection = toCollection,
      fromKey = fromKey,
      toKey = toKey
    )

    memoization(rawF)
  }
}
