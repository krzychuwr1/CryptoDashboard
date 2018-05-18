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
import pl.edu.agh.crypto.dashboard.service.{Crawler, CrawlerUtils, DataService}
import cats.~>

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Launcher extends DBConfig[Task](
  λ[Task ~> Task](_.memoizeOnSuccess),
  ApplicationConfig.load(),
  Set(Currency(CurrencyName("BTC".ci)), Currency(CurrencyName("ETH".ci))),
  "dashboard"
) {

  private class MockCrawler(
    currency: CurrencyName,
    id: FiniteDuration,
    d: FiniteDuration
  ) extends Crawler[Observable, TradingInfo] {

    private val logger = org.log4s.getLogger
    override def stream: Observable[TradingInfo] =
      Observable.intervalAtFixedRate(id, d) map { l =>
        val d = TradingInfo(
          DateTime.now(),
          currency,
          CurrencyName("USD".ci),
          BigDecimal(l % 13) / (l % 29 + 1),
          10000,
          10000
        )
        logger.info(s"Emitting: $d")
        d
      }
  }

  import scala.concurrent.duration._
  private val crawlers = supportedCurrency.map(c => c.name -> new MockCrawler(c.name, 10.seconds, 10.seconds))

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

  private def dataService[T: Decoder : Encoder : Connectable](
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

  class App(endpoint: Task[RhoService[Task]]) extends StreamApp[Task] {

    override def stream(args: List[String], requestShutdown: Task[Unit]): fs2.Stream[Task, StreamApp.ExitCode] = {
      fs2.Stream eval endpoint flatMap { e =>
        BlazeBuilder[Task]
          .bindHttp(7077, "0.0.0.0")
          .mountService(e.toService(middleware))
          .serve
      }
    }
  }

  private val logger = org.log4s.getLogger
  private val crawlerConnector = new CrawlerUtils.MonixInstance[TradingInfo](t => Task {
    logger.error(t)("Task failed")
  })

  def main(args: Array[String]): Unit = {

    for ((currency, crawler) <- crawlers) {
      val t = for {
        s <- tradingInfoDs
        sink <- s.getDataSink(currency)
        _ <- crawlerConnector.crawl(crawler, sink).lastL
      } yield {}
      t runOnComplete {
        case Success(_) =>
        case Failure(f) =>
          logger.error(f)(s"Crawler for currency ${currency.name} crashed")
          sys.exit(1)
      }
    }

    val app = new App(genericAPI)
    app.main(args)
  }

}
