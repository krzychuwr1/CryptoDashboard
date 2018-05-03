package pl.edu.agh.crypto.dashboard.api

import cats.Monad
import cats.effect.Effect
import org.http4s.rho.RhoService
import org.http4s.rho.bits._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import scala.reflect.runtime.universe
import scala.util.control.NonFatal

abstract class CommonParsers[F[+_]: Effect] extends RhoService[F] {

  implicit val dateTimeSP: StringParser[F, DateTime] = new StringParser[F, DateTime] {
    override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, DateTime] = {
      try {
        val df = ISODateTimeFormat.dateTimeNoMillis()
        SuccessResponse(df.parseDateTime(s))
      } catch {
        case NonFatal(_) =>
          FailureResponse.pure[F](
            BadRequest.pure("Illegal argument, expected correct date-time string, format: yyyyMMdd'T'HHmmssZ")
          )
      }
    }
    override def typeTag: Option[universe.TypeTag[DateTime]] =
      Some(implicitly[universe.TypeTag[DateTime]])
  }

}
