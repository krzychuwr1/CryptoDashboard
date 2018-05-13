package pl.edu.agh.crypto.dashboard.api

import cats.Monad
import cats.effect.Effect
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import org.http4s.implicits._
import org.http4s.rho.RhoService
import org.http4s.rho.bits.{ResultResponse, StringParser, SuccessResponse}
import org.http4s.rho.swagger.SwaggerSyntax
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.config.DataTypeEntry
import pl.edu.agh.crypto.dashboard.model.CurrencyName
import pl.edu.agh.crypto.dashboard.service.DataService
import shapeless._

import scala.reflect.runtime.universe

abstract class GenericBalanceAPI[F[+_], Services <: HList](
  service: Services,
  commonParsers: CommonParsers[F]
)(implicit
  ef: Effect[F]
) extends RhoService[F] with SwaggerSyntax[F] {

  import commonParsers._

  implicit def listEncoder[T: Encoder]: EntityEncoder[F, List[T]] = jsonEncoderOf[F, List[T]]

  implicit def mapEncoder[T: Encoder]: EntityEncoder[F, Map[CurrencyName, T]] = jsonEncoderOf[F, Map[CurrencyName, T]]

  private implicit val currencyParser: StringParser[F, CurrencyName] = new StringParser[F, CurrencyName] {
    override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, CurrencyName] = {
      SuccessResponse(CurrencyName(s.ci))
    }

    override def typeTag: Option[universe.TypeTag[CurrencyName]] = Some(implicitly[universe.TypeTag[CurrencyName]])
  }

  private def currencyName(id: String, desc: String) = pathVar[CurrencyName](id, desc)

  private val queryFrom = paramD[Option[DateTime]](
    "from",
    "Start of the query period, in format: yyyy-MM-dd'T'HH:mm:ssZZ",
  )
  private val queryTo = paramD[Option[DateTime]](
    "to",
    "End of the query period, in format: yyyy-MM-dd'T'HH:mm:ssZZ",
  )

  object routes extends Poly1 {

    implicit def allCases[T: Encoder] =
      at[(DataTypeEntry[T], DataService[F, T])] {
        case (elem, service) =>
          val key = elem.key
          val description = elem.description
          description **
            GET / key / "of" / currencyName("queried-currency", "Name of the queried currency") /
            "for" / currencyName("compared-currency", "Name of the compared currency") +?
            (queryFrom & queryTo) |>> { (currency: CurrencyName, base: CurrencyName, from: Option[DateTime], to: Option[DateTime]) =>

            for {
              source <- service.getDataSource(currency)
              result <- source.getDataOf(Set(base), from, to)
              resp <- Ok(result)
            } yield resp
          }
      }

  }

}
