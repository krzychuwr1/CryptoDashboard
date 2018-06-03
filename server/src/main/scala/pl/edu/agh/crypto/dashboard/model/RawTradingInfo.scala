package pl.edu.agh.crypto.dashboard.model

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}

@ConfiguredJsonCodec case class RawTradingInfo(
  @JsonKey("FROMSYMBOL") fromSymbol: CurrencyName,
  @JsonKey("TOSYMBOL") toSymbol: CurrencyName,
  @JsonKey("PRICE") price: BigDecimal,
  @JsonKey("SUPPLY") supply: BigInt,
  @JsonKey("MKTCAP") marketCap: BigDecimal
)

object RawTradingInfo {
  implicit val config: Configuration = Configuration.default
}
