package pl.edu.agh.crypto.dashboard.model

import io.circe.Encoder
import org.joda.time.DateTime

//all fields for the edge, internal for database
case class Edge(at: DateTime, to: CurrencyName)

object Edge {
  //encoder translating at -> _key
  implicit val encoder: Encoder[Edge] = Encoder.forProduct2("_key", "to")(Edge.unapply(_).get)
}
