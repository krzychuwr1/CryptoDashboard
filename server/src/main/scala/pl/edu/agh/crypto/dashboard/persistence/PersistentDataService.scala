package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.instances.list._
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.{AqlQueryOptions, SkiplistIndexOptions}
import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model._
import pl.edu.agh.crypto.dashboard.service._
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import cats.~>

import scala.collection.concurrent.TrieMap

class PersistentDataService[F[_]: Effect: ApplyFromJava, T: Encoder: Decoder: Connectable] private(
  dbAsync: ArangoDatabaseAsync,
  graph: GraphDefinition[Currency, T],
  memoizeOnSuccess: F ~> F
) extends GraphQueries[F, Currency, T, Edge](
  dbAsync,
  graph
) with DataService[F, T] with Connectable.Syntax {
  import EdgeVertex._

  private type MyDs = DataSource[F, T]

  private[this] val dataSources: TrieMap[CurrencyName, F[MyDs]] = TrieMap.empty

  private def getMemoized(name: CurrencyName): F[MyDs] = {
    dataSources.getOrElseUpdate(
      name,
      memoizeOnSuccess(
        keyValueQueries.getRaw[Currency](graph.fromCollection)(name.name.value)
        .map(new SpecializedDataSource(_))
        .widen[DataSource[F, T]]
      )
    )
  }

  private class SpecializedDataSource(currency: Currency) extends DataSource[F, T] {

    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): F[Map[CurrencyName, T]] = {

      val firstOperator = from.fold("")(_ => "&&")
      val secondOperator = from.fold("")(_ => "&&")

      dbAsync.executeQuery[(Edge, T)](
        aql"""
             |FOR v, e IN [1..1] OUTBOUND ${bindKey(graph.fromID(currency))} GRAPH ${graph.name}
             | FILTER ${"e.to" in toSymbols} $firstOperator ${from.map("e._key" |>=| _)} $secondOperator ${to.map("e._key" |<=| _)}
             | RETURN [e, v]
      """.stripMargin,
        new AqlQueryOptions()
      ).map(_.iterator.map({ case (e, v) => e.to -> v }).toMap)
    }

  }

  private class SpecializedDataSink(currency: Currency) extends DataSink[F, T] {
    override def saveData(data: T): F[Unit] = {
      putEdge(currency.doNothing, data.upsert, Some(data.connect))
    }
  }

  /**
    * Checks if the given currency is supported, if it is the case, returns data source for it
    * @param currency name of the currency to support
    * @return data source for the specific currency (memoized)
    */
  override def getDataSource(
    currency: CurrencyName
  ): F[MyDs] =
    getMemoized(currency)

  override def getDataSink(currency: CurrencyName): F[DataSink[F, T]] = {
    val sinkRaw = keyValueQueries.getRaw[Currency](graph.fromCollection)(currency.name.value)
      .map(new SpecializedDataSink(_))
      .widen[DataSink[F,T]]
    memoizeOnSuccess(sinkRaw)
  }
}

object PersistentDataService extends ApplyFromJava.Syntax {

  /**
    * Creates a persistent data service,
    * ensures that all required indexes are existing
    * @param dbAsync connection to arango-db
    * @param graph - graph configuration
    * @param edgeIndexes - indexes to be created for edge (to optimise application)
    * @param memoization - natural transformation for memoisation
    * @tparam F - operating effect
    * @tparam T - supported types
    * @return Initialized DataService
    */
  def create[F[_]: Effect: ApplyFromJava, T: Encoder: Decoder: Connectable](
    dbAsync: ArangoDatabaseAsync,
    graph: GraphDefinition[Currency, T],
    supportedCurrencies: Set[Currency],
    edgeIndexes: IndexDefinition*
  )(
    memoization: F ~> F//memoization of the effect, in case of success
  ): F[DataService[F, T]] = {
    import scala.collection.JavaConverters._

    val indexResult = edgeIndexes.toList traverse {
      case IndexDefinition(field, unique) =>
        val deferredIndex = dbAsync.collection(graph.edgeCollection)
        .ensureSkiplistIndex(Iterable(field).asJava, new SkiplistIndexOptions().unique(unique))
        .defer
        memoization(deferredIndex)
    }

    val queries = new KeyValueQueries[F](dbAsync) {}

    val updateResult = supportedCurrencies.toList traverse { c =>
      queries.put(graph.fromCollection)(graph.fromKey(c), c)
    }

    for {
      _ <- indexResult
      _ <- updateResult
    } yield new PersistentDataService(dbAsync, graph, memoization) : DataService[F, T]

  }

}
