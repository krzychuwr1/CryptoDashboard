package pl.edu.agh.crypto.dashboard.util

import shapeless._

trait Find[L <: HList, A] {
  def get(l: L): A
}

object Find {

  implicit def hconsFound[A, H, T <: HList](implicit ev: H =:= A) = new Find[H :: T, A] {
    override def get(l: H :: T): A = l.head
  }

  implicit def hconsNotFound[A, H, T <: HList](implicit f: Find[T, A]) = new Find[H :: T, A] {
    def get(l: H :: T): A = f.get(l.tail)
  }
}
