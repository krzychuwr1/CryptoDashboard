package pl.edu.agh.crypto.dashboard.model

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import org.joda.time.DateTime

@ConfiguredJsonCodec case class RawTradingInfo(
  @JsonKey("FROMSYMBOL") fromSymbol: CurrencyName,
  @JsonKey("TOSYMBOL") toSymbol: CurrencyName,
  @JsonKey("PRICE") price: BigDecimal,
  @JsonKey("SUPPLY") supply: BigDecimal,
  @JsonKey("MKTCAP") marketCap: BigDecimal
) {
  def toTradingInfo(when: DateTime) = TradingInfo(
    when,
    fromSymbol,
    toSymbol,
    price,
    supply.toBigInt,
    marketCap
  )
}

object RawTradingInfo {
  implicit val config: Configuration = Configuration.default
}
