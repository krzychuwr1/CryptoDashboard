package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.BasicDataSource
import pl.edu.agh.crypto.dashboard.model._
import pl.edu.agh.crypto.dashboard.service.TradingDataService
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava


class PersistentTradingInfoService[F[_]: Effect: ApplyFromJava] private(
  coin: Currency
) extends TradingDataService[F, BasicDataSource[F]] {

  private val tradingInfoDS: DataSource[F, TradingInfoEntry] = new SymbolDataSource[TradingInfoEntry] {
    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): F[List[TradingInfoEntry]] = ???
  }

  private val pricePairEntryDS: DataSource[F, PricePairEntry] = new SymbolDataSource[PricePairEntry] {
    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): F[List[PricePairEntry]] = ???
  }

  override def dataSources: DataSources = {
    import shapeless.HNil
    pricePairEntryDS :: tradingInfoDS :: HNil
  }

  override protected def fromSymbol: CurrencyName = coin.name
}
