package pl.edu.agh.crypto.dashboard.model

case class GraphDefinition[From, To](
  name: String,
  edgeCollection: String,
  fromCollection: String,
  toCollection: String,
  fromKey: From => String,
  toKey: To => String
) {

  def fromID(doc: From): String = s"$fromCollection/${fromKey(doc)}"
  def toID(doc: To): String = s"$toCollection/${toKey(doc)}"
}
