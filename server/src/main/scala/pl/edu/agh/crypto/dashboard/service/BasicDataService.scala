package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import cats.syntax.functor._
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import shapeless.HList

/**
  * Utility class for constructing data-sources "from scratch"
  * @param dataSources data sources to be supported by this service
  * @param ef effect-proof
  * @tparam F effect
  * @tparam DS Types of supported data sources
  */
class BasicDataService[F[_], DS <: HList](
  dataSources: CurrencyName => F[DS],
)(implicit
  ef: Effect[F]
) extends DataServiceProvider[F, DS] {
  override def getTradingDataService(currencyName: CurrencyName): F[TradingDataService[F, DS]] =
    dataSources(currencyName).map(BasicTradingService[F](currencyName, _))
}


object BasicDataService {

  def apply[F[_]: Effect, DS <: HList](
    dataSources: CurrencyName => F[DS],
  ): BasicDataService[F, DS] =
    new BasicDataService(dataSources)

}