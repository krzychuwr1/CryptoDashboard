package pl.edu.agh.crypto.dashboard.service

import pl.edu.agh.crypto.dashboard.model.CurrencyName

/**
  * For nice, type-safe currying of services
  * @tparam F Effect
  * @tparam T Type of supported data
  */
trait DataService[F[_], T] {
  def getDataSource(currency: CurrencyName): F[DataSource[F, T]]
  def getDataSink(currency: CurrencyName): F[DataSink[F, T]]
}