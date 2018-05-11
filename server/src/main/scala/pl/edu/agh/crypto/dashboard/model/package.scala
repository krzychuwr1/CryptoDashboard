package pl.edu.agh.crypto.dashboard

import io.circe.Encoder
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

package object model {

  type PricePairEntry = Map[CurrencyName, BigDecimal]
  type TradingInfoEntry = Map[CurrencyName, TradingInfo]

  implicit val encodeDateTime: Encoder[DateTime] = Encoder.encodeString contramap ISODateTimeFormat.basicDateTime().print
}
