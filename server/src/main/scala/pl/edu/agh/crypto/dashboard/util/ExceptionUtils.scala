package pl.edu.agh.crypto.dashboard.util

import scala.util.Try

trait ExceptionUtils {

  def enrichedStackTrace[T <: Throwable](
    t: T
  )(implicit
    enclosing: sourcecode.Enclosing,
    name: sourcecode.Name,
    file: sourcecode.File,
    line: sourcecode.Line
  ): T = ExceptionUtils.enrichedStackTrace(t)

}

object ExceptionUtils {

  def enrichedStackTrace[T <: Throwable](
    t: T
  )(implicit
    enclosing: sourcecode.Enclosing,
    name: sourcecode.Name,
    file: sourcecode.File,
    line: sourcecode.Line
  ): T = {
    val classFile = Try { file.value.split("""\\|/""").last } getOrElse file.value
    val enrichedST = new StackTraceElement(enclosing.value, name.value, classFile, line.value) +: t.getStackTrace
    t.setStackTrace(enrichedST)
    t
  }
}
