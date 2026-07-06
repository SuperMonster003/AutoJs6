package org.autojs.autojs.inrt

import android.os.SystemClock
import kotlin.math.max

object InrtToastVisibilityGuard {

    @Volatile
    private var holdUntilElapsedMs = 0L

    fun holdForToast(isLong: Boolean) {
        val durationMs = if (isLong) LONG_TOAST_VISIBLE_MS else SHORT_TOAST_VISIBLE_MS
        holdUntilElapsedMs = max(holdUntilElapsedMs, SystemClock.elapsedRealtime() + durationMs)
    }

    fun remainingHoldMs(): Long {
        return (holdUntilElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private const val SHORT_TOAST_VISIBLE_MS = 2_400L
    private const val LONG_TOAST_VISIBLE_MS = 3_900L
}
