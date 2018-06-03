package pl.edu.agh.crypto.dashboard.service

import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.CurrencyName

import scala.concurrent.duration.FiniteDuration

case class CrawlerConfig(
  currencyFrom: Set[CurrencyName],
  currencyTo: Set[CurrencyName],
  interval: FiniteDuration,
  path: String,
  startFrom: DateTime
)