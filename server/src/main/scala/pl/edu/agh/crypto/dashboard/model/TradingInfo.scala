package pl.edu.agh.crypto.dashboard.model

import org.joda.time.DateTime

import io.circe.Encoder
import io.circe.generic.semiauto._

case class TradingInfo(
  when: DateTime,
  fromSymbol: Currency,
  toSymbol: Currency,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
)

object TradingInfo {

  implicit val currencyEncoder: Encoder[Currency] = Encoder[String].contramap(_.name.name.value)
  implicit val encoder: Encoder[TradingInfo] = deriveEncoder

}
