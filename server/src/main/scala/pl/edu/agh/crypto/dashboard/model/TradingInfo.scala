package pl.edu.agh.crypto.dashboard.model

import io.circe.generic.JsonCodec
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.persistence.Connectable

@JsonCodec case class TradingInfo(
  when: DateTime,
  fromSymbol: CurrencyName,
  toSymbol: CurrencyName,
  price: BigDecimal,
  supply: BigInt,
  marketCap: BigDecimal
) {
  def key: String = s"${fromSymbol.name.value}-${toSymbol.name.value}-${when.getMillis}"
}

object TradingInfo {
  implicit val connectable: Connectable[TradingInfo] = { ti => Edge(ti.when, ti.toSymbol) }
}
