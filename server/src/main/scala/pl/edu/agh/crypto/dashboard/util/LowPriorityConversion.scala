package pl.edu.agh.crypto.dashboard.util

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe._

trait LowPriorityConversion[F[_]] {
  implicit def fromCirceDecoder[T: Decoder](implicit ev: Sync[F]): EntityDecoder[F, T] = jsonOf[F, T]
  implicit def fromCirceEncoder[T: Encoder](implicit ev: Sync[F], enc: EntityEncoder[F, String]): EntityEncoder[F, T] = jsonEncoderOf[F, T]
}
