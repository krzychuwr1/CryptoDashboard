package pl.edu.agh.crypto.dashboard.persistence

import pl.edu.agh.crypto.dashboard.persistence.EdgeVertex.ModificationAction


case class EdgeVertex[T](
  value: T,
  action: ModificationAction
)

object EdgeVertex {

  sealed trait ModificationAction
  case object DoNothing extends ModificationAction
  case object Upsert extends ModificationAction

  implicit class EdgeVertexOps[T](private val t: T) extends AnyVal {

    def upsert: EdgeVertex[T] = EdgeVertex(
      t,
      Upsert
    )

    def doNothing: EdgeVertex[T] = EdgeVertex(
      t,
      DoNothing
    )

  }

}
