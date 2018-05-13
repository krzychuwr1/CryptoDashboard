package pl.edu.agh.crypto.dashboard.service

trait DataSink[F[_], T] {
  def saveData(data: T): F[Unit]
}
