package pl.edu.agh.crypto.dashboard.model

case class PricePairEntry(
  pairs: Map[CurrencyName, BigDecimal]
) extends AnyVal

