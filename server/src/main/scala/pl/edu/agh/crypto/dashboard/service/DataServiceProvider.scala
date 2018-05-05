package pl.edu.agh.crypto.dashboard.service

import pl.edu.agh.crypto.dashboard.model.CurrencyName
import shapeless.HList

trait DataServiceProvider[F[_], DataSources <: HList] {
  def getTradingDataService(currencyName: CurrencyName): F[TradingDataService[F, DataSources]]
}
