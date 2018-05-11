package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import shapeless.{HList, HNil, ::}

class BasicTradingService[F[_]: Effect, DS <: HList](
   val fromSymbol: CurrencyName,
   val dataSources: DS
) extends TradingDataService[F, DS]

object BasicTradingService {
  type BasicDataSource[F[_]] = DataSource[F, PricePairEntry] :: DataSource[F, TradingInfoEntry] :: HNil

  private[service] final class PartiallyAppliedDataService[F[_]](private val dummy: Boolean = true) extends AnyVal {
    def apply[DS <: HList](
      fromSymbol: CurrencyName,
      dataSources: DS
    )(implicit
      ef: Effect[F]
    ): BasicTradingService[F, DS] = new BasicTradingService(
      fromSymbol,
      dataSources
    )
  }

  def apply[F[_]]: PartiallyAppliedDataService[F] = new PartiallyAppliedDataService
}
