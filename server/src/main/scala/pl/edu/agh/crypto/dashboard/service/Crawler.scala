package pl.edu.agh.crypto.dashboard.service

trait Crawler[S[_], T] {
  def stream: S[T]
}
