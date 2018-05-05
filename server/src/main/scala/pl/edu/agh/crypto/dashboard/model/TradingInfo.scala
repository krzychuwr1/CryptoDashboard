package pl.edu.agh.crypto.dashboard.model

case class TradingInfo(
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
)
