package pl.edu.agh.crypto.dashboard

package object model {

  type PricePairEntry = Map[CurrencyName, BigDecimal]
  type TradingInfoEntry = Map[CurrencyName, TradingInfo]

}
