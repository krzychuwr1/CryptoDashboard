package pl.edu.agh.crypto.dashboard.persistence

case class Query(code: String, parameters: Map[String, Any]) {
  def boxParameters: Map[String, AnyRef] = parameters.collect {
    case (k, b: Byte) => k -> Byte.box(b)
    case (k, b: Boolean) => k -> Boolean.box(b)
    case (k, s: Short) => k -> Short.box(s)
    case (k, i: Int) => k -> Int.box(i)
    case (k, l: Long) => k -> Long.box(l)
    case (k, f: Float) => k -> Float.box(f)
    case (k, d: Double) => k -> Double.box(d)
    case (k, v: AnyRef) => k -> v
  }

  def stripMargin: Query = copy(code = code.stripMargin)
}