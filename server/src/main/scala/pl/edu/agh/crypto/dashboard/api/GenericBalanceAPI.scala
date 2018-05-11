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
import pl.edu.agh.crypto.dashboard.model.{CurrencyName, PricePairEntry, TradingInfoEntry}
import pl.edu.agh.crypto.dashboard.service.TradingDataService.DataSource
import pl.edu.agh.crypto.dashboard.service.{DataServiceProvider, TradingDataService}
import shapeless._

import scala.reflect.runtime.universe

abstract class GenericBalanceAPI[F[+_], Keys <: HList, DS <: HList](
  val keys: Keys,
  val dataSource: DataServiceProvider[F, DS],
  commonParsers: CommonParsers[F]
)(implicit
  ef: Effect[F]
) extends RhoService[F] with SwaggerSyntax[F] {
  import commonParsers._

  implicit def listEncoder[T: Encoder]: EntityEncoder[F, List[T]] = jsonEncoderOf[F, List[T]]

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
    import shapeless.ops.hlist.Selector

    implicit def allCases[T](implicit s: Selector[DS, DataSource[F, T]]) =
      at[DataTypeEntry[T]] { elem =>
        val DataTypeEntry(key, description, encoder) = elem
        implicit val e: Encoder[T] = encoder
        description **
        GET / key / "of" / currencyName("queried-currency","Name of the queried currency") /
          "for" / currencyName("compared-currency", "Name of the compared currency") +?
          (queryFrom & queryTo)  |>> { (currency: CurrencyName, base: CurrencyName, from: Option[DateTime], to: Option[DateTime]) =>

          def queryData(dataService: TradingDataService[F, DS]) = (from, to) match {
            case (Some(f), Some(t)) => dataService.getDataOf[T](Set(base), f, t) flatMap { Ok(_) }
            case (Some(f), None) => dataService.getDataOf[T](Set(base), f) flatMap { Ok(_) }
            case (None, None) => dataService.getDataOf[T](Set(base)) flatMap { Ok(_) }
            case _ => BadRequest("Cannot specify only `to` query limit (need to provide `from`)")
          }

          for {
            ds <- dataSource.getTradingDataService(currency)
            resp <- queryData(ds)
          } yield resp
        }
      }

  }

}

object GenericBalanceAPI {

  class BasicGenericAPI[F[+_]: Effect](
    ds: DataServiceProvider[F, BasicDataSource[F]],
    cp: CommonParsers[F]
  ) extends GenericBalanceAPI[F, DataTypeEntry[PricePairEntry] :: DataTypeEntry[TradingInfoEntry] :: HNil, BasicDataSource[F]](
    keys =
      DataTypeEntry[PricePairEntry]("rate", "exchange rates for the specific currency, against some other in the given time period", implicitly[Encoder[PricePairEntry]]) ::
        DataTypeEntry[TradingInfoEntry]("info", "complete information about specific currency pair", implicitly[Encoder[TradingInfoEntry]]) ::
        HNil,
    ds,
    cp
  ) {
    keys.map(routes)
  }



}
