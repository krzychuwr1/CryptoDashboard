package pl.edu.agh.crypto.dashboard.model

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime

case class Indicators(sma: BigDecimal, date: DateTime)

object Indicators {
  implicit val encoder: Encoder[Indicators] = deriveEncoder
  implicit val decoder: Decoder[Indicators] = deriveDecoder
}

