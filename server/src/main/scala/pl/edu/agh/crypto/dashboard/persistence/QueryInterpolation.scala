package pl.edu.agh.crypto.dashboard.persistence

import QueryInterpolation._
import QueryParameter._

trait QueryInterpolation {
  implicit def toExtendedQueryInterpolator(sc: StringContext): QueryInjectionExtension = new QueryInjectionExtension(sc)
}

object QueryInterpolation {

  class QueryInjectionExtension(private val sc: StringContext) extends AnyVal {
    def aql(args: QueryParameter*): Query = {

      def withoutDuplicates(params: List[BoundParameter]): Map[BoundParameter, String] = {
        import scala.collection.mutable.{Map => MMap}
        val b = MMap.empty[BoundParameter, String]
        def iter(maxIndex: Int, elems: List[BoundParameter]): Map[BoundParameter, String] = elems match {
          case param :: tail if !b.contains(param) =>
            param match {
              case v: Variable =>
                b.put(v, s"var_$maxIndex")
              case c: Collection =>
                b.put(c, s"@var_$maxIndex")
            }
            iter(maxIndex + 1, tail)
          case _ :: tail =>
            iter(maxIndex, tail)
          case Nil =>
            b.toMap
        }
        iter(1, params)
      }

      val paramNames: Map[BoundParameter, String] = withoutDuplicates {
        args.collect {
          case v: BoundParameter => v
        }.toList
      }

      val params = paramNames map {
        case (Variable(v), n) => n -> v
        case (Collection(c), n) => n -> c
      }

      def fillVariable(variable: String): String = s"@$variable"

      val flattened = args map {
        case b: BoundParameter =>
          fillVariable(paramNames(b))
        case StringParameter(s) =>
          s
      }
      
      Query(sc.s(flattened:_*), params)
    }
  }

}