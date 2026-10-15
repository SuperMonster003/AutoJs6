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
        get() {
            val sides = snapshot()
            val turn = rotation()
            return if (turn == ROTATION_90 || turn == ROTATION_270) sides.long else sides.short
        }

    val height: Int
        get() {
            val sides = snapshot()
            val turn = rotation()
            return if (turn == ROTATION_90 || turn == ROTATION_270) sides.short else sides.long
        }
}
