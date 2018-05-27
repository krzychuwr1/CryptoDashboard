package pl.edu.agh.crypto.dashboard.model

import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime
import io.circe.generic.semiauto._
import pl.edu.agh.crypto.dashboard.persistence.Connectable

case class Indicators(sma: BigDecimal, fromSymbol: CurrencyName, toSymbol: CurrencyName, date: DateTime) {
  def key: String = s"${fromSymbol.name}-${toSymbol.name}-${date.getMillis}"
}

object Indicators {
  implicit val encoder: Encoder[TradingInfo] = deriveEncoder
  implicit val decoder: Decoder[TradingInfo] = deriveDecoder

  implicit val connectable: Connectable[TradingInfo] = { ti => Edge(ti.when, ti.toSymbol) }
}

