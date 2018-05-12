package pl.edu.agh.crypto.dashboard

import fs2.StreamApp
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.server.blaze.BlazeBuilder
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{CommonParsers, GenericBalanceAPI}
import pl.edu.agh.crypto.dashboard.config.DataTypeEntry
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.{DataService, DataSource}
import shapeless.PolyDefns.~>

object Launcher {

  private class MockDataService[T] extends DataService[Task, T] {

    private class MockDataSource(val fromSymbol: CurrencyName) extends DataSource[Task, T] {
      override def getDataOf(
        toSymbols: Set[CurrencyName],
        from: Option[DateTime],
        to: Option[DateTime]
      ): Task[List[T]] = Task.now(List.empty)
    }

    override def getDatSource(currency: CurrencyName): Task[DataSource[Task, T]] =
      Task.pure(new MockDataSource(currency)).memoizeOnSuccess
  }

  private def mockDataService[T]: Task[DataService[Task, T]] = Task.pure(new MockDataService[T]).memoize

  class App extends StreamApp[Task] {

    private val swaggerSupport = SwaggerSupport.apply[Task]
    private val middleware = swaggerSupport.createRhoMiddleware()
    import api.ciStringKeyEncoder
    import shapeless._

    private val priceEntry = DataTypeEntry[PricePairEntry](
      "rate",
      "exchange rates for the specific currency, against some other in the given time period"
    )

    private val tradingEntry = DataTypeEntry[TradingInfoEntry](
      "info",
      "complete information about specific currency pair"
    )

    lazy val genericAPI = new GenericBalanceAPI[Task, DataTypeEntry[PricePairEntry] :: DataTypeEntry[TradingInfoEntry] :: HNil](
      keys =
        priceEntry ::
          tradingEntry ::
          HNil,
      dataServices = λ[DataTypeEntry ~> λ[X => Task[DataService[Task, X]]]](_ => mockDataService),
      new CommonParsers[Task] {}
    ) {
      keys.map(routes).toList.foreach(_ => ())
    }

    override def stream(args: List[String], requestShutdown: Task[Unit]): fs2.Stream[Task, StreamApp.ExitCode] =
      BlazeBuilder[Task]
      .bindHttp(7077, "0.0.0.0")
      .mountService(genericAPI.toService(middleware))
      .serve
  }

  def main(args: Array[String]): Unit = {
    val app = new App
    app.main(args)
  }

}
