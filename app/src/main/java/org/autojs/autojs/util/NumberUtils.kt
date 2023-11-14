package org.autojs.autojs.util

@Suppress("unused")
object NumberUtils {

    @JvmStatic
    fun toElegantlyDoubleString(d: Double): String = when (d) {
        d.toLong().toDouble() -> String.format("%d", d.toLong())
        else -> String.format("%s", d)
    }

}