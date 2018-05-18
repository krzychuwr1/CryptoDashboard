package pl.edu.agh.crypto.dashboard.config

import com.typesafe.config.ConfigFactory
import org.log4s.getLogger
import pureconfig.{CamelCase, ConfigFieldMapping, ProductHint, loadConfig}

case class ApplicationConfig(
  dbHost: String,
  dbPort: Int,
  dbUser: String,
  dbPassword: String
)
object ApplicationConfig {
  private val logger = getLogger(classOf[ApplicationConfig])
  implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  def load(): ApplicationConfig = {
    val underlying = ConfigFactory.load(classOf[ApplicationConfig].getClassLoader)
    val res = loadConfig[ApplicationConfig](underlying)

    res.fold(
      fa = { errors =>
        logger.error(s"Failed to read config, encountered errors: ${errors.toList.map(_.description).mkString("\n","\n","")}")
        throw new IllegalStateException("Failed to load config")
      },
      fb = identity
    )
  }
}
