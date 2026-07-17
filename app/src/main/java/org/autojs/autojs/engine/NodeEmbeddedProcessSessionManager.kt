package org.autojs.autojs.engine

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs6.BuildConfig
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the long-lived Binder lease for every embedded Node process slot.
 *
 * A process session is reusable, but an execution is not: the service/native layer still creates
 * and destroys a fresh Environment, isolate, bridge provider, and resource registry for every run.
 */
object NodeEmbeddedProcessSessionManager {

    const val DEFAULT_IDLE_TTL_MS = 30_000L
    const val PREWARM_IDLE_TTL_MS = 10_000L

    private const val TAG = "NodeEmbeddedProcessSession"
    private const val BIND_TIMEOUT_MS = 15_000L
    private const val QUARANTINE_MS = 2_000L
    private const val PROCESS_RETIRE_TIMEOUT_MS = 3_000L
    private const val HEARTBEAT_INTERVAL_MS = 5_000L
    private const val HEARTBEAT_RPC_TIMEOUT_MS = 1_000L
    private const val SHUTDOWN_WAIT_SLICE_MS = 20L

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AutoJs6-Node-SessionManager").apply { isDaemon = true }
    }
    private val heartbeatScheduler = ScheduledThreadPoolExecutor(2) { runnable ->
        Thread(runnable, "AutoJs6-Node-SessionHeartbeat").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
    }
    private val heartbeatRpcExecutor = newBoundedRpcExecutor("AutoJs6-Node-HeartbeatRpc")
    private val runtimeReadyRpcExecutor = newBoundedRpcExecutor(
        threadName = "AutoJs6-Node-RuntimeReadyRpc",
        threadCount = 3,
        queueCapacity = 3,
    )
    private val lifecycleExecutor = Executors.newFixedThreadPool(3) { runnable ->
        Thread(runnable, "AutoJs6-Node-SessionLifecycle").apply { isDaemon = true }
    }
    private val shutdownRpcExecutor = newBoundedRpcExecutor("AutoJs6-Node-ShutdownRpc")
    private val bindingToken = AtomicLong(0L)
    private val leaseToken = AtomicLong(0L)
    private val managerEpoch = AtomicLong(0L)
    private val managerLifecycleLock = Object()
    private val sessions = ConcurrentHashMap<String, SlotSession>()
    @Volatile
    private var closing = false

    @Volatile
    internal var beforeRuntimeReadyRpcForTests: (() -> Unit)? = null

    @Volatile
    internal var onRuntimeInitializationWaitForTests: (() -> Unit)? = null

    enum class State {
        COLD,
        BINDING,
        READY,
        BUSY,
        DRAINING,
        QUARANTINED,
    }

    data class AcquireResult(
        val lease: SessionLease?,
        val errorMessage: String? = null,
        val nativeValues: Map<String, String> = emptyMap(),
    ) {
        val acquired: Boolean
            get() = lease != null
    }

    @ConsistentCopyVisibility
    data class SessionLease internal constructor(
        val slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
        val generation: Long,
        val acquiredAtElapsedMs: Long,
        internal val bindingId: Long,
        internal val sessionEpoch: Long,
        internal val managerEpoch: Long,
        internal val leaseId: Long,
        internal val runtimeValues: Map<String, String>,
    ) {
        fun nativeValues(status: String = "ready"): Map<String, String> = linkedMapOf<String, String>().apply {
            putAll(runtimeValues)
            put("embedded_script.process_session.enabled", "true")
            put("embedded_script.process_session.status", status)
            put("embedded_script.process_session.slot_id", slot.id)
            put("embedded_script.process_session.process_suffix", slot.processSuffix)
            put("embedded_script.process_session.generation", generation.toString())
            put("embedded_script.process_session.binding_id", bindingId.toString())
            put("embedded_script.process_session.epoch", sessionEpoch.toString())
            put("embedded_script.process_session.manager_epoch", managerEpoch.toString())
            put("embedded_script.process_session.lease_id", leaseId.toString())
            put("embedded_script.process_session.idle_ttl_ms", DEFAULT_IDLE_TTL_MS.toString())
            put("embedded_script.process_session.acquired_at_elapsed_ms", acquiredAtElapsedMs.toString())
        }
    }

    data class PrewarmResult(
        val started: Boolean,
        val status: String,
        val reason: String,
        val processName: String,
        val pid: Int,
        val nativeValues: Map<String, String>,
    )

    private class SlotSession(
        val slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
    ) {
        val lock = Object()
        var state = State.COLD
        var epoch = 0L
        var bindingId = 0L
        var generation = 0L
        var connection: ServiceConnection? = null
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var control: INodeEmbeddedProcessControl? = null
        val activeLeaseIds = linkedSetOf<Long>()
        var quarantineUntilElapsedMs = 0L
        var retiringPid: Int? = null
        var processExitConfirmed = true
        var exitAbsentSamples = 0
        var retirementInFlight = false
        var lastFailure = ""
        var lastRuntimeValues: Map<String, String> = emptyMap()
        var idleFuture: ScheduledFuture<*>? = null
        var heartbeatFuture: ScheduledFuture<*>? = null
        var heartbeatSequence = 0L
        val activeExecutions = linkedMapOf<String, ExecutionRegistration>()
    }

    private data class ExecutionRegistration(
        val leaseId: Long,
        val onProcessDeath: () -> Unit,
    )

    private data class RetirementWork(
        val epoch: Long,
        val control: INodeEmbeddedProcessControl?,
        val binder: IBinder?,
        val connection: ServiceConnection?,
        val callbacks: List<() -> Unit>,
        val pid: Int?,
        val reason: String,
        val coldOnSuccess: Boolean,
    )

    fun isEnabled(): Boolean = BuildConfig.NODEJS_EMBEDDED_PERSISTENT_RUNTIME_ENABLED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isClosing(): Boolean = closing

    /**
     * Reopens the manager for a new application lifetime. In-flight work from an earlier lifetime
     * keeps its old manager epoch and therefore cannot publish a reusable lease into this one. A
     * reopen is deferred while any old session is unsettled; callers can inspect [isClosing].
     */
    fun reopenForUse(): Long = synchronized(managerLifecycleLock) {
        if (closing) {
            val sessionsSettled = sessions.values.all { session ->
                synchronized(session.lock) {
                    session.state == State.COLD &&
                            session.control == null &&
                            session.connection == null &&
                            session.activeLeaseIds.isEmpty() &&
                            !session.retirementInFlight
                }
            }
            if (!sessionsSettled) {
                Log.w(TAG, "manager reopen deferred because process sessions are still active")
                return@synchronized managerEpoch.get()
            }
            closing = false
            managerEpoch.incrementAndGet()
        }
        managerEpoch.get()
    }

    /**
     * Prevents new acquire/prewarm calls synchronously. The potentially blocking process shutdown
     * can then be performed off the main thread through [shutdownAllAndAwait].
     */
    fun beginClosing(): Long = synchronized(managerLifecycleLock) {
        if (!closing) {
            closing = true
            managerEpoch.incrementAndGet()
        }
        managerEpoch.get()
    }

    fun acquire(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
        timeoutMs: Long = BIND_TIMEOUT_MS,
    ): AcquireResult {
        if (!isEnabled()) {
            return AcquireResult(
                lease = null,
                errorMessage = "Persistent embedded Node runtime is disabled by build configuration.",
                nativeValues = disabledNativeValues(),
            )
        }
        val contextRef = context.applicationContext
        if (!isServiceDeclared(contextRef, slot.componentName(contextRef))) {
            return AcquireResult(
                lease = null,
                errorMessage = "Embedded Node process slot ${slot.id} is not declared in this APK.",
                nativeValues = mapOf(
                    "embedded_script.process_session.enabled" to "true",
                    "embedded_script.process_session.status" to "undeclared",
                    "embedded_script.process_session.slot_id" to slot.id,
                ),
            )
        }
        val acquisitionManagerEpoch: Long
        val session: SlotSession
        synchronized(managerLifecycleLock) {
            if (closing) {
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process session manager is closing.",
                    nativeValues = closingNativeValues(slot),
                )
            }
            acquisitionManagerEpoch = managerEpoch.get()
            session = sessions.getOrPut(slot.id) { SlotSession(slot) }
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(1L)
        var shouldBind = false
        var connectionToBind: ServiceConnection? = null
        var bindingEpoch = -1L
        var bindingIdToBind = -1L
        var preBindQuarantineWork: RetirementWork? = null
        if (!resetQuarantineAfterConfirmedExit(contextRef, session)) {
            return AcquireResult(
                lease = null,
                errorMessage = "Embedded Node process slot ${slot.id} is quarantined: ${sessionSnapshot(session, "quarantined")["embedded_script.process_session.last_failure"].orEmpty()}",
                nativeValues = sessionSnapshot(session, "quarantined"),
            )
        }
        synchronized(session.lock) {
            while (session.state == State.DRAINING) {
                if (!managerOpenAt(acquisitionManagerEpoch)) {
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Embedded Node process session manager began closing while slot ${slot.id} retired.",
                        nativeValues = sessionNativeValuesLocked(session, "closing"),
                    )
                }
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Timed out while the previous embedded Node process session for slot ${slot.id} retired.",
                        nativeValues = sessionNativeValuesLocked(session, "retire_timeout"),
                    )
                }
                try {
                    session.lock.wait(remaining.coerceAtMost(100L))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Interrupted while the previous embedded Node process session retired.",
                        nativeValues = sessionNativeValuesLocked(session, "retire_interrupted"),
                    )
                }
            }
            session.idleFuture?.cancel(false)
            session.idleFuture = null
            if (!managerOpenAt(acquisitionManagerEpoch)) {
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process session manager is closing.",
                    nativeValues = sessionNativeValuesLocked(session, "closing"),
                )
            }
            if (session.state == State.QUARANTINED) {
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process slot ${slot.id} remains quarantined: ${session.lastFailure}",
                    nativeValues = sessionNativeValuesLocked(session, "quarantined"),
                )
            }
            if (session.control == null || session.binder?.isBinderAlive != true) {
                if (session.state == State.COLD) {
                    val id = bindingToken.incrementAndGet()
                    val epoch = session.epoch
                    val connection = createConnection(
                        contextRef,
                        session,
                        id,
                        epoch,
                        acquisitionManagerEpoch,
                    )
                    session.bindingId = id
                    session.connection = connection
                    session.state = State.BINDING
                    shouldBind = true
                    connectionToBind = connection
                    bindingEpoch = epoch
                    bindingIdToBind = id
                } else if (session.state != State.BINDING) {
                    preBindQuarantineWork = beginQuarantineLocked(
                        session,
                        "binder_unavailable_before_acquire",
                    )
                }
            }
        }
        preBindQuarantineWork?.let { work ->
            dispatchRetirementWork(contextRef, session, work)
            return AcquireResult(
                lease = null,
                errorMessage = "Embedded Node process session for slot ${slot.id} lost its Binder and was quarantined.",
                nativeValues = sessionSnapshot(session, "binder_unavailable"),
            )
        }
        if (shouldBind) {
            val bindStillClaimed = synchronized(session.lock) {
                session.epoch == bindingEpoch &&
                        session.state == State.BINDING &&
                        session.connection === connectionToBind &&
                        managerOpenAt(acquisitionManagerEpoch)
            }
            if (!bindStillClaimed) {
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process binding for slot ${slot.id} was invalidated before it started.",
                    nativeValues = sessionSnapshot(session, if (closing) "closing" else "binding_invalidated"),
                )
            }
            val bound = runCatching {
                val bindIntent = Intent().setComponent(slot.componentName(contextRef))
                contextRef.bindService(
                    bindIntent,
                    Context.BIND_AUTO_CREATE,
                    scheduler,
                    connectionToBind!!,
                )
            }.onFailure { error ->
                Log.w(TAG, "session.bind failed slot=${slot.id}", error)
            }.getOrDefault(false)
            val bindRemainsCurrent = synchronized(session.lock) {
                session.epoch == bindingEpoch &&
                        session.bindingId == bindingIdToBind &&
                        session.connection === connectionToBind
            }
            if (bound && !bindRemainsCurrent) {
                runCatching { contextRef.unbindService(connectionToBind!!) }
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process binding for slot ${slot.id} was invalidated while bindService was in flight.",
                    nativeValues = sessionSnapshot(session, if (closing) "closing" else "binding_invalidated"),
                )
            }
            if (!bound) {
                quarantine(
                    contextRef,
                    session,
                    "bind_returned_false",
                    expectedBindingId = bindingIdToBind,
                    expectedEpoch = bindingEpoch,
                )
            }
        }
        var unavailableReason: String? = null
        var unavailableEpoch = -1L
        var unavailableBindingId = -1L
        var bindingInterrupted = false
        synchronized(session.lock) {
            while (session.control == null && session.state == State.BINDING) {
                if (!managerOpenAt(acquisitionManagerEpoch)) {
                    break
                }
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    break
                }
                try {
                    session.lock.wait(remaining.coerceAtMost(250L))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    bindingInterrupted = true
                    break
                }
            }
            if (session.control == null || session.binder?.isBinderAlive != true) {
                val reason = when {
                    bindingInterrupted -> "bind_interrupted"
                    session.state == State.BINDING -> "bind_timeout"
                    else -> session.lastFailure.ifBlank { "binder_unavailable" }
                }
                unavailableReason = reason
                unavailableEpoch = session.epoch
                unavailableBindingId = session.bindingId
            }
        }
        unavailableReason?.let { reason ->
            quarantine(
                contextRef,
                session,
                reason,
                expectedBindingId = unavailableBindingId,
                expectedEpoch = unavailableEpoch,
            )
            return AcquireResult(
                lease = null,
                errorMessage = "Unable to establish embedded Node process session for slot ${slot.id}: $reason.",
                nativeValues = sessionSnapshot(session, "unavailable"),
            )
        }

        val leaseId = leaseToken.incrementAndGet()
        val sessionEpoch: Long
        val bindingId: Long
        val generation: Long
        val binder: IBinder
        val control: INodeEmbeddedProcessControl
        val runtimeReadyRequired: Boolean
        val cachedRuntimeValues: Map<String, String>
        synchronized(session.lock) {
            while (session.state == State.BUSY && !runtimeHealthy(session.lastRuntimeValues)) {
                if (!managerOpenAt(acquisitionManagerEpoch)) {
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Embedded Node process session manager began closing during runtime initialization.",
                        nativeValues = sessionNativeValuesLocked(session, "closing"),
                    )
                }
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Timed out waiting for embedded Node process runtime initialization for slot ${slot.id}.",
                        nativeValues = sessionNativeValuesLocked(session, "runtime_initialization_wait_timeout"),
                    )
                }
                onRuntimeInitializationWaitForTests?.invoke()
                try {
                    session.lock.wait(remaining.coerceAtMost(100L))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return AcquireResult(
                        lease = null,
                        errorMessage = "Interrupted while waiting for embedded Node process runtime initialization for slot ${slot.id}.",
                        nativeValues = sessionNativeValuesLocked(session, "runtime_initialization_wait_interrupted"),
                    )
                }
            }
            val currentBinder = session.binder
            val currentControl = session.control
            if (!managerOpenAt(acquisitionManagerEpoch) ||
                session.state !in setOf(State.READY, State.BUSY) ||
                currentBinder?.isBinderAlive != true ||
                currentControl == null
            ) {
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process session for slot ${slot.id} was invalidated before runtime initialization.",
                    nativeValues = sessionNativeValuesLocked(session, if (closing) "closing" else "invalidated"),
                )
            }
            cachedRuntimeValues = session.lastRuntimeValues
            val cachedRuntimeHealthy = runtimeHealthy(cachedRuntimeValues)
            runtimeReadyRequired = !cachedRuntimeHealthy
            sessionEpoch = session.epoch
            bindingId = session.bindingId
            generation = session.generation
            binder = currentBinder
            control = currentControl
            session.activeLeaseIds += leaseId
            session.state = State.BUSY
        }

        val runtimeValues = if (runtimeReadyRequired) {
            beforeRuntimeReadyRpcForTests?.invoke()
            val runtimeReadyRpc = try {
                runtimeReadyRpcExecutor.submit<Bundle> { control.ensureRuntimeReady(true) }
            } catch (_: RejectedExecutionException) {
                failAcquireAndQuarantine(
                    contextRef,
                    session,
                    leaseId,
                    sessionEpoch,
                    bindingId,
                    "runtime_ready_executor_rejected",
                    emptyMap(),
                )
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process runtime-ready executor rejected slot ${slot.id}.",
                    nativeValues = sessionSnapshot(session, "runtime_ready_executor_rejected"),
                )
            }
            try {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    throw TimeoutException("Acquire deadline expired before runtime-ready RPC completed.")
                }
                runtimeReadyRpc.get(remaining, TimeUnit.MILLISECONDS).toStringMap()
            } catch (error: Throwable) {
                runtimeReadyRpc.cancel(true)
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                val failure = if (error is TimeoutException) {
                    "runtime_ready_timeout"
                } else {
                    "runtime_ready_exception:${error.javaClass.simpleName}"
                }
                failAcquireAndQuarantine(
                    contextRef,
                    session,
                    leaseId,
                    sessionEpoch,
                    bindingId,
                    failure,
                    emptyMap(),
                )
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process runtime initialization failed: ${error.message ?: error.javaClass.name}",
                    nativeValues = sessionSnapshot(session, failure),
                )
            }
        } else {
            cachedRuntimeValues
        }
        if (!runtimeHealthy(runtimeValues)) {
            failAcquireAndQuarantine(
                contextRef,
                session,
                leaseId,
                sessionEpoch,
                bindingId,
                "runtime_not_healthy",
                runtimeValues,
            )
            return AcquireResult(
                lease = null,
                errorMessage = "Embedded Node process runtime for slot ${slot.id} did not become healthy.",
                nativeValues = runtimeValues + sessionSnapshot(session, "runtime_not_healthy"),
            )
        }
        if (!binder.isBinderAlive) {
            failAcquireAndQuarantine(
                contextRef,
                session,
                leaseId,
                sessionEpoch,
                bindingId,
                "runtime_ready_binder_died",
                runtimeValues,
            )
            return AcquireResult(
                lease = null,
                errorMessage = "Embedded Node process Binder died after runtime initialization for slot ${slot.id}.",
                nativeValues = sessionSnapshot(session, "runtime_ready_binder_died"),
            )
        }
        synchronized(session.lock) {
            if (!managerOpenAt(acquisitionManagerEpoch) ||
                session.epoch != sessionEpoch ||
                session.bindingId != bindingId ||
                session.generation != generation ||
                session.binder !== binder ||
                session.control !== control ||
                !binder.isBinderAlive ||
                leaseId !in session.activeLeaseIds ||
                session.state != State.BUSY
            ) {
                session.activeLeaseIds.remove(leaseId)
                if (session.state == State.BUSY && session.activeLeaseIds.isEmpty() && session.control != null) {
                    session.state = State.READY
                }
                return AcquireResult(
                    lease = null,
                    errorMessage = "Embedded Node process session for slot ${slot.id} changed while the runtime-ready RPC was in flight.",
                    nativeValues = sessionNativeValuesLocked(session, if (closing) "closing" else "runtime_ready_stale"),
                )
            }
            session.lastRuntimeValues = runtimeValues
            session.lock.notifyAll()
            scheduleHeartbeatLocked(contextRef, session)
            return AcquireResult(
                lease = SessionLease(
                    slot = slot,
                    generation = generation,
                    acquiredAtElapsedMs = SystemClock.elapsedRealtime(),
                    bindingId = bindingId,
                    sessionEpoch = sessionEpoch,
                    managerEpoch = acquisitionManagerEpoch,
                    leaseId = leaseId,
                    runtimeValues = runtimeValues + sessionNativeValuesLocked(session, "ready"),
                ),
            )
        }
    }

    fun registerExecution(
        lease: SessionLease,
        executionId: String,
        onProcessDeath: () -> Unit,
    ): Boolean {
        val session = sessions[lease.slot.id] ?: return false
        synchronized(session.lock) {
            if (!leaseIsActiveLocked(session, lease) ||
                session.state != State.BUSY ||
                session.binder?.isBinderAlive != true ||
                session.control == null ||
                executionId in session.activeExecutions
            ) {
                return false
            }
            session.activeExecutions[executionId] = ExecutionRegistration(lease.leaseId, onProcessDeath)
            return true
        }
    }

    fun <T> startRegisteredExecution(
        lease: SessionLease,
        executionId: String,
        start: () -> T,
    ): T? = synchronized(managerLifecycleLock) manager@{
        if (!managerOpenAt(lease.managerEpoch)) {
            return@manager null
        }
        val session = sessions[lease.slot.id] ?: return@manager null
        synchronized(session.lock) session@{
            val registration = session.activeExecutions[executionId]
            if (!leaseIsActiveLocked(session, lease) ||
                session.state != State.BUSY ||
                session.binder?.isBinderAlive != true ||
                session.control == null ||
                registration?.leaseId != lease.leaseId
            ) {
                return@session null
            }
            start()
        }
    }

    fun unregisterExecution(lease: SessionLease?, executionId: String) {
        val session = lease?.let { sessions[it.slot.id] } ?: return
        synchronized(session.lock) {
            if (session.activeExecutions[executionId]?.leaseId == lease.leaseId) {
                session.activeExecutions.remove(executionId)
            }
        }
    }

    fun release(
        context: Context,
        lease: SessionLease?,
        healthy: Boolean,
        reason: String,
        idleTtlMs: Long = DEFAULT_IDLE_TTL_MS,
    ) {
        lease ?: return
        val contextRef = context.applicationContext
        val session = sessions[lease.slot.id] ?: return
        var quarantineWork: RetirementWork? = null
        synchronized(session.lock) {
            if (!leaseIsActiveLocked(session, lease)) {
                return
            }
            if (!healthy || session.binder?.isBinderAlive != true || session.control == null) {
                quarantineWork = beginQuarantineLocked(
                    session,
                    if (!healthy) {
                        reason.ifBlank { "execution_teardown_unhealthy" }
                    } else {
                        "binder_unavailable_on_release"
                    },
                    expectedEpoch = lease.sessionEpoch,
                    expectedBindingId = lease.bindingId,
                )
            } else {
                session.activeLeaseIds.remove(lease.leaseId)
                session.activeExecutions.entries.removeAll { (_, registration) ->
                    registration.leaseId == lease.leaseId
                }
                if (session.activeLeaseIds.isEmpty()) {
                    session.state = State.READY
                    scheduleIdleRetirementLocked(contextRef, session, idleTtlMs)
                } else {
                    session.state = State.BUSY
                }
            }
        }
        quarantineWork?.let { dispatchRetirementWork(contextRef, session, it) }
    }

    fun prewarm(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot = NodeEmbeddedProcessPoolPolicy.primarySlot,
        ttlMs: Long = PREWARM_IDLE_TTL_MS,
        timeoutMs: Long = BIND_TIMEOUT_MS,
    ): PrewarmResult {
        val result = acquire(context, slot, timeoutMs)
        val lease = result.lease
        if (lease == null) {
            return PrewarmResult(
                started = false,
                status = "failed",
                reason = result.errorMessage.orEmpty().ifBlank { "session_acquire_failed" },
                processName = result.nativeValues["process_runtime.process_name"].orEmpty(),
                pid = result.nativeValues["process_runtime.pid"]?.toIntOrNull() ?: -1,
                nativeValues = result.nativeValues,
            )
        }
        release(context, lease, healthy = true, reason = "prewarm_ready", idleTtlMs = ttlMs)
        return PrewarmResult(
            started = true,
            status = "ready",
            reason = "runtime_ready",
            processName = lease.runtimeValues["process_runtime.process_name"].orEmpty(),
            pid = lease.runtimeValues["process_runtime.pid"]?.toIntOrNull() ?: -1,
            nativeValues = lease.nativeValues("prewarmed"),
        )
    }

    fun onTrimMemory(context: Context, level: Int) {
        if (!isEnabled() || level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            return
        }
        val contextRef = context.applicationContext
        sessions.values.forEach { session ->
            retireIfIdle(contextRef, session, "trim_memory_$level")
        }
    }

    fun shutdownAllAndAwait(
        context: Context,
        reason: String = "explicit_shutdown",
        timeoutMs: Long = 5_000L,
        expectedClosingEpoch: Long? = null,
    ): Boolean {
        val contextRef = context.applicationContext
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(1L)
        val shutdownSessions = synchronized(managerLifecycleLock) {
            if (expectedClosingEpoch != null) {
                if (!closing || managerEpoch.get() != expectedClosingEpoch) {
                    return true
                }
            } else if (!closing) {
                closing = true
                managerEpoch.incrementAndGet()
            }
            sessions.values.toList()
        }
        shutdownSessions.forEach { session ->
            val observedPid = findSlotProcessPid(contextRef, session.slot)
            val work = synchronized(session.lock) {
                beginRetirementLocked(
                    session,
                    reason,
                    requireIdle = false,
                    fallbackPid = observedPid,
                )
            }
            work?.let {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                dispatchRetirementWork(
                    contextRef,
                    session,
                    it,
                    processExitTimeoutMs = remaining,
                )
            }
        }
        while (SystemClock.elapsedRealtime() < deadline) {
            if (allSessionsDisconnectedAndExited(contextRef, shutdownSessions)) {
                return true
            }
            try {
                Thread.sleep(SHUTDOWN_WAIT_SLICE_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return allSessionsDisconnectedAndExited(contextRef, shutdownSessions)
    }

    fun resultIsReusable(nativeValues: Map<String, String>): Boolean {
        val state = nativeValues.runtimeValue("process_runtime.state", "embedded_script.process_runtime.state")
            .lowercase()
        val healthy = nativeValues.runtimeValue("process_runtime.healthy", "embedded_script.process_runtime.healthy")
            .toBooleanStrictOrNull()
        val poisoned = nativeValues.runtimeValue("process_runtime.poisoned", "embedded_script.process_runtime.poisoned")
            .toBooleanStrictOrNull() ?: false
        val teardownClean = nativeValues.runtimeValue(
            "process_runtime.teardown_clean",
            "embedded_script.process_runtime.teardown_clean",
        ).toBooleanStrictOrNull()
        val executionTeardownClean = nativeValues["execution.teardown_clean"]?.toBooleanStrictOrNull()
        val serviceCleanupClean = nativeValues["embedded_script.service.cleanup_clean"]?.toBooleanStrictOrNull()
        val serviceRetirementRequested = nativeValues["embedded_script.service.retirement_requested"]
            ?.toBooleanStrictOrNull() ?: false
        val nativeExecutionEntered = nativeValues["embedded_script.native_execution_entered"]
            ?.toBooleanStrictOrNull()
        if (nativeExecutionEntered == false) {
            return healthy == true &&
                    !poisoned &&
                    !serviceRetirementRequested &&
                    state == "ready" &&
                    teardownClean == true &&
                    serviceCleanupClean == true
        }
        return healthy == true &&
                !poisoned &&
                !serviceRetirementRequested &&
                state == "ready" &&
                teardownClean == true &&
                executionTeardownClean == true &&
                serviceCleanupClean == true
    }

    fun sessionSnapshot(slotId: String): Map<String, String> {
        val session = sessions[slotId] ?: return mapOf(
            "embedded_script.process_session.enabled" to isEnabled().toString(),
            "embedded_script.process_session.status" to "absent",
            "embedded_script.process_session.slot_id" to slotId,
            "embedded_script.process_session.manager_epoch" to managerEpoch.get().toString(),
            "embedded_script.process_session.manager_closing" to closing.toString(),
        )
        return sessionSnapshot(session, synchronized(session.lock) { session.state.name.lowercase() })
    }

    private fun createConnection(
        context: Context,
        session: SlotSession,
        id: Long,
        epoch: Long,
        expectedManagerEpoch: Long,
    ): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val control = INodeEmbeddedProcessControl.Stub.asInterface(service)
                val deathRecipient = IBinder.DeathRecipient {
                    handleBinderFailure(context, session, id, epoch, "binder_death")
                }
                val linked = runCatching {
                    service.linkToDeath(deathRecipient, 0)
                    true
                }.getOrDefault(false)
                if (!linked) {
                    handleBinderFailure(context, session, id, epoch, "link_to_death_failed")
                    return
                }
                var stale = false
                synchronized(session.lock) {
                    if (session.epoch != epoch ||
                        session.bindingId != id ||
                        session.connection !== this ||
                        !managerOpenAt(expectedManagerEpoch)
                    ) {
                        runCatching { service.unlinkToDeath(deathRecipient, 0) }
                        stale = true
                    } else {
                        session.binder = service
                        session.deathRecipient = deathRecipient
                        session.control = control
                        session.generation += 1
                        session.state = State.READY
                        session.lastFailure = ""
                        session.lock.notifyAll()
                    }
                }
                if (stale) {
                    runCatching { context.unbindService(this) }
                    return
                }
                Log.i(TAG, "session.connected slot=${session.slot.id} generation=${session.generation} bindingId=$id")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                handleBinderFailure(context, session, id, epoch, "service_disconnected")
            }

            override fun onBindingDied(name: ComponentName) {
                handleBinderFailure(context, session, id, epoch, "binding_died")
            }

            override fun onNullBinding(name: ComponentName) {
                handleBinderFailure(context, session, id, epoch, "null_binding")
            }
        }
    }

    private fun handleBinderFailure(
        context: Context,
        session: SlotSession,
        id: Long,
        epoch: Long,
        reason: String,
    ) {
        quarantine(context, session, reason, expectedBindingId = id, expectedEpoch = epoch)
    }

    private fun failAcquireAndQuarantine(
        context: Context,
        session: SlotSession,
        leaseId: Long,
        expectedEpoch: Long,
        expectedBindingId: Long,
        reason: String,
        values: Map<String, String>,
    ) {
        val work: RetirementWork?
        synchronized(session.lock) {
            if (session.epoch != expectedEpoch ||
                session.bindingId != expectedBindingId ||
                leaseId !in session.activeLeaseIds
            ) {
                return
            }
            if (values.isNotEmpty()) {
                session.lastRuntimeValues = session.lastRuntimeValues + values
            }
            session.activeLeaseIds.remove(leaseId)
            work = beginQuarantineLocked(
                session,
                reason,
                expectedEpoch = expectedEpoch,
                expectedBindingId = expectedBindingId,
            )
        }
        work?.let { dispatchRetirementWork(context, session, it) }
    }

    private fun quarantine(
        context: Context,
        session: SlotSession,
        reason: String,
        expectedBindingId: Long? = null,
        expectedEpoch: Long? = null,
    ) {
        val observedPid = findSlotProcessPid(context, session.slot)
        val work = synchronized(session.lock) {
            beginQuarantineLocked(
                session,
                reason,
                expectedEpoch,
                expectedBindingId,
                fallbackPid = observedPid,
            )
        }
        work?.let { dispatchRetirementWork(context, session, it) }
    }

    private fun beginQuarantineLocked(
        session: SlotSession,
        reason: String,
        expectedEpoch: Long? = null,
        expectedBindingId: Long? = null,
        fallbackPid: Int? = null,
    ): RetirementWork? {
        if (expectedEpoch != null && session.epoch != expectedEpoch) {
            return null
        }
        if (expectedBindingId != null && session.bindingId != expectedBindingId) {
            return null
        }
        if (session.state in setOf(State.DRAINING, State.QUARANTINED)) {
            return null
        }
        session.idleFuture?.cancel(false)
        session.idleFuture = null
        session.heartbeatFuture?.cancel(false)
        session.heartbeatFuture = null
        val control = session.control
        val binder = session.binder
        val connection = session.connection
        val pid = session.lastRuntimeValues.runtimeValue(
            "process_runtime.pid",
            "embedded_script.process_runtime.pid",
        ).toIntOrNull()
            ?.takeIf { it > 0 }
            ?: session.retiringPid
            ?: fallbackPid
        val callbacks = session.activeExecutions.values.map(ExecutionRegistration::onProcessDeath)
        session.epoch += 1
        session.lastFailure = reason
        session.quarantineUntilElapsedMs = SystemClock.elapsedRealtime() + QUARANTINE_MS
        session.retiringPid = pid
        session.processExitConfirmed = false
        session.exitAbsentSamples = 0
        session.retirementInFlight = true
        session.state = State.QUARANTINED
        session.activeExecutions.clear()
        session.activeLeaseIds.clear()
        session.connection = null
        clearBinderLocked(session)
        session.lock.notifyAll()
        return RetirementWork(
            epoch = session.epoch,
            control = control,
            binder = binder,
            connection = connection,
            callbacks = callbacks,
            pid = pid,
            reason = reason,
            coldOnSuccess = false,
        )
    }

    private fun scheduleIdleRetirementLocked(context: Context, session: SlotSession, idleTtlMs: Long) {
        session.idleFuture?.cancel(false)
        val generation = session.generation
        val epoch = session.epoch
        session.idleFuture = scheduler.schedule(
            {
                retireIfIdle(
                    context,
                    session,
                    "idle_ttl_expired",
                    expectedEpoch = epoch,
                    expectedGeneration = generation,
                )
            },
            idleTtlMs.coerceAtLeast(1L),
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleHeartbeatLocked(context: Context, session: SlotSession) {
        if (session.heartbeatFuture?.isDone == false) {
            return
        }
        val bindingId = session.bindingId
        val epoch = session.epoch
        session.heartbeatFuture = heartbeatScheduler.scheduleWithFixedDelay(
            {
                val control = synchronized(session.lock) {
                    session.control.takeIf {
                        session.epoch == epoch &&
                                session.bindingId == bindingId &&
                                session.state == State.READY &&
                                session.binder?.isBinderAlive == true
                    }
                } ?: return@scheduleWithFixedDelay
                val rpc = try {
                    heartbeatRpcExecutor.submit<Bundle> { control.getRuntimeStatus() }
                } catch (_: RejectedExecutionException) {
                    quarantine(context, session, "heartbeat_executor_rejected", bindingId, epoch)
                    return@scheduleWithFixedDelay
                }
                val values = try {
                    rpc.get(HEARTBEAT_RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS).toStringMap()
                } catch (_: TimeoutException) {
                    rpc.cancel(true)
                    quarantine(context, session, "heartbeat_timeout", bindingId, epoch)
                    return@scheduleWithFixedDelay
                } catch (error: Throwable) {
                    quarantine(
                        context,
                        session,
                        "heartbeat_exception:${error.javaClass.simpleName}",
                        bindingId,
                        epoch,
                    )
                    return@scheduleWithFixedDelay
                }
                val stillCurrent = synchronized(session.lock) {
                    session.epoch == epoch &&
                            session.bindingId == bindingId &&
                            session.control === control &&
                            session.state == State.READY &&
                            session.binder?.isBinderAlive == true
                }
                if (!stillCurrent) {
                    return@scheduleWithFixedDelay
                }
                if (!runtimeHealthy(values)) {
                    quarantine(
                        context,
                        session,
                        "heartbeat_runtime_unhealthy",
                        expectedBindingId = bindingId,
                        expectedEpoch = epoch,
                    )
                    return@scheduleWithFixedDelay
                }
                synchronized(session.lock) {
                    if (session.epoch == epoch &&
                        session.bindingId == bindingId &&
                        session.state == State.READY
                    ) {
                        session.heartbeatSequence += 1
                        session.lastRuntimeValues = session.lastRuntimeValues + values
                    }
                }
            },
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun retireIfIdle(
        context: Context,
        session: SlotSession,
        reason: String,
        expectedEpoch: Long? = null,
        expectedGeneration: Long? = null,
    ): Boolean {
        val work = synchronized(session.lock) {
            beginRetirementLocked(
                session,
                reason,
                requireIdle = true,
                expectedEpoch = expectedEpoch,
                expectedGeneration = expectedGeneration,
            )
        } ?: return false
        dispatchRetirementWork(context, session, work)
        return true
    }

    private fun beginRetirementLocked(
        session: SlotSession,
        reason: String,
        requireIdle: Boolean,
        expectedEpoch: Long? = null,
        expectedGeneration: Long? = null,
        fallbackPid: Int? = null,
    ): RetirementWork? {
        if (expectedEpoch != null && session.epoch != expectedEpoch) {
            return null
        }
        if (expectedGeneration != null && session.generation != expectedGeneration) {
            return null
        }
        if (session.state in setOf(State.DRAINING, State.QUARANTINED)) {
            return null
        }
        if (requireIdle && (session.state != State.READY || session.activeLeaseIds.isNotEmpty())) {
            return null
        }
        if (session.state == State.COLD &&
            session.connection == null &&
            session.control == null &&
            fallbackPid == null
        ) {
            return null
        }
        session.idleFuture?.cancel(false)
        session.idleFuture = null
        session.heartbeatFuture?.cancel(false)
        session.heartbeatFuture = null
        val control = session.control
        val binder = session.binder
        val connection = session.connection
        val pid = session.lastRuntimeValues.runtimeValue(
            "process_runtime.pid",
            "embedded_script.process_runtime.pid",
        ).toIntOrNull()
            ?.takeIf { it > 0 }
            ?: session.retiringPid
            ?: fallbackPid
        val callbacks = session.activeExecutions.values.map(ExecutionRegistration::onProcessDeath)
        session.epoch += 1
        session.state = State.DRAINING
        session.lastFailure = reason
        session.retiringPid = pid
        session.processExitConfirmed = false
        session.exitAbsentSamples = 0
        session.retirementInFlight = true
        session.connection = null
        session.activeExecutions.clear()
        session.activeLeaseIds.clear()
        clearBinderLocked(session)
        session.lock.notifyAll()
        return RetirementWork(
            epoch = session.epoch,
            control = control,
            binder = binder,
            connection = connection,
            callbacks = callbacks,
            pid = pid,
            reason = reason,
            coldOnSuccess = true,
        )
    }

    private fun dispatchRetirementWork(
        context: Context,
        session: SlotSession,
        work: RetirementWork,
        processExitTimeoutMs: Long = PROCESS_RETIRE_TIMEOUT_MS,
    ) {
        try {
            lifecycleExecutor.execute {
                val deadline = SystemClock.elapsedRealtime() + processExitTimeoutMs.coerceAtLeast(1L)
                work.callbacks.forEach { callback -> runCatching(callback) }
                val shutdownAcknowledged = requestShutdownWithTimeout(
                    session = session,
                    control = work.control,
                    reason = work.reason,
                    timeoutMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L),
                )
                if (!shutdownAcknowledged) {
                    synchronized(session.lock) {
                        if (session.epoch == work.epoch) {
                            session.lastFailure = "${work.reason}:shutdown_ack_timeout"
                        }
                    }
                }
                work.connection?.let { runCatching { context.unbindService(it) } }
                runCatching { context.stopService(Intent().setComponent(session.slot.componentName(context))) }
                val exited = waitForProcessExit(
                    context = context,
                    slot = session.slot,
                    timeoutMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L),
                )
                completeRetirement(session, work, exited)
            }
        } catch (error: RejectedExecutionException) {
            scheduler.execute {
                work.callbacks.forEach { callback -> runCatching(callback) }
            }
            if (work.binder?.isBinderAlive != true) {
                work.connection?.let { runCatching { context.unbindService(it) } }
                runCatching { context.stopService(Intent().setComponent(session.slot.componentName(context))) }
            }
            completeRetirement(session, work, exited = false)
            synchronized(session.lock) {
                if (session.epoch == work.epoch && session.state == State.QUARANTINED) {
                    session.lastFailure = "${work.reason}:retirement_executor_rejected"
                }
            }
            Log.w(TAG, "session retirement executor rejected slot=${session.slot.id}", error)
        }
    }

    private fun requestShutdownWithTimeout(
        session: SlotSession,
        control: INodeEmbeddedProcessControl?,
        reason: String,
        timeoutMs: Long,
    ): Boolean {
        control ?: return true
        val rpc: Future<*> = try {
            shutdownRpcExecutor.submit { control.requestShutdown(reason) }
        } catch (error: RejectedExecutionException) {
            Log.w(TAG, "session shutdown executor rejected slot=${session.slot.id}", error)
            return false
        }
        return try {
            rpc.get(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            rpc.cancel(true)
            Log.w(TAG, "session shutdown request timed out slot=${session.slot.id}")
            false
        } catch (error: Throwable) {
            Log.w(TAG, "session shutdown request failed slot=${session.slot.id}", error)
            false
        }
    }

    private fun completeRetirement(session: SlotSession, work: RetirementWork, exited: Boolean) {
        synchronized(session.lock) {
            if (session.epoch != work.epoch ||
                session.state !in setOf(State.DRAINING, State.QUARANTINED)
            ) {
                return
            }
            session.processExitConfirmed = exited
            session.exitAbsentSamples = if (exited) 2 else 0
            session.retirementInFlight = false
            if (work.coldOnSuccess && exited) {
                resetDisconnectedStateLocked(session)
            } else {
                session.state = State.QUARANTINED
                if (session.quarantineUntilElapsedMs <= 0L) {
                    session.quarantineUntilElapsedMs = SystemClock.elapsedRealtime() + QUARANTINE_MS
                }
                if (!exited && !session.lastFailure.endsWith(":process_exit_timeout")) {
                    session.lastFailure = "${work.reason}:process_exit_timeout"
                }
            }
            session.lock.notifyAll()
        }
        val status = if (exited) "retired" else "quarantined_process_alive"
        Log.i(TAG, "session.$status slot=${session.slot.id} generation=${session.generation} reason=${work.reason}")
    }

    private fun clearBinderLocked(session: SlotSession) {
        val binder = session.binder
        val deathRecipient = session.deathRecipient
        if (binder != null && deathRecipient != null) {
            runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        }
        session.binder = null
        session.deathRecipient = null
        session.control = null
    }

    private fun resetDisconnectedStateLocked(session: SlotSession) {
        session.state = State.COLD
        session.quarantineUntilElapsedMs = 0L
        session.retiringPid = null
        session.processExitConfirmed = true
        session.exitAbsentSamples = 0
        session.retirementInFlight = false
        session.lastFailure = ""
        session.lastRuntimeValues = emptyMap()
        session.connection = null
        session.activeLeaseIds.clear()
        session.activeExecutions.clear()
        session.idleFuture?.cancel(false)
        session.idleFuture = null
        session.heartbeatFuture?.cancel(false)
        session.heartbeatFuture = null
        clearBinderLocked(session)
    }

    private fun resetQuarantineAfterConfirmedExit(context: Context, session: SlotSession): Boolean {
        val epoch: Long
        val pid: Int?
        synchronized(session.lock) {
            if (session.state != State.QUARANTINED) {
                return true
            }
            if (session.retirementInFlight ||
                SystemClock.elapsedRealtime() < session.quarantineUntilElapsedMs
            ) {
                return false
            }
            epoch = session.epoch
            pid = session.retiringPid
            if (session.processExitConfirmed) {
                resetDisconnectedStateLocked(session)
                session.lock.notifyAll()
                return true
            }
        }
        if (!waitForProcessExit(context, session.slot, SHUTDOWN_WAIT_SLICE_MS * 3L)) {
            return false
        }
        synchronized(session.lock) {
            if (session.state != State.QUARANTINED ||
                session.epoch != epoch ||
                session.retiringPid != pid ||
                session.retirementInFlight ||
                SystemClock.elapsedRealtime() < session.quarantineUntilElapsedMs
            ) {
                return session.state != State.QUARANTINED
            }
            session.processExitConfirmed = true
            resetDisconnectedStateLocked(session)
            session.lock.notifyAll()
            return true
        }
    }

    private fun leaseIsActiveLocked(session: SlotSession, lease: SessionLease): Boolean =
        lease.managerEpoch == managerEpoch.get() &&
                lease.sessionEpoch == session.epoch &&
                lease.generation == session.generation &&
                lease.bindingId == session.bindingId &&
                lease.leaseId in session.activeLeaseIds

    private fun managerOpenAt(expectedEpoch: Long): Boolean =
        !closing && managerEpoch.get() == expectedEpoch

    private fun allSessionsDisconnectedAndExited(
        context: Context,
        shutdownSessions: List<SlotSession>,
    ): Boolean = shutdownSessions.all { session ->
        val snapshot = synchronized(session.lock) {
            Triple(session.control == null, !session.retirementInFlight, session.retiringPid)
        }
        if (!snapshot.first || !snapshot.second) {
            false
        } else {
            val processAlive = isSlotProcessAlive(context, session.slot)
            synchronized(session.lock) {
                if (session.control != null ||
                    session.retirementInFlight ||
                    session.retiringPid != snapshot.third
                ) {
                    false
                } else if (processAlive) {
                    session.exitAbsentSamples = 0
                    false
                } else if (session.state == State.QUARANTINED) {
                    session.exitAbsentSamples += 1
                    if (session.exitAbsentSamples < 2) {
                        false
                    } else {
                        session.processExitConfirmed = true
                        resetDisconnectedStateLocked(session)
                        session.lock.notifyAll()
                        true
                    }
                } else {
                    true
                }
            }
        }
    }

    private fun sessionSnapshot(session: SlotSession, status: String): Map<String, String> {
        synchronized(session.lock) {
            return sessionNativeValuesLocked(session, status)
        }
    }

    private fun sessionNativeValuesLocked(session: SlotSession, status: String): Map<String, String> =
        linkedMapOf<String, String>().apply {
            putAll(session.lastRuntimeValues)
            put("embedded_script.process_session.enabled", "true")
            put("embedded_script.process_session.status", status)
            put("embedded_script.process_session.state", session.state.name.lowercase())
            put("embedded_script.process_session.slot_id", session.slot.id)
            put("embedded_script.process_session.process_suffix", session.slot.processSuffix)
            put("embedded_script.process_session.generation", session.generation.toString())
            put("embedded_script.process_session.binding_id", session.bindingId.toString())
            put("embedded_script.process_session.epoch", session.epoch.toString())
            put("embedded_script.process_session.manager_epoch", managerEpoch.get().toString())
            put("embedded_script.process_session.manager_closing", closing.toString())
            put("embedded_script.process_session.active_lease_count", session.activeLeaseIds.size.toString())
            put("embedded_script.process_session.binder_alive", (session.binder?.isBinderAlive == true).toString())
            put("embedded_script.process_session.quarantined", (session.state == State.QUARANTINED).toString())
            put("embedded_script.process_session.quarantine_until_elapsed_ms", session.quarantineUntilElapsedMs.toString())
            put("embedded_script.process_session.retiring_pid", session.retiringPid?.toString().orEmpty())
            put("embedded_script.process_session.process_exit_confirmed", session.processExitConfirmed.toString())
            put("embedded_script.process_session.exit_absent_samples", session.exitAbsentSamples.toString())
            put("embedded_script.process_session.retirement_in_flight", session.retirementInFlight.toString())
            put("embedded_script.process_session.last_failure", session.lastFailure)
            put("embedded_script.process_session.heartbeat_sequence", session.heartbeatSequence.toString())
            put("embedded_script.process_session.heartbeat_interval_ms", HEARTBEAT_INTERVAL_MS.toString())
        }

    private fun runtimeHealthy(values: Map<String, String>): Boolean {
        val state = values.runtimeValue("process_runtime.state", "embedded_script.process_runtime.state").lowercase()
        val healthy = values.runtimeValue("process_runtime.healthy", "embedded_script.process_runtime.healthy")
            .toBooleanStrictOrNull()
        val poisoned = values.runtimeValue("process_runtime.poisoned", "embedded_script.process_runtime.poisoned")
            .toBooleanStrictOrNull() ?: false
        return healthy == true && !poisoned && state == "ready"
    }

    private fun Map<String, String>.runtimeValue(vararg names: String): String {
        names.forEach { name -> this[name]?.takeIf(String::isNotBlank)?.let { return it } }
        return ""
    }

    private fun Bundle?.toStringMap(): Map<String, String> {
        this ?: return emptyMap()
        return keySet().associateTo(linkedMapOf()) { key -> key to (get(key)?.toString().orEmpty()) }
    }

    private fun isServiceDeclared(context: Context, component: ComponentName): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(component, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun isSlotProcessAlive(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
    ): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val expectedProcessName = expectedProcessName(context, slot)
        return runCatching {
            val processes = activityManager.runningAppProcesses ?: return true
            processes.any { process ->
                process.processName == expectedProcessName
            }
        }.getOrDefault(true)
    }

    private fun findSlotProcessPid(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
    ): Int? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        val expectedProcessName = expectedProcessName(context, slot)
        return runCatching {
            val processes = activityManager.runningAppProcesses ?: return null
            processes
                .firstOrNull { process -> process.processName == expectedProcessName }
                ?.pid
        }.getOrNull()
    }

    private fun expectedProcessName(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
    ): String = if (slot.processSuffix.startsWith(":")) {
        context.packageName + slot.processSuffix
    } else {
        slot.processSuffix
    }

    private fun waitForProcessExit(
        context: Context,
        slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
        timeoutMs: Long,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(1L)
        var consecutiveAbsentSamples = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            val alive = isSlotProcessAlive(context, slot)
            if (!alive) {
                consecutiveAbsentSamples += 1
                if (consecutiveAbsentSamples >= 2) {
                    return true
                }
            } else {
                consecutiveAbsentSamples = 0
            }
            try {
                Thread.sleep(SHUTDOWN_WAIT_SLICE_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return consecutiveAbsentSamples >= 2 && !isSlotProcessAlive(context, slot)
    }

    private fun newBoundedRpcExecutor(
        threadName: String,
        threadCount: Int = 2,
        queueCapacity: Int = 2,
    ): ThreadPoolExecutor = ThreadPoolExecutor(
        threadCount,
        threadCount,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(queueCapacity),
        { runnable -> Thread(runnable, threadName).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    private fun disabledNativeValues(): Map<String, String> = mapOf(
        "embedded_script.process_session.enabled" to "false",
        "embedded_script.process_session.status" to "one_shot_rollback",
        "embedded_script.process_session.idle_ttl_ms" to "0",
    )

    private fun closingNativeValues(slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot): Map<String, String> = mapOf(
        "embedded_script.process_session.enabled" to "true",
        "embedded_script.process_session.status" to "closing",
        "embedded_script.process_session.slot_id" to slot.id,
        "embedded_script.process_session.manager_epoch" to managerEpoch.get().toString(),
        "embedded_script.process_session.manager_closing" to "true",
    )
}
