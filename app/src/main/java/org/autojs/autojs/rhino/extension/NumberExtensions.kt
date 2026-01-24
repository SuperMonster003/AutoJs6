package org.autojs.autojs.rhino.extension

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

}