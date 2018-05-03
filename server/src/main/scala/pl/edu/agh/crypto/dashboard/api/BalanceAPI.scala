package pl.edu.agh.crypto.dashboard.api

import cats.Monad
import cats.effect.Effect
import org.http4s.rho.RhoService
import org.http4s.rho.bits.{FailureResponse, ResultResponse, StringParser, SuccessResponse}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.util.CaseInsensitiveString
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.CurrencyName

import scala.reflect.runtime.universe
import org.http4s.implicits._

class BalanceAPI[F[+_]: Effect](
  beginning: DateTime,
  supportedCurrencies: Set[CaseInsensitiveString],
  commonParsers: CommonParsers[F]
) extends RhoService[F] with SwaggerSyntax[F] {
  import commonParsers._

  private implicit val currencyParser: StringParser[F, CurrencyName] = new StringParser[F, CurrencyName] {
    override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, CurrencyName] = {
      if (supportedCurrencies contains s.ci) SuccessResponse(CurrencyName(s.ci))
      else FailureResponse.pure(
        BadRequest.pure(s"Unknown/unsupported currency $s")
      )
    }

    override def typeTag: Option[universe.TypeTag[CurrencyName]] = Some(implicitly[universe.TypeTag[CurrencyName]])
  }

  //found bug of the compiler, _.isBeforeNow should be a correct closure, according to scala language specification
  private def isBeforeNow(dt: DateTime) = dt.isBeforeNow
  private def isAfterNow(dt: DateTime) = dt.isAfterNow

  "Get balance of the specific currency, in given time period" **
  GET / "balance" / pathVar[CurrencyName]("currency", "Name of the queried currency") +?
    (param[DateTime]("from", beginning, isBeforeNow(_)) &
      param[DateTime]("to", DateTime.now(), isAfterNow(_))) |>> { (currency: CurrencyName, from: DateTime, to: DateTime) =>
      Ok(s"query: ${currency.name}, $from, $to")
    }

}
