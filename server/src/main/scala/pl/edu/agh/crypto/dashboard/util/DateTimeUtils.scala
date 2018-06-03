package pl.edu.agh.crypto.dashboard.util

import java.time.{Instant, ZoneId, ZonedDateTime}

import org.joda.time.DateTime
import pl.edu.agh.crypto.dashboard.util.DateTimeUtils.DateTimeExtension

trait DateTimeUtils {
  implicit def dateTimeExtension(dateTime: DateTime): DateTimeExtension = new DateTimeExtension(dateTime)
}

object DateTimeUtils {

  class DateTimeExtension(private val dateTime: DateTime) extends AnyVal {
    def dateTimeToZonedTime: ZonedDateTime = {
      val instant = Instant.ofEpochMilli(dateTime.getMillis)
      val zoneId = ZoneId.of(dateTime.getZone.getID, ZoneId.SHORT_IDS)
      ZonedDateTime.ofInstant(instant, zoneId)
    }
  }
}
