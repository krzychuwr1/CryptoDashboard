package pl.edu.agh.crypto.dashboard.model

case class TradingInfoEntry(
  info: Map[CurrencyName, TradingInfo]
) extends AnyVal
