package pl.edu.agh.crypto.dashboard.model

import org.joda.time.DateTime
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import pl.edu.agh.crypto.dashboard.persistence.Connectable

case class PriceInfo(
  when: DateTime,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal
) {
  def key: String = s"${fromSymbol.name.value}-${toSymbol.name.value}-${when.getMillis}"
}

object PriceInfo {
  implicit val encoder: Encoder[PriceInfo] = deriveEncoder
  implicit val decoder: Decoder[PriceInfo] = deriveDecoder

  implicit val connectable: Connectable[PriceInfo] = { p => Edge(p.when, p.toSymbol) }
}
