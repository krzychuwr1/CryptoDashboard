package pl.edu.agh.crypto.dashboard.model

import io.circe.generic.JsonCodec
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.persistence.Connectable

@JsonCodec case class PriceInfo(
  when: DateTime,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal
) {
  def key: String = s"${fromSymbol.name.value}-${toSymbol.name.value}-${when.getMillis}"
}

object PriceInfo {
  implicit val connectable: Connectable[PriceInfo] = { p => Edge(p.when, p.toSymbol) }
}
