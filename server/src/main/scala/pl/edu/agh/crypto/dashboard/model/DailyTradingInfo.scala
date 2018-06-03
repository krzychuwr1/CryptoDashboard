package pl.edu.agh.crypto.dashboard.model

import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime
import io.circe.generic.semiauto._
import org.ta4j.core.{Bar, BaseBar}
import pl.edu.agh.crypto.dashboard.persistence.Connectable
import pl.edu.agh.crypto.dashboard.util._

case class DailyTradingInfo(
  time: DateTime,
  close: BigDecimal,
  high: BigDecimal,
  low: BigDecimal,
  open: BigDecimal,
  volume: BigDecimal,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName
) {
  def key: String = s"${fromSymbol.name.value}-${toSymbol.name.value}-${time.getMillis}"

  def toBar: Bar = new BaseBar(
    time.dateTimeToZonedTime,
    open.toTa4j,
    high.toTa4j,
    low.toTa4j,
    close.toTa4j,
    volume.toTa4j
  )
}

object DailyTradingInfo {

  implicit val encoder: Encoder[DailyTradingInfo] = deriveEncoder
  implicit val decoder: Decoder[DailyTradingInfo] = deriveDecoder
  implicit val connectable: Connectable[DailyTradingInfo] = { ti => Edge(ti.time, ti.toSymbol) }

}
