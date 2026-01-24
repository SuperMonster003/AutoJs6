package org.autojs.autojs.util

import java.math.RoundingMode

object NumberUtils {

    @JvmStatic
    @JvmOverloads
    fun Double.roundToString(scale: Int, stripTrailingZeros: Boolean = true): String {
        return toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .let { if (stripTrailingZeros) it.stripTrailingZeros() else it }
            .toPlainString()
    }

}