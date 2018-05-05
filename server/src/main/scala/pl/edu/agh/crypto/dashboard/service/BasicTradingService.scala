package pl.edu.agh.crypto.dashboard.service

import cats.effect.Effect
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import shapeless.HList

class BasicTradingService[F[_]: Effect, DS <: HList](
   val fromSymbol: CurrencyName,
   val dataSources: DS
) extends TradingDataService[F, DS]
