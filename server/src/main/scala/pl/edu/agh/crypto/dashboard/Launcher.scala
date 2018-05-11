package pl.edu.agh.crypto.dashboard

import fs2.StreamApp
import io.circe.Encoder
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.implicits._
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.server.blaze.BlazeBuilder
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{BalanceAPI, CommonParsers, DataTypeEntry, GenericBalanceAPI}
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.BasicDataService
import pl.edu.agh.crypto.dashboard.service.BasicTradingService.BasicDataSource
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import shapeless.HNil

object Launcher {

  private class MockDataSource[T](val fromSymbol: CurrencyName) extends DataSource[Task, T] {
    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): Task[List[T]] = Task.now(List.empty)
  }

  private def mockDataSource[T](fromSymbol: CurrencyName): DataSource[Task, T] = new MockDataSource[T](fromSymbol)


  //quite easy to setup service, without messy type-signatures, etc
  lazy val serviceProvider = BasicDataService { currencyName =>
    import shapeless._
    Task pure {
       mockDataSource[PricePairEntry](currencyName) :: mockDataSource[TradingInfoEntry](currencyName) :: HNil
    }
  }

  class App extends StreamApp[Task] {

    private val swaggerSupport = SwaggerSupport.apply[Task]
    private val middleware = swaggerSupport.createRhoMiddleware()
    import shapeless._
    import api.ciStringKeyEncoder

    lazy val genericAPI = new GenericBalanceAPI[
      Task, DataTypeEntry[PricePairEntry] :: DataTypeEntry[TradingInfoEntry] :: HNil,
      BasicDataSource[Task]](
      keys =
        DataTypeEntry[PricePairEntry]("rate", "exchange rates for the specific currency, against some other in the given time period", implicitly[Encoder[PricePairEntry]]) ::
          DataTypeEntry[TradingInfoEntry]("info", "complete information about specific currency pair", implicitly[Encoder[TradingInfoEntry]]) ::
          HNil,
      dataSource = serviceProvider,
      new CommonParsers[Task] {}
    ) {
      keys.map(routes).toList.foreach(_ => ())
    }

    lazy val balanceAPI = new BalanceAPI[Task](
      DateTime.parse("2018-05-01T00:00:01"),
      Set("BTC".ci, "ETH".ci),
      serviceProvider,
      new CommonParsers[Task] {}
    )

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
