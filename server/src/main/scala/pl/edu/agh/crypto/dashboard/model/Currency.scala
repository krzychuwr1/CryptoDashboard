package pl.edu.agh.crypto.dashboard.model

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._

case class Currency(
  name: CurrencyName
)

object Currency {
  implicit val encoder: Encoder[Currency] = Encoder.forProduct2("name", "_key")(c => c.name -> c.name)
  implicit val decoder: Decoder[Currency] = { _.downField("name").as[CurrencyName].map(Currency(_)) }
}
