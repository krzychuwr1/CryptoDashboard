package pl.edu.agh.crypto.dashboard

import io.circe.KeyEncoder
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource

package object api {
  import shapeless.{::, HNil}
  type BasicDataSource[F[_]] = DataSource[F, PricePairEntry] :: DataSource[F, TradingInfoEntry] :: HNil

  implicit val ciStringKeyEncoder: KeyEncoder[CurrencyName] = KeyEncoder.encodeKeyString.contramap(_.name.value)
}
