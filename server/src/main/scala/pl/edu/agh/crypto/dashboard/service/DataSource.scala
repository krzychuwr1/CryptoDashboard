package pl.edu.agh.crypto.dashboard.service

import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.CurrencyName

trait DataSource[F[_], T] {
  def getDataOf(toSymbols: Set[CurrencyName], from: Option[DateTime], to: Option[DateTime]): F[Map[CurrencyName, T]]
}
