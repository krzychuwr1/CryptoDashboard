package pl.edu.agh.crypto.dashboard.api

import io.circe.Encoder

case class DataTypeEntry[T](key: String, description: String, encoder: Encoder[T])
