package pl.edu.agh.crypto.dashboard.persistence

import io.circe.{Encoder, KeyEncoder}

sealed trait QueryParameter
object QueryParameter {

  trait Syntax extends SerializationUtils {
    implicit def stringToParameter(s: String): StringParameter = StringParameter(s)
    def bind[T: Encoder](elem: T): Variable = Variable(elem.toJMap)
    def bindKey[T: KeyEncoder](elem: T): Variable = Variable(KeyEncoder[T].apply(elem))
    def bindCollection(s: String) = Collection(s)
  }

  sealed trait BoundParameter extends QueryParameter

  case class Variable(value: Any) extends BoundParameter
  case class Collection(value: String) extends BoundParameter
  case class StringParameter(name: String) extends QueryParameter

}