package pl.edu.agh.crypto.dashboard.model

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._

case class Currency(
  name: CurrencyName
)

object Currency {
  implicit val encoder: Encoder[Currency] = deriveEncoder
  implicit val decoder: Decoder[Currency] = deriveDecoder
}
