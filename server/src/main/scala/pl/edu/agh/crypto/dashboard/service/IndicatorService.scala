package pl.edu.agh.crypto.dashboard.service


import cats.effect.{Effect, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.joda.time.DateTime
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.{BaseTimeSeries, Decimal, Indicator, TimeSeries}
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, DailyTradingInfo, Indicators}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava

import scala.collection.JavaConverters._

class IndicatorService[F[_] : Effect : ApplyFromJava](
  infoService: DataService[F, DailyTradingInfo]
) extends DataService[F, Indicators] {

  private def timeSeriesOf(name: String, data: Seq[DailyTradingInfo]): TimeSeries = {
    new BaseTimeSeries(data.map(_.toBar).asJava)
  }

  private def smaOf(series: TimeSeries): Indicator[Decimal] = {
    val closePrice = new ClosePriceIndicator(series)
    new SMAIndicator(closePrice, 5)
  }

  private def generateSeries(
    currency: CurrencyName,
    toCurrencies: Set[CurrencyName])(
    from: Option[DateTime],
    to: Option[DateTime]
  ): F[Map[CurrencyName, Seq[Indicators]]] = for {
    is <- infoService.getDataSource(currency)
    data <- is.getDataOf(toCurrencies, from, to)
  } yield {
    data map {
      case (cn, ti) =>
        val ts = timeSeriesOf(cn.name.value, ti)
        val shortSMA = smaOf(ts)
        val data = ti.zipWithIndex map { case (dti, ind) => Indicators(shortSMA.getValue(ind).getDelegate, dti.time) }
        cn -> data
    }
  }

  private class IndicatorDataSource(currency: CurrencyName) extends DataSource[F, Indicators] {
    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): F[Map[CurrencyName, Seq[Indicators]]] =
      generateSeries(currency, toSymbols)(from, to)
  }

  private class NotImplementedSink extends DataSink[F, Indicators] {
    override def saveData(data: Indicators): F[Unit] =
      Effect[F] raiseError new NotImplementedError(s"${classOf[IndicatorService[F]].getSimpleName} does not implement sink")
  }

  override def getDataSource(currency: CurrencyName): F[DataSource[F, Indicators]] = {
    Sync[F] delay new IndicatorDataSource(currency)
  }

  override def getDataSink(currency: CurrencyName): F[DataSink[F, Indicators]] = {
    Sync[F] delay new NotImplementedSink
  }
}
