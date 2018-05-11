package pl.edu.agh.crypto.dashboard.service

import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import shapeless.{HList, ops}, ops.hlist._

/**
  * Service providing data for a specific COIN ("FROMSYMBOL")
  * It is "type-level" polymorphic, which means that concrete implementations may provide different kind of data
  * @tparam F context
  * @tparam DS types of available data
  */
trait TradingDataService[F[_], DS <: HList] { self =>

  //"list" of all supported data-types
  type DataSources = DS
  def dataSources: DataSources

  protected def fromSymbol: CurrencyName

  trait SymbolDataSource[T] extends DataSource[F, T] {
    override protected def fromSymbol: CurrencyName = self.fromSymbol
  }

  private type FindDataSource[T] = Selector[DataSources, DataSource[F, T]]

  def getDataOf[T: FindDataSource](toSymbols: Set[CurrencyName]): F[List[T]] =
    dataSources.select[DataSource[F, T]].getDataOf(toSymbols, None, None)

  def getDataOf[T: FindDataSource](toSymbols: Set[CurrencyName], from: DateTime): F[List[T]] =
    dataSources.select[DataSource[F, T]].getDataOf(toSymbols, Some(from), None)

  def getDataOf[T: FindDataSource](toSymbols: Set[CurrencyName], from: DateTime, to: DateTime): F[List[T]] =
    dataSources.select[DataSource[F, T]].getDataOf(toSymbols, Some(from), Some(to))

}

object TradingDataService {

  trait DataSource[F[_], T] {
    protected def fromSymbol: CurrencyName
    def getDataOf(toSymbols: Set[CurrencyName], from: Option[DateTime], to: Option[DateTime]): F[List[T]]
  }
}
