package pl.edu.agh.crypto.dashboard.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.joda.time.DateTime

//all fields for the edge, internal for database
case class Edge(at: DateTime, to: CurrencyName)

object Edge {
  //encoder translating at -> _key
  implicit val encoder: Encoder[Edge] = deriveEncoder
  implicit val decoder: Decoder[Edge] = deriveDecoder
}
