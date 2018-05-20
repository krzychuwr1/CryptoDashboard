package pl.edu.agh.crypto.dashboard

import io.circe.{Decoder, Encoder}
import org.http4s.util.CaseInsensitiveString
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import scala.util.Try

package object model {

  implicit val encodeDateTime: Encoder[DateTime] = Encoder.encodeString contramap ISODateTimeFormat.basicDateTime().print
  implicit val decodeDateTime: Decoder[DateTime] = Decoder.decodeString emapTry { s =>
    Try { ISODateTimeFormat.basicDateTime().parseDateTime(s) }
  }

  implicit val encodeCurrencyName: Encoder[CurrencyName] = Encoder.encodeString.contramap(_.name.value)
  implicit val decodeCurrencyName: Decoder[CurrencyName] = Decoder.decodeString.map(s => CurrencyName(CaseInsensitiveString(s)))
}
