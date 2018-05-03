package pl.edu.agh.crypto.dashboard

import fs2.StreamApp
import monix.eval.Task
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.util.CaseInsensitiveString
import org.http4s.implicits._
import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.api.{BalanceAPI, CommonParsers}
import monix.execution.Scheduler.Implicits.global
import org.http4s.server.blaze.BlazeBuilder

object Launcher {

  CaseInsensitiveString

  class App extends StreamApp[Task] {

    private val swaggerSupport = SwaggerSupport.apply[Task]
    private val middleware = swaggerSupport.createRhoMiddleware()

    lazy val balanceAPI = new BalanceAPI[Task](
      DateTime.parse("2018-05-01T00:00:01"),
      Set("BTC".ci, "ETH".ci),
      new CommonParsers[Task] {}
    )

    override def stream(args: List[String], requestShutdown: Task[Unit]): fs2.Stream[Task, StreamApp.ExitCode] =
      BlazeBuilder[Task]
      .bindHttp(7077, "0.0.0.0")
      .mountService(balanceAPI.toService(middleware))
      .serve
  }

  def main(args: Array[String]): Unit = {
    val app = new App
    app.main(args)
  }

}
