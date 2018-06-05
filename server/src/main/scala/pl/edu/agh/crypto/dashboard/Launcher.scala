package pl.edu.agh.crypto.dashboard

import cats.~>
import fs2.StreamApp
import io.circe.{Decoder, Encoder}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.client.blaze.Http1Client
import org.http4s.implicits._
import org.http4s.rho.RhoService
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.server.blaze.BlazeBuilder
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{CommonParsers, GenericBalanceAPI}
import pl.edu.agh.crypto.dashboard.config.{ApplicationConfig, DBConfig, DataTypeEntry}
import pl.edu.agh.crypto.dashboard.model.{Currency, CurrencyName, DailyTradingInfo, Indicators, PriceInfo, RawTradingInfo, TradingInfo}
import pl.edu.agh.crypto.dashboard.persistence.{Connectable, GraphDefinition}
import pl.edu.agh.crypto.dashboard.service._

import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object Launcher extends DBConfig[Task](
  λ[Task ~> Task](_.memoizeOnSuccess),
  ApplicationConfig.load(),
  Set(Currency(CurrencyName("BTC".ci)), Currency(CurrencyName("ETH".ci))),
  "dashboard"
) {

  import scala.concurrent.duration._

  private val logger = org.log4s.getLogger

  private val targetCurrencies = Set(CurrencyName("USD".ci), CurrencyName("EUR".ci))

  private val historicalCrawlerConfig = CrawlerConfig(
    supportedCurrency.map(_.name),
    targetCurrencies,
    1.second,
    "https://min-api.cryptocompare.com/data/histoday",
    DateTime.now() minusDays 100
  )

  private val realTimeCrawlerConfig = historicalCrawlerConfig.copy(
    interval = 1.minute,
    path = "https://min-api.cryptocompare.com/data/pricemultifull"
  )

  private val client = Http1Client[Task]()

  private val realTimeCrawler = client.map(c => new RealTimeCrawler[Task, RawTradingInfo, TradingInfo](
    c,
    realTimeCrawlerConfig,
    _.toTradingInfo(DateTime.now())
  ))

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
        "daily-data",
        "daily-of",
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

  private val historicCrawler = {
    import cats.syntax.option._
    for {
      ds <- dailyService
      c <- client
      source <- ds.getDataSource(supportedCurrency.head.name)
      presentData <- source.getDataOf(targetCurrencies, DateTime.now().minusDays(2).some, DateTime.now().some)
    } yield {
      val nonEmpty = presentData.flatMap({ case (_, v) => v }).nonEmpty
      logger.info(s"Creating historic crawler, initialised: $nonEmpty")
      new HistoricalHttpCrawler[Task, DailyTradingInfo](
        c,
        historicalCrawlerConfig,
        nonEmpty
      )
    }
  }

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
    "indicators",
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

  private def crawlerConnector[T: ClassTag](getCurrency: T => CurrencyName) = new CrawlerConnector.MonixInstance[T](
    t => Task {
      logger.error(t)("Task failed")
    },
    getCurrency
  )

  def main(args: Array[String]): Unit = {

    val dailyT: Task[Unit] = for {
      ds <- tradingInfoDs
      rc <- realTimeCrawler
      _ <- crawlerConnector[TradingInfo](_.fromSymbol).crawl(rc, ds)
    } yield ()

    val historicT: Task[Unit] = for {
      ds <- dailyService
      hc <- historicCrawler
      _ <- crawlerConnector[DailyTradingInfo](_.fromSymbol).crawl(hc, ds)
    } yield ()

    dailyT.runOnComplete({
      case Success(_) =>
      case Failure(t) =>
        logger.error(t)("Crawlers failed")
        sys.exit(1)
    })

    historicT.runOnComplete({
      case Success(_) =>
      case Failure(t) =>
        logger.error(t)("Crawlers failed")
        sys.exit(1)
    })

    val app = new App(genericAPI)
    app.main(args)
  }

}
