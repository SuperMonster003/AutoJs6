package org.autojs.plugin.lua.runtime.fake

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLongArray

/** Stable indexes for the test-only control service's counter snapshot. */
object FakeLuaConformanceCounterContract {
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

    val names: List<String> = listOf(
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

internal object FakeLuaConformanceState {
    private val controlLock = Any()

    @Volatile
    private var current = FakeLuaConformanceGeneration()

    fun capture(): FakeLuaConformanceGeneration = current

    fun reset() {
        val previous = synchronized(controlLock) {
            current.also { current = FakeLuaConformanceGeneration() }
        }
        previous.closeAndRelease()
    }

    fun releasePluginInfoStall() = synchronized(controlLock) {
        current.releasePluginInfoStall()
    }

    fun releaseRuntimeInfoStall() = synchronized(controlLock) {
        current.releaseRuntimeInfoStall()
    }

    fun releaseCreateExecutionStall() = synchronized(controlLock) {
        current.releaseCreateExecutionStall()
    }

    fun releaseAllStalls() = synchronized(controlLock) {
        current.releaseAllStalls()
    }

    fun counterSnapshot(): LongArray = synchronized(controlLock) {
        current.counterSnapshot()
    }
}

internal class FakeLuaConformanceGeneration {
    private val pluginInfoStall = CountDownLatch(1)
    private val runtimeInfoStall = CountDownLatch(1)
    private val createExecutionStall = CountDownLatch(1)
    private val counters = AtomicLongArray(FakeLuaConformanceCounterContract.SIZE)
    private val resourceLock = Any()
    private val resources = linkedSetOf<TrackedSourceDescriptor>()
    private var closed = false

    fun record(index: Int) {
        require(index in 0 until FakeLuaConformanceCounterContract.SIZE)
        counters.incrementAndGet(index)
    }

    fun awaitPluginInfoRelease() = pluginInfoStall.await()

    fun awaitRuntimeInfoRelease() = runtimeInfoStall.await()

    fun awaitCreateExecutionRelease() = createExecutionStall.await()

    fun releasePluginInfoStall() = pluginInfoStall.countDown()

    fun releaseRuntimeInfoStall() = runtimeInfoStall.countDown()

    fun releaseCreateExecutionStall() = createExecutionStall.countDown()

    fun releaseAllStalls() {
        releasePluginInfoStall()
        releaseRuntimeInfoStall()
        releaseCreateExecutionStall()
    }

    fun duplicateIncoming(incoming: ParcelFileDescriptor): TrackedSourceDescriptor {
        val adopted = adoptIncoming(incoming)
        try {
            return adopted.duplicate()
        } finally {
            adopted.close()
        }
    }

    fun adoptIncoming(incoming: ParcelFileDescriptor): TrackedSourceDescriptor = track(incoming)

    internal fun duplicateOwned(source: ParcelFileDescriptor): TrackedSourceDescriptor =
        track(ParcelFileDescriptor.dup(source.fileDescriptor))

    fun counterSnapshot(): LongArray = LongArray(FakeLuaConformanceCounterContract.SIZE) { index ->
        counters.get(index)
    }

    fun closeAndRelease() {
        val snapshot = synchronized(resourceLock) {
            if (closed) return
            closed = true
            resources.toList()
        }
        releaseAllStalls()
        snapshot.forEach(TrackedSourceDescriptor::close)
    }

    private fun track(descriptor: ParcelFileDescriptor): TrackedSourceDescriptor {
        val tracked = TrackedSourceDescriptor(this, descriptor)
        synchronized(resourceLock) {
            if (closed) {
                closeQuietly(descriptor)
                throw IllegalStateException("Lua conformance generation is closed")
            }
            resources += tracked
            record(FakeLuaConformanceCounterContract.SOURCE_FDS_OPENED)
        }
        return tracked
    }

    internal fun descriptorClosed(value: TrackedSourceDescriptor) {
        val removed = synchronized(resourceLock) { resources.remove(value) }
        if (removed) record(FakeLuaConformanceCounterContract.SOURCE_FDS_CLOSED)
    }
}

internal class TrackedSourceDescriptor(
    private val generation: FakeLuaConformanceGeneration,
    private val descriptor: ParcelFileDescriptor,
) : Closeable {
    private val closed = AtomicBoolean(false)

    fun duplicate(): TrackedSourceDescriptor = generation.duplicateOwned(descriptor)

    /**
     * API-24-compatible, non-mutating access-mode probe for a regular source file.
     * Linux rejects even a zero-length pwrite with EBADF when the descriptor lacks write access;
     * a writable descriptor returns zero without changing the file.
     */
    fun hasReadOnlyAccessMode(): Boolean {
        check(!closed.get()) { "Tracked Lua source descriptor is closed" }
        val probe = duplicate()
        return try {
            try {
                val written = Os.pwrite(
                    probe.descriptor.fileDescriptor,
                    ZERO_LENGTH_WRITE_PROBE,
                    0,
                    0,
                    0L,
                )
                check(written == 0) { "Zero-length Lua source write probe returned $written" }
                false
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EBADF) throw error
                true
            }
        } finally {
            probe.close()
        }
    }

    fun openInputStream(): InputStream {
        check(!closed.get()) { "Tracked Lua source descriptor is closed" }
        return object : FilterInputStream(ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            override fun close() {
                if (!this@TrackedSourceDescriptor.closed.compareAndSet(false, true)) return
                try {
                    super.close()
                } finally {
                    generation.descriptorClosed(this@TrackedSourceDescriptor)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeQuietly(descriptor)
        generation.descriptorClosed(this)
    }
}

internal fun closeQuietly(descriptor: ParcelFileDescriptor?) {
    runCatching { descriptor?.close() }
}

private val ZERO_LENGTH_WRITE_PROBE = ByteArray(0)
