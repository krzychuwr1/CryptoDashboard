package pl.edu.agh.crypto.dashboard.model

import io.circe.{KeyDecoder, KeyEncoder}
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString

case class CurrencyName(name: CaseInsensitiveString) extends AnyVal

object CurrencyName {

  trait KeyCodecs {
    implicit val ciStringKeyEncoder: KeyEncoder[CurrencyName] = KeyEncoder.encodeKeyString.contramap(_.name.value)
    implicit val cnStringKeyDecoder: KeyDecoder[CurrencyName] = KeyDecoder.decodeKeyString.map(s => CurrencyName(s.ci))
  }

}
