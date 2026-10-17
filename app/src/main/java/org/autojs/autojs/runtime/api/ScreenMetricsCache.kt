package org.autojs.autojs.runtime.api

import android.view.Surface.ROTATION_90
import android.view.Surface.ROTATION_270

/** Publishes both screen sides together and keeps each read valid across an Activity reset. */
internal class ScreenMetricsCache(
    private val measure: () -> Sides,
    private val rotation: () -> Int,
) {
    data class Sides(val short: Int, val long: Int) {
        init {
            require(short > 0 && long >= short) { "Screen dimensions must be positive and ordered" }
        }
    }

    data class Dimensions(val width: Int, val height: Int, val rotation: Int)

    @Volatile
    private var cached: Sides? = null

    fun snapshot(): Sides = cached ?: synchronized(this) {
        cached ?: measure().also { cached = it }
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    val width: Int
        get() = dimensions().width

    val height: Int
        get() = dimensions().height

    /** A single rotation read keeps both dimensions coherent while the device turns. */
    fun dimensions(): Dimensions {
        val sides = snapshot()
        val turn = rotation()
        return if (turn == ROTATION_90 || turn == ROTATION_270) {
            Dimensions(sides.long, sides.short, turn)
        } else {
            Dimensions(sides.short, sides.long, turn)
        }
    }
}
