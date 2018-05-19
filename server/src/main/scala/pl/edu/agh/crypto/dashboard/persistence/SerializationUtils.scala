package pl.edu.agh.crypto.dashboard.persistence

import SerializationUtils._
import io.circe.{Decoder, Encoder, Json, JsonNumber}

trait SerializationUtils {

  implicit def toDecoderExtension(jsString: String): DecoderExtension = new DecoderExtension(jsString)
  implicit def toEncoderExtension[T](elem: T): EncoderExtension[T] = new EncoderExtension(elem)
  implicit def toObjectToMapExtension2[T](elem: T): ObjectToMapExtension2[T] = new ObjectToMapExtension2(elem)

}

object SerializationUtils {
  import io.circe.jawn.parse
  import scala.collection.JavaConverters._

  class DecoderExtension(private val jsString: String) extends AnyVal {

    def deserializeTo[T: Decoder]: Either[Throwable, T] = for {
      json <- parse(jsString)
      value <- json.as[T]
    } yield value

  }

  class EncoderExtension[T](private val elem: T) extends AnyVal {
    def toJson(implicit ev: Encoder[T]): Json = ev(elem)
  }

  class ObjectToMapExtension2[T](private val obj: T) extends AnyVal {
    def toJMap(implicit encoder: Encoder[T]): Any = {
      val json = encoder(obj)
      jsonToJavaRepr(json)
    }

    private def jsonToJavaRepr(json: Json): Any = {
      json.asArray.map(a => (a :+ Json.Null).map(jsonToJavaRepr).asJava)
        .orElse(json.asObject.map(_.toMap.mapValues(jsonToJavaRepr).asJava))
        .orElse(json.asString)
        .orElse(json.asBoolean)
        .orElse(json.asNumber.map(encodeNumber))
        .getOrElse(json.asNull.get)
    }

    private def encodeNumber(number: JsonNumber): Any = {
      number.toShort
        .orElse(number.toInt)
        .orElse(number.toLong)
        .orElse(number.toBigInt)
        /*.orElse(number.toBigDecimal) -- introducing strange behaviour, has to default to double value*/
        .getOrElse(number.toDouble)
    }



  }
}