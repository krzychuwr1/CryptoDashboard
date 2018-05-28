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
import org.joda.time.{DateTime, Days}
import pl.edu.agh.crypto.dashboard.api.{CommonParsers, GenericBalanceAPI}
import pl.edu.agh.crypto.dashboard.config.{ApplicationConfig, DBConfig, DataTypeEntry}
import pl.edu.agh.crypto.dashboard.model.{Currency, CurrencyName, DailyTradingInfo, Indicators, PriceInfo, TradingInfo}
import pl.edu.agh.crypto.dashboard.persistence.{Connectable, GraphDefinition}
import pl.edu.agh.crypto.dashboard.service.{Crawler, CrawlerUtils, DataService, IndicatorService}
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

  private class DailyMockCrawler(
    currency: CurrencyName
  ) extends Crawler[Observable, DailyTradingInfo] {
    private val startedDate = DateTime.now()
    private val logger = org.log4s.getLogger
    override def stream: Observable[DailyTradingInfo] =
      Observable.intervalAtFixedRate(15.seconds, 15.seconds) map { l =>
        val low = 10000 - BigDecimal((l + 3999213) % 1000)
        val high = low + BigDecimal((l + 2334113) % 1000)
        val open = low + BigDecimal((l + 123457) % 100 )
        val close = high - BigDecimal((l + 754321) % 100)
        val d = DailyTradingInfo(
          startedDate.plus(Days.days(1)),
          close = close,
          high = high,
          low = low,
          open = open,
          volume = 100000,
          fromSymbol = currency,
          toSymbol = CurrencyName("USD".ci)
        )
        logger.info(s"Emitting: $d")
        d
      }
  }

  private val crawlers = supportedCurrency.map(c => c.name -> new MockCrawler(c.name, 10.seconds, 10.seconds))
  private val dailyCrawler = supportedCurrency.map(c => c.name -> new DailyMockCrawler(c.name))

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

  private val dailyGraph: Task[GraphDefinition[Currency, DailyTradingInfo]] =
    for {
      db <- dataBaseAsync
      definition <- GraphDefinition.create[Task, Currency, DailyTradingInfo](
        db, memoize
      )(
        "indicators",
        "indicator-of",
        "currency",
        "data",
        _.name.name.value,
        _.key
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

  private val dailyService = dailyGraph.flatMap(dataService[DailyTradingInfo])

  private val indicatorService: Task[DataService[Task, Indicators]] = dailyService.map(new IndicatorService(_))

  import shapeless._

  private val priceEntry = DataTypeEntry[PriceInfo](
    "rate",
    "exchange rates for the specific currency, against some other in the given time period"
  )

  private val tradingEntry = DataTypeEntry[TradingInfo](
    "info",
    "complete information about specific currency pair"
  )

  private val dailyEntry = DataTypeEntry[DailyTradingInfo](
    "daily-info",
    "information from an entire day"
  )

  private val indicatorEntry = DataTypeEntry[Indicators](
    "indicatros",
    "basic economic indicators calculated for the given currency"
  )

  private val dataServices = for {
    prices <- dataService[PriceInfo](prKey)
    infos <- tradingInfoDs
    daily <- dailyService
    indicators <- indicatorService
  } yield prices :: infos :: daily :: indicators :: HNil

  private val keys = priceEntry :: tradingEntry :: dailyEntry :: indicatorEntry :: HNil

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

  private def crawlerConnector[T] = new CrawlerUtils.MonixInstance[T](t => Task {
    logger.error(t)("Task failed")
  })

  def main(args: Array[String]): Unit = {

    for ((currency, crawler) <- crawlers) {

      tradingInfoDs runOnComplete {
        case Success(_) =>
        case Failure(f) =>
          logger.error(f)(s"Crawler for currency ${currency.name} crashed")
          sys.exit(1)
      }

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

    for ((currency, crawler) <- dailyCrawler) {

      dailyService runOnComplete {
        case Success(_) =>
        case Failure(f) =>
          logger.error(f)(s"Crawler for currency ${currency.name} crashed")
          sys.exit(1)
      }

      val dt = for {
        s <- dailyService
        sink <- s.getDataSink(currency)
        _ <- crawlerConnector.crawl(crawler, sink).lastL
      } yield {}

      dt runOnComplete {
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
