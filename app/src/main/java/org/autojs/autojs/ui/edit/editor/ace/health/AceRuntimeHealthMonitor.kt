package org.autojs.autojs.ui.edit.editor.ace.health

import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

class AceRuntimeHealthMonitor(
    private val mainHandler: Handler,
    private val fallbackPolicy: AceFallbackPolicy = AceFallbackPolicy(),
    private val pageLoadTimeoutMillis: Long = PAGE_LOAD_TIMEOUT_MS,
    private val bridgeReadyTimeoutMillis: Long = READY_TIMEOUT_MS,
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

    @Volatile
    var lastHeartbeatSuspendedAtUptimeMillis: Long = 0L
        private set

    @Volatile
    var lastHeartbeatResumedAtUptimeMillis: Long = 0L
        private set

    @Volatile
    var heartbeatSuspendedReason: String? = null
        private set

    private val fallbackStarted = AtomicBoolean(false)
    private var heartbeatCheck: (() -> Unit)? = null
    private var heartbeatFailures = 0
    private var heartbeatSuspendedUntilUptimeMillis = 0L

    private val pageLoadTimeoutRunnable = Runnable {
        if (!isTerminal() && state == AceHealthState.LOADING_PAGE) {
            reportFailure(
                AceFailure(
                    type = AceFailureType.PAGE_LOAD_TIMEOUT,
                    message = "ACE editor page did not finish loading within ${pageLoadTimeoutMillis}ms",
                ),
            )
        }
    }

    private val bridgeReadyTimeoutRunnable = Runnable {
        if (!isTerminal() && state == AceHealthState.PAGE_LOADED) {
            reportFailure(
                AceFailure(
                    type = AceFailureType.READY_TIMEOUT,
                    message = "ACE editor did not report ready within ${bridgeReadyTimeoutMillis}ms after page load",
                ),
            )
        }
    }

    private val heartbeatRunnable = Runnable {
        if (isTerminal() || state != AceHealthState.RUNNING) {
            return@Runnable
        }
        val suspendedDelay = heartbeatSuspendedDelayMillis()
        if (suspendedDelay > 0L) {
            scheduleHeartbeat(suspendedDelay)
            return@Runnable
        }
        heartbeatCheck?.invoke()
        scheduleHeartbeat()
    }

    fun markLoadingPage() {
        transitionTo(AceHealthState.LOADING_PAGE)
        cancelStartupTimeouts()
        mainHandler.postDelayed(pageLoadTimeoutRunnable, pageLoadTimeoutMillis)
    }

    fun markPageLoaded() {
        if (!isTerminal() && state == AceHealthState.LOADING_PAGE) {
            mainHandler.removeCallbacks(pageLoadTimeoutRunnable)
            transitionTo(AceHealthState.PAGE_LOADED)
            mainHandler.postDelayed(bridgeReadyTimeoutRunnable, bridgeReadyTimeoutMillis)
        }
    }

    fun markBridgeReady() {
        if (isTerminal()) {
            return
        }
        cancelStartupTimeouts()
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

    fun suspendHeartbeat(reason: String, durationMillis: Long) {
        if (isTerminal()) {
            return
        }
        val now = SystemClock.uptimeMillis()
        heartbeatSuspendedReason = reason
        lastHeartbeatSuspendedAtUptimeMillis = now
        heartbeatSuspendedUntilUptimeMillis = maxOf(
            heartbeatSuspendedUntilUptimeMillis,
            now + durationMillis.coerceAtLeast(0L),
        )
    }

    fun resumeHeartbeat(reason: String? = null) {
        if (isTerminal()) {
            return
        }
        if (reason != null && heartbeatSuspendedReason != null && heartbeatSuspendedReason != reason) {
            return
        }
        heartbeatSuspendedUntilUptimeMillis = 0L
        heartbeatSuspendedReason = null
        lastHeartbeatResumedAtUptimeMillis = SystemClock.uptimeMillis()
        scheduleHeartbeat()
    }

    fun isHeartbeatSuspended(): Boolean {
        return heartbeatSuspendedDelayMillis() > 0L
    }

    fun recordHeartbeatResult(ok: Boolean) {
        if (isTerminal()) {
            return
        }
        if (isHeartbeatSuspended()) {
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
        cancelStartupTimeouts()
        mainHandler.removeCallbacks(heartbeatRunnable)
        heartbeatSuspendedUntilUptimeMillis = 0L
        heartbeatSuspendedReason = null
        heartbeatCheck = null
    }

    private fun cancelStartupTimeouts() {
        mainHandler.removeCallbacks(pageLoadTimeoutRunnable)
        mainHandler.removeCallbacks(bridgeReadyTimeoutRunnable)
    }

    private fun scheduleHeartbeat(delayMillis: Long = HEARTBEAT_INTERVAL_MS) {
        mainHandler.removeCallbacks(heartbeatRunnable)
        if (!isTerminal()) {
            mainHandler.postDelayed(heartbeatRunnable, delayMillis.coerceAtLeast(0L))
        }
    }

    private fun heartbeatSuspendedDelayMillis(): Long {
        val until = heartbeatSuspendedUntilUptimeMillis
        if (until <= 0L) {
            return 0L
        }
        val remaining = until - SystemClock.uptimeMillis()
        if (remaining <= 0L) {
            heartbeatSuspendedUntilUptimeMillis = 0L
            heartbeatSuspendedReason = null
            lastHeartbeatResumedAtUptimeMillis = SystemClock.uptimeMillis()
            return 0L
        }
        return remaining
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
        const val PAGE_LOAD_TIMEOUT_MS = 15_000L
        const val READY_TIMEOUT_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val HEARTBEAT_DEGRADED_FAILURES = 2
        private const val HEARTBEAT_FALLBACK_FAILURES = 3
    }
}
