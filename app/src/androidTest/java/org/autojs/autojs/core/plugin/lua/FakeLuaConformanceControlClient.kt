package org.autojs.autojs.core.plugin.lua

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import org.autojs.plugin.lua.runtime.fake.control.IFakeLuaConformanceControl
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exact-component client for the hostile fixture's independent test-only control plane. */
internal class FakeLuaConformanceControlClient private constructor(
    private val context: Context,
    private val connection: PendingControlConnection,
    private val binder: IBinder,
    private val control: IFakeLuaConformanceControl,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val binderFailure = connection.failure
    private val deathRecipient = IBinder.DeathRecipient {
        binderFailure.compareAndSet(null, IllegalStateException("Lua conformance control Binder died"))
    }

    init {
        binder.linkToDeath(deathRecipient, 0)
        requireAlive()
    }

    fun selectScenario(metadataValue: String) = checkedCall {
        control.selectScenario(metadataValue)
    }

    fun selectRuntimeAmbiguity(firstMetadataValue: String, secondMetadataValue: String) = checkedCall {
        control.selectRuntimeAmbiguity(firstMetadataValue, secondMetadataValue)
    }

    fun releasePluginInfoStall() = checkedCall {
        control.releasePluginInfoStall()
    }

    fun releaseRuntimeInfoStall() = checkedCall {
        control.releaseRuntimeInfoStall()
    }

    fun releaseCreateExecutionStall() = checkedCall {
        control.releaseCreateExecutionStall()
    }

    fun releaseAllStalls() = checkedCall {
        control.releaseAllStalls()
    }

    fun reset() = checkedCall {
        control.reset()
    }

    fun counters(): FakeLuaConformanceCounters = checkedCall {
        FakeLuaConformanceCounters(
            requireNotNull(control.counters) { "Lua conformance control returned null counters" },
        )
    }

    fun awaitCounters(
        description: String,
        timeoutMillis: Long = DEFAULT_COUNTER_TIMEOUT_MILLIS,
        predicate: (FakeLuaConformanceCounters) -> Boolean,
    ): FakeLuaConformanceCounters {
        require(timeoutMillis > 0L) { "Lua conformance counter timeout must be positive" }
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var latest = counters()
        while (!predicate(latest)) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw AssertionError("Timed out waiting for $description; latest counters: $latest")
            }
            SystemClock.sleep(COUNTER_POLL_MILLIS)
            latest = counters()
        }
        return latest
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (binder.isBinderAlive && binderFailure.get() == null) {
            runCatching { control.releaseAllStalls() }
            runCatching { control.reset() }
        }
        runCatching { binder.unlinkToDeath(deathRecipient, 0) }
        runCatching { context.unbindService(connection) }
    }

    private inline fun <T> checkedCall(action: () -> T): T {
        check(!closed.get()) { "Lua conformance control client is closed" }
        requireAlive()
        return action().also { requireAlive() }
    }

    private fun requireAlive() {
        binderFailure.get()?.let { throw IllegalStateException("Lua conformance control is unavailable", it) }
        check(binder.isBinderAlive) { "Lua conformance control Binder is not alive" }
    }

    internal class PendingControlConnection(
        private val expectedComponent: ComponentName,
    ) : ServiceConnection {
        val completion = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>()
        val failure = AtomicReference<Throwable?>()

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (name != expectedComponent) {
                failure.compareAndSet(
                    null,
                    SecurityException("Bound unexpected Lua conformance control component $name"),
                )
            } else {
                binder.compareAndSet(null, service)
            }
            completion.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            failure.compareAndSet(null, IllegalStateException("Lua conformance control disconnected: $name"))
            completion.countDown()
        }

        override fun onNullBinding(name: ComponentName) {
            failure.compareAndSet(null, IllegalStateException("Lua conformance control returned a null binding: $name"))
            completion.countDown()
        }

        override fun onBindingDied(name: ComponentName) {
            failure.compareAndSet(null, IllegalStateException("Lua conformance control binding died: $name"))
            completion.countDown()
        }
    }

    companion object {
        const val FAKE_PACKAGE = "org.autojs.plugin.lua.runtime.fake"
        const val CONTROL_SERVICE = "$FAKE_PACKAGE.service.FakeLuaConformanceControlService"
        val CONTROL_COMPONENT = ComponentName(FAKE_PACKAGE, CONTROL_SERVICE)

        fun bind(
            context: Context,
            timeoutMillis: Long = DEFAULT_BIND_TIMEOUT_MILLIS,
        ): FakeLuaConformanceControlClient {
            check(Looper.myLooper() != Looper.getMainLooper()) {
                "Lua conformance control binding must not block the Android main thread"
            }
            require(timeoutMillis > 0L) { "Lua conformance control bind timeout must be positive" }
            val applicationContext = context.applicationContext ?: context
            val connection = PendingControlConnection(CONTROL_COMPONENT)
            val didBind = applicationContext.bindService(
                Intent().setComponent(CONTROL_COMPONENT),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            check(didBind) { "Unable to bind the Lua conformance control service" }
            try {
                check(connection.completion.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    "Timed out binding the Lua conformance control service"
                }
                connection.failure.get()?.let {
                    throw IllegalStateException("Lua conformance control binding failed", it)
                }
                val binder = checkNotNull(connection.binder.get()) {
                    "Lua conformance control returned no Binder"
                }
                val control = checkNotNull(IFakeLuaConformanceControl.Stub.asInterface(binder)) {
                    "Lua conformance control interface is unavailable"
                }
                return FakeLuaConformanceControlClient(applicationContext, connection, binder, control)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                runCatching { applicationContext.unbindService(connection) }
                throw interrupted
            } catch (error: Throwable) {
                runCatching { applicationContext.unbindService(connection) }
                throw error
            }
        }

        private const val DEFAULT_BIND_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_COUNTER_TIMEOUT_MILLIS = 5_000L
        private const val COUNTER_POLL_MILLIS = 20L
    }
}

