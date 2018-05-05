package pl.edu.agh.crypto.dashboard

import fs2.StreamApp
import monix.eval.Task
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.implicits._
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{BalanceAPI, BasicDataSource, CommonParsers}
import monix.execution.Scheduler.Implicits.global
import org.http4s.server.blaze.BlazeBuilder
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import pl.edu.agh.crypto.dashboard.service.{BasicTradingService, DataServiceProvider, TradingDataService}

object Launcher {

  class BasicDataServiceProvider extends DataServiceProvider[Task, BasicDataSource[Task]] {

    private class MockDataSource[T](val fromSymbol: CurrencyName) extends DataSource[Task, T] {
      override def getDataOf(
        toSymbols: Set[CurrencyName],
        from: Option[DateTime],
        to: Option[DateTime]
      ): Task[List[T]] = Task.now(List.empty)
    }

    private def dataService(currencyName: CurrencyName) = {
      import shapeless._
      new BasicTradingService[Task, BasicDataSource[Task]](
        currencyName,
        new MockDataSource[PricePairEntry](currencyName) :: new MockDataSource[TradingInfoEntry](currencyName) :: HNil
      )
    }

    override def getTradingDataService(currencyName: CurrencyName): Task[TradingDataService[Task, BasicDataSource[Task]]] =
      Task { dataService(currencyName) }
  }

  class App extends StreamApp[Task] {

    private val swaggerSupport = SwaggerSupport.apply[Task]
    private val middleware = swaggerSupport.createRhoMiddleware()

    lazy val balanceAPI = new BalanceAPI[Task](
      DateTime.parse("2018-05-01T00:00:01"),
      Set("BTC".ci, "ETH".ci),
      new BasicDataServiceProvider,
      new CommonParsers[Task] {}
    )

    override def stream(args: List[String], requestShutdown: Task[Unit]): fs2.Stream[Task, StreamApp.ExitCode] =
      BlazeBuilder[Task]
      .bindHttp(7077, "0.0.0.0")
      .mountService(balanceAPI.toService(middleware))
      .serve
  }

  def main(args: Array[String]): Unit = {
    val app = new App
    app.main(args)
  }

}
