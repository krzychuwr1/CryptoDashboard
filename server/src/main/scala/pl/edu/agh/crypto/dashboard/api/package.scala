package pl.edu.agh.crypto.dashboard

import io.circe.{KeyDecoder, KeyEncoder}
import org.http4s.util.CaseInsensitiveString
import pl.edu.agh.crypto.dashboard.model.CurrencyName

package object api {

  implicit val ciStringKeyEncoder: KeyEncoder[CurrencyName] = KeyEncoder.encodeKeyString.contramap(_.name.value)
  implicit val cnStringKeyDecoder: KeyDecoder[CurrencyName] = KeyDecoder.decodeKeyString.map(s => CurrencyName(CaseInsensitiveString(s)))
}
