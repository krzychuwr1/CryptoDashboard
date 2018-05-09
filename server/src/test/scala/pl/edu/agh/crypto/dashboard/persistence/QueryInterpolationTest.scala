package pl.edu.agh.crypto.dashboard.persistence

import org.scalatest.{FlatSpec, Matchers}

class QueryInterpolationTest extends FlatSpec with Matchers with QueryInterpolation with QueryParameter.Syntax {

  "Interpolator" should "work as regular interpolator" in {
    val x = "a"
    val y = "b"
    val query = aql"FOR $x IN $y"
    query.parameters shouldBe empty
    query.code should equal(s"FOR $x IN $y")
  }

  it should "name parameters correctly" in {
    val query =
      aql"FOR doc IN ${bindCollection("test")} FILTER doc.v < ${bind(123)} RETURN doc"
    query.parameters("@var_1") should equal("test")
    query.parameters("var_2") should equal(123)
  }

  it should "remove duplicates" in {
    case class MyClass(x: Int, y: Int)
    import io.circe.generic.auto._
    val query =
      aql"""UPSERT { '_key': ${bindKey("lama") }
        |INSERT ${bind(MyClass(3, 4))}
        |UPDATE ${bind(MyClass(3, 4))} IN ${bindCollection("my-collection")}
      """.stripMargin

    println(query.parameters)
    true shouldBe true
  }

}
