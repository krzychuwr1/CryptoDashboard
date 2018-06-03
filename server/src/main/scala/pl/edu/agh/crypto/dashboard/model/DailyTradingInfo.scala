package pl.edu.agh.crypto.dashboard.model

import io.circe._, syntax._
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
  volumefrom: BigDecimal,
  volumeto: BigDecimal,
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
    volumefrom.toTa4j
  )
}

trait DefaultCodecs {
  implicit val encoder: Encoder[DailyTradingInfo] = deriveEncoder
  implicit val decoder: Decoder[DailyTradingInfo] = deriveDecoder
}

object DailyTradingInfo extends DefaultCodecs {

  implicit private class JsonObjOps(private val jsonObject: JsonObject) extends AnyVal {
    def +(kv: (String, Json)): JsonObject = jsonObject.add(kv._1, kv._2)
  }

  implicit val fromSymbols: (CurrencyName, CurrencyName) => Decoder[DailyTradingInfo] = { (f, t) => c =>
    val updated = c.as[JsonObject] map { jo =>
      Json fromJsonObject {
        jo + ("fromSymbol" := f.name.value) + ("toSymbol" := t.name.value)
      }
    }
    updated.flatMap(j => decoder(j.hcursor))
  }

  implicit val connectable: Connectable[DailyTradingInfo] = { ti => Edge(ti.time, ti.toSymbol) }
}
