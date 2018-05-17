package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import cats.syntax.functor._
import cats.syntax.flatMap._
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.AqlQueryOptions
import io.circe.{Decoder, Encoder, Json}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import shapeless.tag.@@


abstract class GraphQueries[F[_], From: Encoder : Decoder, To: Encoder : Decoder, Edge: Encoder : Decoder](
  dbAsync: ArangoDatabaseAsync,
  graph: GraphDefinition[From, To]
)(implicit
  effect: Effect[F],
  ap: ApplyFromJava[F]
) extends QueryInterpolation with CollectionUtils with QueryParameter.Syntax with ApplyFromJava.Syntax {
  import GraphDefinition._

  import io.circe.syntax._
  import EdgeVertex._

  val keyValueQueries = new KeyValueQueries[F](dbAsync) {}

  private def edgeOf(from: From, to: To, edgeFields: Option[Edge])(implicit
    fromKey: String @@ From,
    toKey: String @@ To
  ) = {
    val edgeFieldsObj = edgeFields.map(_.asJson).getOrElse(Json.obj())

    Json.obj(
      "_from" := graph.fromID,
      "_to" := graph.toID
    ).deepMerge(edgeFieldsObj)
  }


  private def putOnlyEdge(from: From, to: To, edgeFields: Option[Edge])(implicit
    fromKey: String @@ From,
    toKey: String @@ To
  ): F[Unit] = {
    val edge = edgeOf(from, to, edgeFields)
    dbAsync.executeModificationQuery(
      aql"""
           |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
           |""".stripMargin,
      new AqlQueryOptions()
    )
  }

  def putEdge(from: EdgeVertex[From], to: EdgeVertex[To], edgeFields: Option[Edge]): F[Unit] = {
    implicit val fromKey: (String @@ From) = graph.fromKey(from.value)
    implicit val toKey: (String @@ To) = graph.toKey(to.value)
    (from, to) match {
      case (EdgeVertex(f, DoNothing), EdgeVertex(t, DoNothing)) =>
        putOnlyEdge(f, t, edgeFields)
      case (EdgeVertex(f, Upsert), EdgeVertex(t, DoNothing)) =>
        keyValueQueries.put(graph.fromCollection)(fromKey, f)
          .flatMap(_ => putOnlyEdge(f, t, edgeFields))
      case (EdgeVertex(f, DoNothing), EdgeVertex(t, Upsert)) =>
        keyValueQueries.put(graph.toCollection)(toKey, t)
          .flatMap(_ => putOnlyEdge(f, t, edgeFields))
      case (EdgeVertex(f, Upsert), EdgeVertex(t, Upsert)) =>
        for {
          _ <- keyValueQueries.put(graph.fromCollection)(fromKey, f)
          _ <- keyValueQueries.put(graph.toCollection)(toKey, t)
          _ <- putOnlyEdge(f, t, edgeFields)
        } yield {}
    }
  }

  def getTo(from: From, filter: (String, String) => String = (_, _) => ""): F[Map[Edge, To]] = {
    implicit val fromKey = graph.fromKey(from)
    dbAsync.executeQuery[(Edge, To)](
      aql"""
           |FOR v, e IN 1..1 OUTBOUND ${bindKey(graph.fromID)} GRAPH '${graph.name}'
           | ${filter("v", "e")}
           | RETURN [e, v]
      """.stripMargin,
      new AqlQueryOptions()
    ).map(_.toMap)
  }

  def getFrom(to: To, filter: (String, String) => String = (_, _) => ""): F[Map[Edge, From]] = {
    implicit val toKey = graph.toKey(to)
    dbAsync.executeQuery[(Edge, From)](
      aql"""
           |FOR v, e IN 1..1 INBOUND ${bindKey(graph.toID)} GRAPH '${graph.name}'
           | ${filter("v", "e")}
           | RETURN [e, v]
      """.stripMargin,
      new AqlQueryOptions()
    ).map(_.toMap)
  }

}

object GraphQueries
