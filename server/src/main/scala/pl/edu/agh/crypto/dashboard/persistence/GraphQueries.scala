package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import cats.syntax.functor._
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.AqlQueryOptions
import io.circe.{Decoder, Encoder, Json}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava


abstract class GraphQueries[F[_], From: Encoder: Decoder, To: Encoder: Decoder, Edge: Encoder: Decoder](
  dbAsync: ArangoDatabaseAsync,
  graph: GraphDefinition[From, To]
)(implicit
  effect: Effect[F],
  ap: ApplyFromJava[F]
) extends QueryInterpolation with CollectionUtils with QueryParameter.Syntax with ApplyFromJava.Syntax {
  import io.circe.syntax._
  import EdgeVertex._

  val keyValueQueries = new KeyValueQueries[F](dbAsync) {}

  private def edgeOf(from: From, to: To, edgeFields: Option[Edge]) = {
    val edgeFieldsObj = edgeFields.map(_.asJson).getOrElse(Json.obj())

    Json.obj(
      "_from" := graph.fromID(from),
      "_to" := graph.toID(to)
    ).deepMerge(edgeFieldsObj)
  }


  private def putOnlyEdge(from: From, to: To, edgeFields: Option[Edge]): F[Unit] = {
    val edge = edgeOf(from, to, edgeFields)
    dbAsync.executeModificationQuery(
      aql"""
        |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
        |""".stripMargin,
      new AqlQueryOptions()
    )
  }

  private def putEdgeAndTo(from: From, to: To, edgeFields: Option[Edge]): F[Unit] = {
    val edge = edgeOf(from, to, edgeFields)

    dbAsync.executeModificationQuery(
      aql"""FOR v IN [0..0] OUTBOUND ${bindKey(graph.fromID(from))} GRAPH ${graph.name}
        |UPSERT { '_key': ${bindKey(graph.toKey(to))}
        |INSERT ${bind(to)} REPLACE ${bind(to)} IN ${bindCollection(graph.toCollection)}
        |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
      """.stripMargin,
      new AqlQueryOptions()
    )

  }

  private def putEdgeAndFrom(from: From, to: To, edgeFields: Option[Edge]): F[Unit] = {
    val edge = edgeOf(from, to, edgeFields)

    dbAsync.executeModificationQuery(
      aql"""FOR v IN [0..0] INBOUND ${bindKey(graph.toID(to))} GRAPH ${graph.name}
           |UPSERT { '_key': ${bindKey(graph.fromKey(from))}
           |INSERT ${bind(from)} REPLACE ${bind(from)} IN ${bindCollection(graph.fromCollection)}
           |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
      """.stripMargin,
      new AqlQueryOptions()
    )
  }

  private def putFullEdge(from: From, to: To, edgeField: Option[Edge]): F[Unit] = {
    val edge = edgeOf(from, to, edgeField)
    dbAsync.executeModificationQuery(
      aql"""UPSERT { '_key': ${bindKey(graph.fromKey(from))}
        |INSERT ${bind(from)} REPLACE ${bind(from)} IN ${bindCollection(graph.fromCollection)}
        |UPSERT { '_key': ${bindKey(graph.toKey(to))}
        |INSERT ${bind(to)} REPLACE ${bind(to)} IN ${bindCollection(graph.toCollection)}
        |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
      """.stripMargin,
      new AqlQueryOptions()
    )
  }

  def putEdge(from: EdgeVertex[From], to: EdgeVertex[To], edgeFields: Option[Edge]): F[Unit] = (from, to) match {
    case (EdgeVertex(f, DoNothing), EdgeVertex(t, DoNothing)) =>
      putOnlyEdge(f, t, edgeFields)
    case (EdgeVertex(f, Upsert), EdgeVertex(t, DoNothing)) =>
      putEdgeAndFrom(f, t, edgeFields)
    case (EdgeVertex(f, DoNothing), EdgeVertex(t, Upsert)) =>
      putEdgeAndTo(f, t, edgeFields)
    case (EdgeVertex(f, Upsert), EdgeVertex(t, Upsert)) =>
      putFullEdge(f, t, edgeFields)
  }

  def getTo(from: From, filter: (String, String) => String = (_, _) => ""): F[Map[Edge, To]] =
    dbAsync.executeQuery[(Edge, To)](
      aql"""
        |FOR v, e IN [1..1] OUTBOUND ${bindKey(graph.fromID(from))} GRAPH ${graph.name}
        | ${filter("v", "e")}
        | RETURN [e, v]
      """.stripMargin,
      new AqlQueryOptions()
    ).map(_.toMap)

  def getFrom(to: To, filter: (String, String) => String = (_, _) => ""): F[Map[Edge, From]] =
    dbAsync.executeQuery[(Edge, From)](
      aql"""
        |FOR v, e IN [1..1] INBOUND ${bindKey(graph.toID(to))} GRAPH ${graph.name}
        | ${filter("v", "e")}
        | RETURN [e, v]
      """.stripMargin,
      new AqlQueryOptions()
    ).map(_.toMap)

}

object GraphQueries
