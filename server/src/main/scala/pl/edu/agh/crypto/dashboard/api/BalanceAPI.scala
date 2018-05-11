package pl.edu.agh.crypto.dashboard.api

import cats.Monad
import cats.effect.Effect
import org.http4s.rho.RhoService
import org.http4s.rho.bits.{FailureResponse, ResultResponse, StringParser, SuccessResponse}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.util.CaseInsensitiveString
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry}

import scala.reflect.runtime.universe
import org.http4s.implicits._
import pl.edu.agh.crypto.dashboard.service.{DataServiceProvider, TradingDataService}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.circe._

class BalanceAPI[F[+ _] : Effect](
  beginning: DateTime,
  supportedCurrencies: Set[CaseInsensitiveString],
  dataServices: DataServiceProvider[F, BasicDataSource[F]],
  commonParsers: CommonParsers[F]
) extends RhoService[F] with SwaggerSyntax[F] {

  implicit def listEncoder[T: Encoder]: EntityEncoder[F, List[T]] = jsonEncoderOf[F, List[T]]

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

  private def currencyName(id: String, desc: String) = pathVar[CurrencyName](id, desc)

  private val queryFrom = paramD[Option[DateTime]](
    "from",
    "Start of the query period, in format: yyyy-MM-dd'T'HH:mm:ssZZ",
  )
  private val queryTo = paramD[Option[DateTime]](
    "to",
    "End of the query period, in format: yyyy-MM-dd'T'HH:mm:ssZZ",
  )

  "Get exchange rates for the specific currency, against some other in the given time period" **
    GET / "rates" / "of" / currencyName("queried-currency","Name of the queried currency") /
    "for" / currencyName("compared-currency", "Name of the compared currency") +?
    (queryFrom & queryTo) |>> { (currency: CurrencyName, base: CurrencyName, from: Option[DateTime], to: Option[DateTime]) =>

    def queryData(dataService: TradingDataService[F, BasicDataSource[F]]) = (from, to) match {
      case (Some(f), Some(t)) => dataService.getDataOf[PricePairEntry](Set(base), f, t) flatMap { Ok(_) }
      case (Some(f), None) => dataService.getDataOf[PricePairEntry](Set(base), f) flatMap { Ok(_) }
      case (None, None) => dataService.getDataOf[PricePairEntry](Set(base)) flatMap { Ok(_) }
      case _ => BadRequest("Cannot specify only `to` query limit (need to provide `from`)")
    }

    for {
      ds <- dataServices.getTradingDataService(currency)
      resp <- queryData(ds)
    } yield resp
  }

}
