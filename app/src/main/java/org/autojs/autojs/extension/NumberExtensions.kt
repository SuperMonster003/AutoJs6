package org.autojs.autojs.extension

object NumberExtensions {

    val Number.string
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
