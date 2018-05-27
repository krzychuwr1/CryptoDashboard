package pl.edu.agh.crypto.dashboard.service


import cats.effect.Effect
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.instances.list._
import cats.syntax.option._
import org.joda.time.DateTime
import org.ta4j.core.TimeSeries
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, Indicators, TradingInfo}
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava

class IndicatorService[F[_]: Effect: ApplyFromJava](
  indicatorService: DataService[F, Indicators],
  infoService: DataService[F, TradingInfo]
) {

  private def timeSeriesOf(name: String, data: Seq[TradingInfo]): TimeSeries = ???

  private def generateSeries(currency: CurrencyName, toCurrencies: Set[CurrencyName])(from: DateTime, to: DateTime) = for {
    is <- infoService.getDataSource(currency)
    data <- is.getDataOf(toCurrencies, from.some, to.some)
    transformed = data map {
      case (cn, ti) =>
        val ts = timeSeriesOf(cn.name.value, ti)
        val closePrice = new ClosePriceIndicator(ts)
        val shortSma = new SMAIndicator(closePrice, 5)
        cn -> shortSma.getValue(42) //todo how to extract data
    }
    _ <- transformed.toList traverse {
      case (n, v) =>
        indicatorService.getDataSink(n).flatMap(_.saveData(Indicators(???, ???, ???, ???)))
    }
  } yield ()

}
