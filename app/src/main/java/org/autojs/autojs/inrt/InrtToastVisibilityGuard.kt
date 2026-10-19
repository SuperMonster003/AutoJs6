package org.autojs.autojs.inrt

import android.os.SystemClock
import kotlin.math.max

object InrtToastVisibilityGuard {

    private var toastHoldUntilElapsedMs = 0L
    private var potentialToastHoldUntilElapsedMs = 0L

    @Synchronized
    fun holdForPotentialRuntimeBridgeToast() {
        potentialToastHoldUntilElapsedMs = max(
            potentialToastHoldUntilElapsedMs,
            SystemClock.elapsedRealtime() + POTENTIAL_RUNTIME_BRIDGE_TOAST_MS,
        )
    }

    @Synchronized
    fun holdForToast(isLong: Boolean) {
        val durationMs = if (isLong) LONG_TOAST_VISIBLE_MS else SHORT_TOAST_VISIBLE_MS
        potentialToastHoldUntilElapsedMs = 0L
        toastHoldUntilElapsedMs = max(toastHoldUntilElapsedMs, SystemClock.elapsedRealtime() + durationMs)
    }

    @Synchronized
    fun remainingHoldMs(): Long {
        val holdUntilElapsedMs = max(toastHoldUntilElapsedMs, potentialToastHoldUntilElapsedMs)
        return (holdUntilElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private const val POTENTIAL_RUNTIME_BRIDGE_TOAST_MS = 12_000L
    private const val SHORT_TOAST_VISIBLE_MS = 3_200L
    private const val LONG_TOAST_VISIBLE_MS = 4_800L
}
