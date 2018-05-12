package pl.edu.agh.crypto.dashboard.model

import org.joda.time.DateTime
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class TradingInfo(
  when: DateTime,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
)

object TradingInfo {
  implicit val encoder: Encoder[TradingInfo] = deriveEncoder
  implicit val decoder: Decoder[TradingInfo] = deriveDecoder
}
