package pl.edu.agh.crypto.dashboard.service

import pl.edu.agh.crypto.dashboard.model.CurrencyName

trait Crawler[S[_], T] {
  def stream: S[Map[CurrencyName, T]]
}
