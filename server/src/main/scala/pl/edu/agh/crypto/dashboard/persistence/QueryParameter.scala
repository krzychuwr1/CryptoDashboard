package pl.edu.agh.crypto.dashboard.persistence

import io.circe.{Encoder, KeyEncoder}

sealed trait QueryParameter
object QueryParameter extends SerializationUtils {

  trait Syntax {
    implicit def stringToParameter(s: String): StringParameter = StringParameter(s)
    implicit def stringToExpressionOps(field: String): ExpressionOps = new ExpressionOps(field)
    implicit def optionBinding(optParam: Option[QueryParameter]): QueryParameter = optParam match {
      case Some(p) => p
      case None => StringParameter("")
    }

    def bind[T: Encoder](elem: T): Variable = Variable(elem.toJMap)
    def bindKey[T: KeyEncoder](elem: T): Variable = Variable(KeyEncoder[T].apply(elem))
    def bindCollection(s: String) = Collection(s)
  }

  sealed trait BoundParameter extends QueryParameter

  case class Variable(value: Any) extends BoundParameter
  case class Collection(value: String) extends BoundParameter
  case class StringParameter(name: String) extends QueryParameter
  case class Expression(field: String, op: String, value: Variable) extends QueryParameter

  class ExpressionOps(private val field: String) extends AnyVal {

    def |==|[T: Encoder](v: T) = Expression(field, "==", Variable(v.toJMap))

    private def orderingOps[T: Encoder](
      v: T,
      op: String
    ) = Expression(field, op, Variable(v.toJMap))

    def |<|[T: Encoder](v: T): Expression =
      orderingOps(v, "<")

    def |<=|[T: Encoder](v: T): Expression =
      orderingOps(v, "<=")

    def |>=|[T: Encoder](v: T): Expression =
      orderingOps(v, ">=")

    def |>|[T: Encoder](v: T): Expression =
      orderingOps(v, ">")

    def in[V: Encoder](v: Iterable[V]): Expression =
      Expression(field, "IN", Variable(v.toJMap))

  }

}