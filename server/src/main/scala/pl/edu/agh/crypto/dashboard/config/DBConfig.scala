package pl.edu.agh.crypto.dashboard.config

import cats.effect.Effect
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.monadError._
import cats.syntax.applicative._
import com.arangodb.{ArangoDBAsync, ArangoDatabaseAsync}
import io.circe.{Decoder, Encoder}
import pl.edu.agh.crypto.dashboard.model.Currency
import pl.edu.agh.crypto.dashboard.persistence.{GraphDefinition, PersistentDataService}
import pl.edu.agh.crypto.dashboard.service.DataService

import scala.collection.JavaConverters._
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import shapeless.PolyDefns.~>

trait DBConfig extends ApplyFromJava.Syntax {

  def dbName: String
  def config: ApplicationConfig

  lazy val dbAsync: ArangoDBAsync = {
    val b = new ArangoDBAsync.Builder()
    b.host(config.dbHost, config.dbPort)
    b.build()
  }

  def dataBaseAsync[F[_]: Effect: ApplyFromJava]: F[ArangoDatabaseAsync] = {
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

  def dataService[F[_]: Effect: ApplyFromJava, T: Encoder: Decoder](
    graphDefinition: GraphDefinition[Currency, T],
    memoize: F ~> F
  ): F[DataService[F, T]] =
    for {
      db <- dataBaseAsync[F]
      service <- PersistentDataService.create[F, T](db, graphDefinition)(memoize)
    } yield service

}
