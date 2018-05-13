package pl.edu.agh.crypto.dashboard.persistence

import pl.edu.agh.crypto.dashboard.model.Edge

trait Connectable[T] {
  def connect(t: T): Edge
}

object Connectable {

  trait Syntax {
    implicit def ops[T](t: T): ConnectableOps[T] = new ConnectableOps(t)
  }

  class ConnectableOps[T](private val t: T) extends AnyVal {
    @inline def connect(implicit c: Connectable[T]): Edge = c.connect(t)
  }

}
