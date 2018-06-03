package pl.edu.agh.crypto.dashboard.util

import org.ta4j.core.Decimal
import pl.edu.agh.crypto.dashboard.util.DecimalUtils.BigDecimalConversions

trait DecimalUtils {
  implicit def toBigDecimalConversions(bd: BigDecimal): BigDecimalConversions = new BigDecimalConversions(bd)
}

object DecimalUtils {

  class BigDecimalConversions(private val bd: BigDecimal) extends AnyVal {
    def toTa4j: Decimal = Decimal.valueOf(bd)
  }

}