internal class FakeLuaConformanceCounters(values: LongArray) {
    private val snapshot = values.copyOf()

    init {
        require(snapshot.size == SIZE) {
            "Lua conformance counter snapshot must contain $SIZE values, got ${snapshot.size}"
        }
    }

    val pluginInfoCalls: Long get() = snapshot[PLUGIN_INFO_CALLS]
    val pluginInfoExits: Long get() = snapshot[PLUGIN_INFO_EXITS]
    val runtimeInfoCalls: Long get() = snapshot[RUNTIME_INFO_CALLS]
    val runtimeInfoExits: Long get() = snapshot[RUNTIME_INFO_EXITS]
    val createCalls: Long get() = snapshot[CREATE_CALLS]
    val createExits: Long get() = snapshot[CREATE_EXITS]
    val startCalls: Long get() = snapshot[START_CALLS]
    val cancelCalls: Long get() = snapshot[CANCEL_CALLS]
    val closeCalls: Long get() = snapshot[CLOSE_CALLS]
    val sourceFdsOpened: Long get() = snapshot[SOURCE_FDS_OPENED]
    val sourceFdsClosed: Long get() = snapshot[SOURCE_FDS_CLOSED]
    val sessionsCreated: Long get() = snapshot[SESSIONS_CREATED]
    val sessionsFinished: Long get() = snapshot[SESSIONS_FINISHED]
    val sourceValidations: Long get() = snapshot[SOURCE_VALIDATIONS]
    val startedCallbacksSent: Long get() = snapshot[STARTED_CALLBACKS_SENT]
    val outputCallbacksSent: Long get() = snapshot[OUTPUT_CALLBACKS_SENT]
    val terminalCallbacksSent: Long get() = snapshot[TERMINAL_CALLBACKS_SENT]
    val busyRejections: Long get() = snapshot[BUSY_REJECTIONS]
    val sourceReadOnlyChecks: Long get() = snapshot[SOURCE_READ_ONLY_CHECKS]
    val sourceReadOnlyConfirmed: Long get() = snapshot[SOURCE_READ_ONLY_CONFIRMED]
    val sourceEarlyEofFailures: Long get() = snapshot[SOURCE_EARLY_EOF_FAILURES]
    val sourceTrailingDataFailures: Long get() = snapshot[SOURCE_TRAILING_DATA_FAILURES]
    val sourceDigestMismatchFailures: Long get() = snapshot[SOURCE_DIGEST_MISMATCH_FAILURES]
    val nullBindingCalls: Long get() = snapshot[NULL_BINDING_CALLS]

    val sourceContentFailures: Long
        get() = sourceEarlyEofFailures + sourceTrailingDataFailures + sourceDigestMismatchFailures

    override fun toString(): String = NAMES.indices.joinToString(prefix = "[", postfix = "]") { index ->
        "${NAMES[index]}=${snapshot[index]}"
    }

    private companion object {
        const val PLUGIN_INFO_CALLS = 0
        const val PLUGIN_INFO_EXITS = 1
        const val RUNTIME_INFO_CALLS = 2
        const val RUNTIME_INFO_EXITS = 3
        const val CREATE_CALLS = 4
        const val CREATE_EXITS = 5
        const val START_CALLS = 6
        const val CANCEL_CALLS = 7
        const val CLOSE_CALLS = 8
        const val SOURCE_FDS_OPENED = 9
        const val SOURCE_FDS_CLOSED = 10
        const val SESSIONS_CREATED = 11
        const val SESSIONS_FINISHED = 12
        const val SOURCE_VALIDATIONS = 13
        const val STARTED_CALLBACKS_SENT = 14
        const val OUTPUT_CALLBACKS_SENT = 15
        const val TERMINAL_CALLBACKS_SENT = 16
        const val BUSY_REJECTIONS = 17
        const val SOURCE_READ_ONLY_CHECKS = 18
        const val SOURCE_READ_ONLY_CONFIRMED = 19
        const val SOURCE_EARLY_EOF_FAILURES = 20
        const val SOURCE_TRAILING_DATA_FAILURES = 21
        const val SOURCE_DIGEST_MISMATCH_FAILURES = 22
        const val NULL_BINDING_CALLS = 23
        const val SIZE = 24
        val NAMES = listOf(
            "pluginInfoCalls",
            "pluginInfoExits",
            "runtimeInfoCalls",
            "runtimeInfoExits",
            "createCalls",
            "createExits",
            "startCalls",
            "cancelCalls",
            "closeCalls",
            "sourceFdsOpened",
            "sourceFdsClosed",
            "sessionsCreated",
            "sessionsFinished",
            "sourceValidations",
            "startedCallbacksSent",
            "outputCallbacksSent",
            "terminalCallbacksSent",
            "busyRejections",
            "sourceReadOnlyChecks",
            "sourceReadOnlyConfirmed",
            "sourceEarlyEofFailures",
            "sourceTrailingDataFailures",
            "sourceDigestMismatchFailures",
            "nullBindingCalls",
        )
    }
}
