package pl.edu.agh.crypto.dashboard.model

import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime

//all fields for the edge, internal for database
case class Edge(at: DateTime, to: CurrencyName)

import shapeless.syntax.std.tuple._

object Edge {
  def keyOf(edge: Edge): String = s"${edge.to.name}-at-${edge.at.getMillis}"
  //encoder translating at -> _key
  implicit val encoder: Encoder[Edge] = Encoder.forProduct3("_key", "at", "to")(e => keyOf(e) +: Edge.unapply(e).get)
  implicit val decoder: Decoder[Edge] = Decoder.forProduct2("at", "to")(Edge.apply)

}
