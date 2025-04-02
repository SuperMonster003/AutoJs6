package org.autojs.autojs.extension

import java.math.RoundingMode

object NumberExtensions {

    val Number.jsString
        get() = when (this) {
            is Double -> when {
                this % 1.0 == 0.0 -> "%.0f".format(this)
                else -> this.toString()
            }
            is Float -> when {
                this % 1.0f == 0.0f -> "%.0f".format(this)
                else -> this.toString()
            }
            else -> this.toString()
        }

    @JvmStatic
    @JvmOverloads
    fun Double.roundToString(scale: Int, stripTrailingZeros: Boolean = true): String {
        return toBigDecimal()
            .setScale(scale, RoundingMode.HALF_UP)
            .let { if (stripTrailingZeros) it.stripTrailingZeros() else it }
            .toPlainString()
    }

}
