package pl.edu.agh.crypto.dashboard

import cats.~>
import fs2.StreamApp
import io.circe.{Decoder, Encoder}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
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

  private val historicalCrawlerConfig = CrawlerConfig(
    supportedCurrency.map(_.name),
    Set(CurrencyName("USD".ci), CurrencyName("EUR".ci)),
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
  private val historicCrawler = client.map(c => new HistoricalHttpCrawler[Task, DailyTradingInfo](
    c,
    historicalCrawlerConfig
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

  private val logger = org.log4s.getLogger

  private def crawlerConnector[T] = new CrawlerUtils.MonixInstance[T](t => Task {
    logger.error(t)("Task failed")
  })

  def main(args: Array[String]): Unit = {

    def runCrawler[T](
      crawler: Crawler[Observable, T],
      dataService: DataService[Task, T]
    )(
      byCurrency: T => CurrencyName
    )(
      implicit ct: ClassTag[T]
    ): Task[Unit] = {

      val grouped = crawler.stream.groupBy(byCurrency)

      val running = grouped map { obs =>
        logger.info(s"Running crawler for ${obs.key.name}, type: ${ct.runtimeClass.getSimpleName}")
        val runObs = for {
          sink <- Observable.fromTask(dataService.getDataSink(obs.key))
          res <- obs.mapTask(sink.saveData)
        } yield res
        runObs.lastOptionL.runOnComplete({
          case Success(_) =>
          case Failure(err) =>
            logger.error(err)(s"Crawler for currency: ${obs.key.name} failed")
            sys.exit(1)
        })
      }

      running.lastOptionL.map(_ => {})
    }

    val dailyT: Task[Unit] = for {
      ds <- tradingInfoDs
      rc <- realTimeCrawler
      _ <- runCrawler(rc, ds)(_.fromSymbol)
    } yield ()

//    val historicT: Task[Unit] = for {
//      ds <- dailyService
//      hc <- historicCrawler
//      _ <- runCrawler(hc, ds)(_.fromSymbol)
//    } yield ()

    dailyT.runOnComplete({
      case Success(_) =>
      case Failure(t) =>
        logger.error(t)("Crawlers failed")
        sys.exit(1)
    })

//    historicT.runOnComplete({
//      case Success(_) =>
//      case Failure(t) =>
//        logger.error(t)("Crawlers failed")
//        sys.exit(1)
//    })

    val app = new App(genericAPI)
    app.main(args)
  }

}
