package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.AqlQueryOptions
import io.circe.{Decoder, Encoder, Json}
import pl.edu.agh.crypto.dashboard.model.GraphDefinition
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava


abstract class GraphQueries[F[_], From: Encoder: Decoder, To: Encoder: Decoder](
  dbAsync: ArangoDatabaseAsync,
  graph: GraphDefinition[From, To]
)(implicit
  effect: Effect[F],
  ap: ApplyFromJava[F]
) extends QueryInterpolation with CollectionUtils with QueryParameter.Syntax with ApplyFromJava.Syntax {
  import io.circe.syntax._
  import EdgeVertex._

  private def edgeOf(from: From, to: To, edgeKey: Option[String]) = {
    val keyObject = edgeKey.map(k => Json.obj("_key" := k)).getOrElse(Json.obj())

    Json.obj(
      "_from" := graph.fromID(from),
      "_to" := graph.toID(to)
    ).deepMerge(keyObject)
  }


  private def putOnlyEdge(from: From, to: To, edgeKey: Option[String]): F[Unit] = {
    val edge = edgeOf(from, to, edgeKey)
    dbAsync.executeModificationQuery(
      aql"""
        |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
        |""".stripMargin,
      new AqlQueryOptions()
    )
  }

  private def putEdgeAndTo(from: From, to: To, edgeKey: Option[String]): F[Unit] = {
    val edge = edgeOf(from, to, edgeKey)

    dbAsync.executeModificationQuery(
      aql"""FOR v IN [0..0] OUTBOUND ${bindKey(graph.fromID(from))} GRAPH ${graph.name}
        |UPSERT { '_key': ${bindKey(graph.toKey(to))}
        |INSERT ${bind(to)} REPLACE ${bind(to)} IN ${bindCollection(graph.toCollection)}
        |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
      """.stripMargin,
      new AqlQueryOptions()
    )

  }

  private def putEdgeAndFrom(from: From, to: To, edgeKey: Option[String]): F[Unit] = {
    val edge = edgeOf(from, to, edgeKey)

    dbAsync.executeModificationQuery(
      aql"""FOR v IN [0..0] INBOUND ${bindKey(graph.toID(to))} GRAPH ${graph.name}
           |UPSERT { '_key': ${bindKey(graph.fromKey(from))}
           |INSERT ${bind(from)} REPLACE ${bind(from)} IN ${bindCollection(graph.fromCollection)}
           |UPSERT ${bind(edge)} INSERT ${bind(edge)} REPLACE ${bind(edge)} IN ${bindCollection(graph.edgeCollection)}
      """.stripMargin,
      new AqlQueryOptions()
    )
  }

  private def putFullEdge(from: From, to: To, edgeKey: Option[String]): F[Unit] = {
    val edge = edgeOf(from, to, edgeKey)
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

  def putEdge(from: EdgeVertex[From], to: EdgeVertex[To], edgeKey: Option[String]): F[Unit] = (from, to) match {
    case (EdgeVertex(f, DoNothing), EdgeVertex(t, DoNothing)) =>
      putOnlyEdge(f, t, edgeKey)
    case (EdgeVertex(f, Upsert), EdgeVertex(t, DoNothing)) =>
      putEdgeAndFrom(f, t, edgeKey)
    case (EdgeVertex(f, DoNothing), EdgeVertex(t, Upsert)) =>
      putEdgeAndTo(f, t, edgeKey)
    case (EdgeVertex(f, Upsert), EdgeVertex(t, Upsert)) =>
      putFullEdge(f, t, edgeKey)
  }

  def getTo(from: From): F[List[To]] =
    dbAsync.executeQuery(
      aql"""
        |FOR v IN [1..1] OUTBOUND ${bindKey(graph.fromID(from))} GRAPH ${graph.name}
        | RETURN v
      """.stripMargin,
      new AqlQueryOptions()
    )

  def getFrom(to: To): F[List[From]] =
    dbAsync.executeQuery(
      aql"""
        |FOR v IN [1..1] INBOUND ${bindKey(graph.toID(to))} GRAPH ${graph.name}
        | RETURN v
      """.stripMargin,
      new AqlQueryOptions()
    )

}
