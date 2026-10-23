package org.autojs.autojs.core.plugin.python

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.foreground.PythonLongRunningForegroundService
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Opaque authorization minted only by a live foreground user-gesture launch. */
internal sealed interface PythonLongRunningExecutionGrant {
    fun openController(label: String, stopExecution: () -> Unit): PythonLongRunningExecutionController
}

internal object PythonLongRunningExecutionAuthorization {
    const val ARGUMENT = "autojs6.python.foregroundLongRunningGrant"

    fun authorizeForeground(config: ExecutionConfig, context: Context) {
        val activity = context.findLiveActivity()
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.LONG_RUNNING_NOT_ALLOWED,
                "Long-running Python execution requires a live foreground Activity",
            )
        config.setArgument(ARGUMENT, ForegroundGrant(activity))
    }

    fun resolve(config: ExecutionConfig?): PythonLongRunningExecutionGrant? {
        if (config == null || !config.arguments.containsKey(ARGUMENT)) return null
        return config.getArgument(ARGUMENT) as? ForegroundGrant
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.LONG_RUNNING_NOT_ALLOWED,
                "Long-running Python authorization was not minted by a foreground launch",
            )
    }

    private class ForegroundGrant(activity: Activity) : PythonLongRunningExecutionGrant {
        private val activity = WeakReference(activity)

        override fun openController(
            label: String,
            stopExecution: () -> Unit,
        ): PythonLongRunningExecutionController {
            val current = activity.get()?.takeIf(Activity::isLive)
                ?: throw PythonRuntimeHostException(
                    PythonRuntimeHostError.LONG_RUNNING_NOT_ALLOWED,
                    "Long-running Python foreground Activity is no longer available",
                )
            return PythonLongRunningExecutionController.start(current, label, stopExecution)
        }
    }
}

/** Pure monotonic lease policy shared by the Host wait loop and unit tests. */
internal class PythonLongRunningLeasePolicy(
    createdAtNanos: Long,
    private val startLeaseNanos: Long = TimeUnit.MILLISECONDS.toNanos(
        PythonRuntimeContract.LONG_RUNNING_START_LEASE_MILLIS,
    ),
    private val heartbeatLeaseNanos: Long = TimeUnit.MILLISECONDS.toNanos(
        PythonRuntimeContract.LONG_RUNNING_HEARTBEAT_LEASE_MILLIS,
    ),
    private val foregroundServiceLeaseNanos: Long = TimeUnit.SECONDS.toNanos(15),
) {
    enum class Failure {
        START_LEASE_EXPIRED,
        HEARTBEAT_LEASE_EXPIRED,
        FOREGROUND_SERVICE_LEASE_EXPIRED,
        FOREGROUND_SERVICE_STOPPED,
    }

    private val createdAtNanos = createdAtNanos.also { require(it >= 0L) }
    private var providerStarted = false
    private var lastProviderSignalNanos = createdAtNanos
    private var lastForegroundServiceSignalNanos = createdAtNanos
    private var foregroundServiceStopped = false

    init {
        require(startLeaseNanos > 0L)
        require(heartbeatLeaseNanos > 0L)
        require(foregroundServiceLeaseNanos > 0L)
    }

    @Synchronized
    fun onProviderStarted(nowNanos: Long) {
        require(nowNanos >= createdAtNanos) { "Provider start time moved backwards" }
        check(!providerStarted) { "Provider start was already observed" }
        check(!isExpired(nowNanos, createdAtNanos, startLeaseNanos)) {
            "Provider start lease already expired"
        }
        providerStarted = true
        lastProviderSignalNanos = nowNanos
    }

    @Synchronized
    fun onProviderHeartbeat(nowNanos: Long) {
        check(providerStarted) { "Provider heartbeat arrived before start" }
        require(nowNanos >= lastProviderSignalNanos) { "Provider heartbeat time moved backwards" }
        check(!isExpired(nowNanos, lastProviderSignalNanos, heartbeatLeaseNanos)) {
            "Provider heartbeat lease already expired"
        }
        lastProviderSignalNanos = nowNanos
    }

    @Synchronized
    fun onForegroundServicePulse(nowNanos: Long) {
        require(nowNanos >= lastForegroundServiceSignalNanos) {
            "Foreground service pulse time moved backwards"
        }
        if (isExpired(nowNanos, lastForegroundServiceSignalNanos, foregroundServiceLeaseNanos)) {
            foregroundServiceStopped = true
            return
        }
        foregroundServiceStopped = false
        lastForegroundServiceSignalNanos = nowNanos
    }

    @Synchronized
    fun onForegroundServiceStopped() {
        foregroundServiceStopped = true
    }

    @Synchronized
    fun failureAt(nowNanos: Long): Failure? {
        require(nowNanos >= createdAtNanos) { "Lease inspection time moved backwards" }
        if (foregroundServiceStopped) return Failure.FOREGROUND_SERVICE_STOPPED
        if (isExpired(nowNanos, lastForegroundServiceSignalNanos, foregroundServiceLeaseNanos)) {
            return Failure.FOREGROUND_SERVICE_LEASE_EXPIRED
        }
        if (!providerStarted) {
            return if (isExpired(nowNanos, createdAtNanos, startLeaseNanos)) {
                Failure.START_LEASE_EXPIRED
            } else {
                null
            }
        }
        return if (isExpired(nowNanos, lastProviderSignalNanos, heartbeatLeaseNanos)) {
            Failure.HEARTBEAT_LEASE_EXPIRED
        } else {
            null
        }
    }

    private fun isExpired(nowNanos: Long, signalNanos: Long, leaseNanos: Long): Boolean =
        nowNanos - signalNanos > leaseNanos
}

