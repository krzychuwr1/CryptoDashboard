package pl.edu.agh.crypto.dashboard

import fs2.StreamApp
import io.circe.{Decoder, Encoder}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.http4s.implicits._
import org.http4s.rho.RhoService
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.server.blaze.BlazeBuilder
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{CommonParsers, GenericBalanceAPI}
import pl.edu.agh.crypto.dashboard.config.{ApplicationConfig, DBConfig, DataTypeEntry}
import pl.edu.agh.crypto.dashboard.model.{Currency, CurrencyName, PriceInfo, TradingInfo}
import pl.edu.agh.crypto.dashboard.persistence.{Connectable, GraphDefinition}
import pl.edu.agh.crypto.dashboard.service.{Crawler, DataService}
import shapeless.PolyDefns.~>

import scala.concurrent.duration.FiniteDuration

object Launcher extends DBConfig[Task](
  λ[Task ~> Task](_.memoizeOnSuccess),
  ApplicationConfig("localhost", 8529),
  "dashboard"
) {

  private class MockCrawler(currency: CurrencyName, id: FiniteDuration, d: FiniteDuration) extends Crawler[Observable, TradingInfo] {
    override def stream: Observable[Map[CurrencyName, TradingInfo]] =
      Observable.intervalAtFixedRate(id, d) map { l =>
        Map(
          CurrencyName("USD".ci) -> TradingInfo(
            DateTime.now(),
            currency,
            CurrencyName("USD".ci),
            BigDecimal(l % 13) / (l % 29),
            10000,
            10000
          )
        )
      }
  }

  private def graph[T](keyOf: T => String) =
    for {
      db <- dataBaseAsync
      definition <- GraphDefinition.create[Task, Currency, T](
        db,
        memoize
      )(
        "trading-data",
        "data-of",
        "currency",
        "data",
        _.name.name.value,
        keyOf
      )
    } yield definition

  private def dataService[T: Decoder: Encoder: Connectable](
    keyOf: T => String
  ): Task[DataService[Task, T]] =
    for {
      g <- graph[T](keyOf)
      ds <- dataService[T](g)
    } yield ds

  private val swaggerSupport = SwaggerSupport.apply[Task]
  private val middleware = swaggerSupport.createRhoMiddleware()

  private val tiKey = { (ti: TradingInfo) => ti.key }

  private val prKey = { (pi: PriceInfo) => pi.key }

  private val tradingInfoDs = dataService[TradingInfo](tiKey)

  import shapeless._

  private val priceEntry = DataTypeEntry[PriceInfo](
    "rate",
    "exchange rates for the specific currency, against some other in the given time period"
  )

  private val tradingEntry = DataTypeEntry[TradingInfo](
    "info",
    "complete information about specific currency pair"
  )

  private val dataServices = for {
    ps <- dataService[PriceInfo](prKey)
    is <- dataService[TradingInfo](tiKey)
  } yield ps :: is :: HNil

  private val keys = priceEntry :: tradingEntry :: HNil



  lazy val genericAPI = dataServices map { s =>
    val apiConfig = keys.zip(s)
    new GenericBalanceAPI[Task, apiConfig.type](
      apiConfig,
      new CommonParsers[Task] {}
    ) {
      apiConfig.map(routes).toList.foreach(_ => ())
    }
  }

  class App(endpoint: RhoService[Task]) extends StreamApp[Task] {

    override def stream(args: List[String], requestShutdown: Task[Unit]): fs2.Stream[Task, StreamApp.ExitCode] =
      BlazeBuilder[Task]
        .bindHttp(7077, "0.0.0.0")
        .mountService(endpoint.toService(middleware))
        .serve
  }

  def main(args: Array[String]): Unit = {
    val app = genericAPI.map(new App(_))
    app.runAsync.map(_.main(args))
  }

}
