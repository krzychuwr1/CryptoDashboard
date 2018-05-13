package pl.edu.agh.crypto.dashboard.model

import org.joda.time.DateTime
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import pl.edu.agh.crypto.dashboard.persistence.Connectable

case class TradingInfo(
  when: DateTime,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
) {
  def key: String = s"${fromSymbol.name.value}-${toSymbol.name.value}-${when.getMillis}"
}

object TradingInfo {
  implicit val encoder: Encoder[TradingInfo] = deriveEncoder
  implicit val decoder: Decoder[TradingInfo] = deriveDecoder

  implicit val connectable: Connectable[TradingInfo] = { ti => Edge(ti.when, ti.toSymbol) }
}
