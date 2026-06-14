package org.autojs.autojs.ui.edit.editor.ace.health

import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

class AceRuntimeHealthMonitor(
    private val mainHandler: Handler,
    private val fallbackPolicy: AceFallbackPolicy = AceFallbackPolicy(),
    private val onFallbackRequired: (AceFailure) -> Unit,
) {

    @Volatile
    var state: AceHealthState = AceHealthState.CREATED
        private set

    @Volatile
    var lastFailure: AceFailure? = null
        private set

    @Volatile
    var lastReadyAtUptimeMillis: Long = 0L
        private set

    @Volatile
    var lastStateAtUptimeMillis: Long = 0L
        private set

    @Volatile
    var lastChangeAtUptimeMillis: Long = 0L
        private set

    @Volatile
    var lastHeartbeatAtUptimeMillis: Long = 0L
        private set

    private val fallbackStarted = AtomicBoolean(false)
    private var heartbeatCheck: (() -> Unit)? = null
    private var heartbeatFailures = 0

    private val readyTimeoutRunnable = Runnable {
        if (!isTerminal() && state != AceHealthState.RUNNING && state != AceHealthState.BRIDGE_READY) {
            reportFailure(
                AceFailure(
                    type = AceFailureType.READY_TIMEOUT,
                    message = "ACE editor did not report ready within ${READY_TIMEOUT_MS}ms",
                ),
            )
        }
    }

    private val heartbeatRunnable = Runnable {
        if (isTerminal() || state != AceHealthState.RUNNING) {
            return@Runnable
        }
        heartbeatCheck?.invoke()
        scheduleHeartbeat()
    }

    fun markLoadingPage() {
        transitionTo(AceHealthState.LOADING_PAGE)
        mainHandler.removeCallbacks(readyTimeoutRunnable)
        mainHandler.postDelayed(readyTimeoutRunnable, READY_TIMEOUT_MS)
    }

    fun markPageLoaded() {
        if (!isTerminal() && state == AceHealthState.LOADING_PAGE) {
            transitionTo(AceHealthState.PAGE_LOADED)
        }
    }

    fun markBridgeReady() {
        if (isTerminal()) {
            return
        }
        mainHandler.removeCallbacks(readyTimeoutRunnable)
        lastReadyAtUptimeMillis = SystemClock.uptimeMillis()
        transitionTo(AceHealthState.BRIDGE_READY)
        transitionTo(AceHealthState.RUNNING)
    }

    fun markStateEvent() {
        lastStateAtUptimeMillis = SystemClock.uptimeMillis()
    }

    fun markChangeEvent() {
        lastChangeAtUptimeMillis = SystemClock.uptimeMillis()
        markStateEvent()
    }

    fun startHeartbeat(check: () -> Unit) {
        heartbeatCheck = check
        heartbeatFailures = 0
        scheduleHeartbeat()
    }

    fun recordHeartbeatResult(ok: Boolean) {
        if (isTerminal()) {
            return
        }
        lastHeartbeatAtUptimeMillis = SystemClock.uptimeMillis()
        if (ok) {
            heartbeatFailures = 0
            if (state == AceHealthState.DEGRADED) {
                transitionTo(AceHealthState.RUNNING)
            }
            return
        }

        heartbeatFailures += 1
        if (heartbeatFailures >= HEARTBEAT_FALLBACK_FAILURES) {
            reportFailure(
                AceFailure(
                    type = AceFailureType.HEARTBEAT_TIMEOUT,
                    message = "ACE heartbeat failed $heartbeatFailures times",
                ),
            )
        } else if (heartbeatFailures >= HEARTBEAT_DEGRADED_FAILURES) {
            transitionTo(AceHealthState.DEGRADED)
        }
    }

    fun reportFailure(failure: AceFailure) {
        val enriched = failure.copy(state = state)
        lastFailure = enriched

        if (!fallbackPolicy.shouldFallback(enriched)) {
            if (!isTerminal()) {
                transitionTo(AceHealthState.DEGRADED)
            }
            return
        }

        if (!fallbackStarted.compareAndSet(false, true)) {
            return
        }
        transitionTo(AceHealthState.FALLING_BACK)
        mainHandler.post {
            onFallbackRequired(enriched)
        }
    }

    fun markDestroyed() {
        transitionTo(AceHealthState.DESTROYED)
        mainHandler.removeCallbacks(readyTimeoutRunnable)
        mainHandler.removeCallbacks(heartbeatRunnable)
        heartbeatCheck = null
    }

    private fun scheduleHeartbeat() {
        mainHandler.removeCallbacks(heartbeatRunnable)
        if (!isTerminal()) {
            mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun transitionTo(next: AceHealthState) {
        state = next
    }

    private fun isTerminal(): Boolean {
        return state == AceHealthState.FALLING_BACK ||
            state == AceHealthState.FAILED ||
            state == AceHealthState.DESTROYED
    }

    companion object {
        const val READY_TIMEOUT_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val HEARTBEAT_DEGRADED_FAILURES = 2
        private const val HEARTBEAT_FALLBACK_FAILURES = 3
    }
}