internal class PythonLongRunningExecutionController private constructor(
    private val applicationContext: Context,
    internal val token: String,
    private val leasePolicy: PythonLongRunningLeasePolicy,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    fun onStarted() {
        check(!closed.get()) { "Long-running Python controller is closed" }
        leasePolicy.onProviderStarted(SystemClock.elapsedRealtimeNanos())
        PythonLongRunningExecutionRegistry.markRunning(token)
    }

    fun onHeartbeat(elapsedMillis: Long) {
        check(!closed.get()) { "Long-running Python controller is closed" }
        leasePolicy.onProviderHeartbeat(SystemClock.elapsedRealtimeNanos())
        PythonLongRunningExecutionRegistry.markHeartbeat(token, elapsedMillis)
    }

    internal fun onForegroundServicePulse(nowNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        if (!closed.get()) leasePolicy.onForegroundServicePulse(nowNanos)
    }

    internal fun onForegroundServiceStopped() {
        if (!closed.get()) leasePolicy.onForegroundServiceStopped()
    }

    fun leaseFailure(nowNanos: Long = SystemClock.elapsedRealtimeNanos()): PythonRuntimeHostException? =
        leasePolicy.failureAt(nowNanos)?.let { failure ->
            PythonRuntimeHostException(
                PythonRuntimeHostError.EXECUTION_LOST,
                "Long-running Python liveness lease failed: ${failure.name}",
            )
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (PythonLongRunningExecutionRegistry.unregister(token)) {
            applicationContext.stopService(
                PythonLongRunningForegroundService.intent(applicationContext, token, stop = false),
            )
        }
    }

    companion object {
        fun start(
            activity: Activity,
            label: String,
            stopExecution: () -> Unit,
        ): PythonLongRunningExecutionController {
            val token = UUID.randomUUID().toString()
            val controller = PythonLongRunningExecutionController(
                activity.applicationContext,
                token,
                PythonLongRunningLeasePolicy(SystemClock.elapsedRealtimeNanos()),
            )
            PythonLongRunningExecutionRegistry.register(
                token = token,
                label = label.take(MAX_LABEL_CHARS),
                controller = controller,
                stopExecution = stopExecution,
            )
            try {
                ContextCompat.startForegroundService(
                    activity,
                    PythonLongRunningForegroundService.intent(activity, token, stop = false),
                )
                return controller
            } catch (error: RuntimeException) {
                PythonLongRunningExecutionRegistry.unregister(token)
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.LONG_RUNNING_NOT_ALLOWED,
                    "Long-running Python foreground service could not start",
                    error,
                )
            }
        }

        private const val MAX_LABEL_CHARS = 128
    }
}

internal object PythonLongRunningExecutionRegistry {
    enum class Phase {
        STARTING,
        RUNNING,
        STOPPING,
    }

    data class Snapshot(
        val token: String,
        val label: String,
        val phase: Phase,
        val elapsedMillis: Long,
    )

    private data class Record(
        val label: String,
        val controller: PythonLongRunningExecutionController,
        val stopExecution: () -> Unit,
        var phase: Phase = Phase.STARTING,
        var elapsedMillis: Long = 0L,
    )

    private val records = LinkedHashMap<String, Record>()

    @Synchronized
    fun register(
        token: String,
        label: String,
        controller: PythonLongRunningExecutionController,
        stopExecution: () -> Unit,
    ) {
        check(records.putIfAbsent(token, Record(label, controller, stopExecution)) == null) {
            "Long-running Python token was already registered"
        }
    }

    @Synchronized
    fun unregister(token: String): Boolean {
        records.remove(token)
        return records.isEmpty()
    }

    /** For the memory gate (docs/memory-phase2/ROADMAP.md T1 (h)). */
    @Synchronized
    fun recordCount(): Int = records.size

    @Synchronized
    fun markRunning(token: String) {
        records[token]?.phase = Phase.RUNNING
    }

    @Synchronized
    fun markHeartbeat(token: String, elapsedMillis: Long) {
        records[token]?.let { record ->
            record.phase = Phase.RUNNING
            record.elapsedMillis = elapsedMillis
        }
    }

    fun pulseForegroundService(nowNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val controllers = synchronized(this) { records.values.map(Record::controller) }
        controllers.forEach { controller -> controller.onForegroundServicePulse(nowNanos) }
    }

    fun foregroundServiceStopped() {
        val controllers = synchronized(this) { records.values.map(Record::controller) }
        controllers.forEach(PythonLongRunningExecutionController::onForegroundServiceStopped)
    }

    @Synchronized
    fun snapshot(token: String?): Snapshot? {
        val entry = token?.let { requested -> records[requested]?.let { requested to it } }
            ?: records.entries.firstOrNull()?.let { it.key to it.value }
            ?: return null
        return entry.second.snapshot(entry.first)
    }

    fun requestStop(token: String): Boolean {
        val stopExecution = synchronized(this) {
            val record = records[token] ?: return false
            if (record.phase == Phase.STOPPING) return true
            record.phase = Phase.STOPPING
            record.stopExecution
        }
        runCatching(stopExecution)
        return true
    }

    private fun Record.snapshot(token: String) = Snapshot(token, label, phase, elapsedMillis)
}

private fun Context.findLiveActivity(): Activity? {
    var current: Context? = this
    val visited = HashSet<Context>()
    while (current != null && visited.add(current)) {
        when (current) {
            is Activity -> return current.takeIf(Activity::isLive)
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
    return null
}

private fun Activity.isLive(): Boolean =
    !isFinishing && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
