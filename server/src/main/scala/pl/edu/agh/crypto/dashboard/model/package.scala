package pl.edu.agh.crypto.dashboard

import io.circe.{Decoder, Encoder}
import org.http4s.implicits._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import scala.util.Try

package object model {

  implicit val encodeDateTime: Encoder[DateTime] = Encoder.encodeString contramap ISODateTimeFormat.basicDateTime().print
  implicit val decodeDateTime: Decoder[DateTime] = Decoder.decodeString emapTry { s =>
    Try { ISODateTimeFormat.basicDateTime().parseDateTime(s) }
  } or { Decoder.decodeLong.map(x => new DateTime(x * 1000L)) }

  implicit val encodeCurrencyName: Encoder[CurrencyName] = Encoder.encodeString.contramap(_.name.value)
  implicit val decodeCurrencyName: Decoder[CurrencyName] = Decoder.decodeString.map(s => CurrencyName(s.ci))

}
