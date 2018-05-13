package pl.edu.agh.crypto.dashboard.config

import cats.effect.Effect
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.monadError._
import cats.syntax.applicative._
import com.arangodb.{ArangoDBAsync, ArangoDatabaseAsync}
import io.circe.{Decoder, Encoder}
import pl.edu.agh.crypto.dashboard.model.Currency
import pl.edu.agh.crypto.dashboard.persistence.{Connectable, GraphDefinition, PersistentDataService}
import pl.edu.agh.crypto.dashboard.service.DataService

import scala.collection.JavaConverters._
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import shapeless.PolyDefns.~>

abstract class DBConfig[F[_]: Effect: ApplyFromJava](
  val memoize: F ~> F,
  config: ApplicationConfig,
  dbName: String
) extends ApplyFromJava.Syntax {

  lazy val dbAsync: ArangoDBAsync = {
    val b = new ArangoDBAsync.Builder()
    b.host(config.dbHost, config.dbPort)
    b.build()
  }

  lazy val dataBaseAsync: F[ArangoDatabaseAsync] = memoize.apply[ArangoDatabaseAsync]{
    for {
      dbs <- dbAsync.getDatabases.defer
      databases = dbs.asScala.toSet
      _ <- if (databases contains dbName) {
        ().pure[F]
      } else {
        dbAsync.createDatabase(dbName)
          .defer
          .ensure(new Exception("Failed to initialize database"))(identity(_))
      }
    } yield dbAsync.db(dbName)
  }

  def dataService[T: Decoder: Encoder: Connectable](
    graphDefinition: GraphDefinition[Currency, T]
  ): F[DataService[F, T]] =
    for {
      db <- dataBaseAsync
      service <- PersistentDataService.create[F, T](db, graphDefinition)(memoize)
    } yield service

}
