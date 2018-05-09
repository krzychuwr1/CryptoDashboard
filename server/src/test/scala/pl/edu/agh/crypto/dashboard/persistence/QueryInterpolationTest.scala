package pl.edu.agh.crypto.dashboard.persistence

import org.scalatest.{FlatSpec, Matchers}

class QueryInterpolationTest extends FlatSpec with Matchers
  with QueryInterpolation with QueryParameter.Syntax with SerializationUtils {

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

  it should "correctly serialize json object (java map, string to any)" in {
    case class MyClass(x: Int, y: Int)
    import io.circe.generic.auto._
    import java.util.{Map => JMap}
    val query = aql"${bind(MyClass(3, 4))}"

    query.parameters("var_1") should matchPattern {
      case m: JMap[_, _] if m.get("x") == 3 && m.get("y") == 4 =>
    }
  }

  it should "remove duplicates" in {
    case class MyClass(x: Int, y: Int)
    import io.circe.generic.auto._

    val query = aql"""UPSERT { '_key': ${bindKey("lama") }
         |INSERT ${bind(MyClass(3, 4))}
         |UPDATE ${bind(MyClass(3, 4))} IN ${bindCollection("my-collection")}
      """.stripMargin

    query.parameters.size should equal(3)
  }

}
