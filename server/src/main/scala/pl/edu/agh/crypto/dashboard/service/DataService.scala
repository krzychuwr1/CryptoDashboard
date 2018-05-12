package pl.edu.agh.crypto.dashboard.service

import pl.edu.agh.crypto.dashboard.model.CurrencyName

/**
  * For nice, type-safe currying of services
  * @tparam F Effect
  * @tparam T Type of supported data
  */
trait DataService[F[_], T] {
  def getDatSource(currency: CurrencyName): F[DataSource[F, T]]
}