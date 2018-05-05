package pl.edu.agh.crypto.dashboard.service

import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import pl.edu.agh.crypto.dashboard.util.Find
import shapeless.HList

/**
  * Service providing data for a specific COIN ("FROMSYMBOL")
  * It is "type-level" polimorphic, which means that concrete implementations may provide different kind of data
  * @tparam F context
  * @tparam DS types of available data
  */
trait TradingDataService[F[_], DS <: HList] {

  //"list" of all supported data-types
  type DataSources = DS
  def dataSources: DataSources

  @inline private def dataSource[T](implicit find: Find[DataSources, DataSource[F, T]]) = find.get(dataSources)

  protected def fromSymbol: CurrencyName

  def getDataOf[T](toSymbols: Set[CurrencyName])(implicit find: Find[DataSources, DataSource[F, T]]): F[List[T]] =
    dataSource[T].getDataOf(toSymbols, None, None)

  def getDataOf[T](toSymbols: Set[CurrencyName], from: DateTime)(implicit find: Find[DataSources, DataSource[F, T]]): F[List[T]] =
    dataSource[T].getDataOf(toSymbols, Some(from), None)

  def getDataOf[T](toSymbols: Set[CurrencyName], from: DateTime, to: DateTime)(implicit find: Find[DataSources, DataSource[F, T]]): F[List[T]] =
    dataSource[T].getDataOf(toSymbols, Some(from), Some(to))

}

object TradingDataService {

  trait DataSource[F[_], T] {
    protected def fromSymbol: CurrencyName
    def getDataOf(toSymbols: Set[CurrencyName], from: Option[DateTime], to: Option[DateTime]): F[List[T]]
  }
}
