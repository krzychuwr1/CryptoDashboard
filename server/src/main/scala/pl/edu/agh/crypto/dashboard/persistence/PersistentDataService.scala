package pl.edu.agh.crypto.dashboard.persistence

import cats.effect.Effect
import cats.syntax.functor._
import com.arangodb.ArangoDatabaseAsync
import com.arangodb.model.{AqlQueryOptions, SkiplistIndexOptions}
import io.circe.{Decoder, Encoder}
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.model._
import pl.edu.agh.crypto.dashboard.service._
import pl.edu.agh.crypto.dashboard.util.ApplyFromJava
import shapeless.PolyDefns.~>

class PersistentDataService[F[_]: Effect: ApplyFromJava, T: Encoder: Decoder] private(
  dbAsync: ArangoDatabaseAsync,
  graph: GraphDefinition[Currency, T],
  memoizeOnSuccess: F ~> F
) extends GraphQueries[F, Currency, T, Edge](
  dbAsync,
  graph
) with DataService[F, T] {

  private class SpecializedDataSource(currency: Currency) extends DataSource[F, T] {

    override def getDataOf(
      toSymbols: Set[CurrencyName],
      from: Option[DateTime],
      to: Option[DateTime]
    ): F[List[T]] = {

      val firstOperator = from.fold("")(_ => "&&")
      val secondOperator = from.fold("")(_ => "&&")

      dbAsync.executeQuery(
        aql"""
             |FOR v, e IN [1..1] OUTBOUND ${bindKey(graph.fromID(currency))} GRAPH ${graph.name}
             | FILTER ${"e.to" in toSymbols} $firstOperator ${from.map("e._key" |>=| _)} $secondOperator ${to.map("e._key" |<=| _)}
             | RETURN v
      """.stripMargin,
        new AqlQueryOptions()
      )
    }

  }

  /**
    * Checks if the given currency is supported, if it is the case, returns data source for it
    * @param currency name of the currency to support
    * @return data source for the specific currency (memoized)
    */
  override def getDatSource(
    currency: CurrencyName
  ): F[DataSource[F, T]] = {
    val sourceRaw = keyValueQueries.getRaw[Currency](graph.fromCollection)(currency.name.value)
      .map(new SpecializedDataSource(_))
      .widen[DataSource[F, T]]
    memoizeOnSuccess(sourceRaw)
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
  def create[F[_]: Effect: ApplyFromJava, T: Encoder: Decoder](
    dbAsync: ArangoDatabaseAsync,
    graph: GraphDefinition[Currency, T],
    edgeIndexes: IndexDefinition*
  )(
    memoization: F ~> F//memoization of the effect, in case of success
  ): F[DataService[F, T]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    import scala.collection.JavaConverters._

    val indexResult = edgeIndexes.toList traverse {
      case IndexDefinition(field, unique) =>
        val deferredIndex = dbAsync.collection(graph.edgeCollection)
        .ensureSkiplistIndex(Iterable(field).asJava, new SkiplistIndexOptions().unique(unique))
        .defer
        memoization(deferredIndex)
    }

    indexResult.map(_ => new PersistentDataService(dbAsync, graph, memoization)).widen[DataService[F, T]]
  }

}
