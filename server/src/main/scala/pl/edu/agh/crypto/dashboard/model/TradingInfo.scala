package pl.edu.agh.crypto.dashboard.model

import org.joda.time.DateTime

case class TradingInfo(
  when: DateTime,
  fromSymbol: Currency,
  toSymbol: Currency,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
)
