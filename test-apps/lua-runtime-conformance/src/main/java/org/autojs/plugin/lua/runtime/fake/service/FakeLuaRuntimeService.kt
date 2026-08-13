package org.autojs.plugin.lua.runtime.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.lua.runtime.api.ILuaExecutionCallback
import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityBroker
import org.autojs.plugin.lua.runtime.api.ILuaRuntimeProvider
import org.autojs.plugin.lua.runtime.api.LuaCancellationReason
import org.autojs.plugin.lua.runtime.api.LuaExecutionCancellation
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeValidation
import org.autojs.plugin.lua.runtime.api.LuaSha256
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceCounterContract
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceGeneration
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceState
import org.autojs.plugin.lua.runtime.fake.FakeLuaRuntimeFixture
import org.autojs.plugin.lua.runtime.fake.FakeLuaRuntimeScenario
import org.autojs.plugin.lua.runtime.fake.TrackedSourceDescriptor
import org.autojs.plugin.lua.runtime.fake.closeQuietly
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

abstract class FakeLuaRuntimeService : Service() {
    protected abstract val scenario: FakeLuaRuntimeScenario

    private lateinit var callerVerifier: HostCallerVerifier
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeLuaSession>()

    override fun onCreate() {
        super.onCreate()
        verifyManifestScenario()
        callerVerifier = HostCallerVerifier(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.toList().forEach(RemoteFakeLuaSession::serviceDestroyed)
        sessions.clear()
        super.onDestroy()
    }

    private val binder = object : ILuaRuntimeProvider.Stub() {
        override fun getRuntimeInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            val generation = FakeLuaConformanceState.capture()
            generation.record(FakeLuaConformanceCounterContract.RUNTIME_INFO_CALLS)
            return try {
                when (scenario) {
                    FakeLuaRuntimeScenario.MALFORMED_RUNTIME_INFO -> byteArrayOf(1, 2, 3)
                    FakeLuaRuntimeScenario.STALL_RUNTIME_INFO -> {
                        generation.awaitRuntimeInfoRelease()
                        LuaRuntimeCodec.encodeRuntimeInfo(FakeLuaRuntimeFixture.runtimeInfo())
                    }
                    else -> LuaRuntimeCodec.encodeRuntimeInfo(FakeLuaRuntimeFixture.runtimeInfo())
                }
            } finally {
                generation.record(FakeLuaConformanceCounterContract.RUNTIME_INFO_EXITS)
            }
        }

        override fun createExecution(
            requestMetadata: ByteArray?,
            source: ParcelFileDescriptor?,
            callback: ILuaExecutionCallback?,
            hostBroker: ILuaHostCapabilityBroker?,
        ): ILuaExecutionSession? {
            val generation = FakeLuaConformanceState.capture()
            generation.record(FakeLuaConformanceCounterContract.CREATE_CALLS)
            try {
                val ownerUid = try {
                    callerVerifier.enforceAllowedCaller()
                } catch (error: Throwable) {
                    closeQuietly(source)
                    throw error
                }
                if (requestMetadata == null || source == null || callback == null || hostBroker == null) {
                    closeQuietly(source)
                    throw IllegalArgumentException("Lua fixture execution arguments must not be null")
                }
                val request = decodeAndValidateRequest(requestMetadata, source)
                val ownedSource = when (scenario) {
                    FakeLuaRuntimeScenario.STALL_CREATE,
                    FakeLuaRuntimeScenario.LATE_CREATE_RETURN_SESSION,
                    -> awaitCreateRelease(generation, source)
                    FakeLuaRuntimeScenario.ZERO_CREDIT_OUTPUT -> awaitZeroCreditOutputRelease(
                        generation = generation,
                        incoming = source,
                        request = request,
                        callback = callback,
                    )
                    else -> generation.duplicateIncoming(source)
                }
                verifyAndCloseSource(generation, ownedSource, request)
                val busyOnStart = scenario == FakeLuaRuntimeScenario.BUSY_SECOND_CREATE && sessions.isNotEmpty()
                val session = newSession(
                    generation = generation,
                    ownerUid = ownerUid,
                    request = request,
                    callback = callback,
                    busyOnStart = busyOnStart,
                )
                if (scenario == FakeLuaRuntimeScenario.CALLBACK_BEFORE_START) {
                    try {
                        generation.record(FakeLuaConformanceCounterContract.STARTED_CALLBACKS_SENT)
                        callback.onStarted(
                            LuaRuntimeCodec.encodeStarted(FakeLuaRuntimeFixture.started(request)),
                        )
                    } catch (error: Throwable) {
                        session.serviceDestroyed()
                        throw error
                    }
                }
                return session
            } finally {
                generation.record(FakeLuaConformanceCounterContract.CREATE_EXITS)
            }
        }
    }

    private fun verifyAndCloseSource(
        generation: FakeLuaConformanceGeneration,
        source: TrackedSourceDescriptor,
        request: LuaExecutionRequest,
    ) {
        try {
            generation.record(FakeLuaConformanceCounterContract.SOURCE_READ_ONLY_CHECKS)
            require(source.hasReadOnlyAccessMode()) { "Lua fixture source descriptor is writable" }
            generation.record(FakeLuaConformanceCounterContract.SOURCE_READ_ONLY_CONFIRMED)

            val digest = MessageDigest.getInstance("SHA-256")
            source.openInputStream().use { input ->
                val buffer = ByteArray(SOURCE_READ_BUFFER_BYTES)
                var remaining = request.sourceLengthBytes
                while (remaining > 0L) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count <= 0) {
                        generation.record(FakeLuaConformanceCounterContract.SOURCE_EARLY_EOF_FAILURES)
                        throw IllegalArgumentException(
                            "Lua fixture source reached EOF before its declared length",
                        )
                    }
                    digest.update(buffer, 0, count)
                    remaining -= count.toLong()
                }
                if (input.read() != -1) {
                    generation.record(FakeLuaConformanceCounterContract.SOURCE_TRAILING_DATA_FAILURES)
                    throw IllegalArgumentException(
                        "Lua fixture source contains bytes after its declared length",
                    )
                }
            }
            if (LuaSha256.fromBytes(digest.digest()) != request.sourceSha256) {
                generation.record(FakeLuaConformanceCounterContract.SOURCE_DIGEST_MISMATCH_FAILURES)
                throw IllegalArgumentException(
                    "Lua fixture source SHA-256 differs from its request metadata",
                )
            }
            generation.record(FakeLuaConformanceCounterContract.SOURCE_VALIDATIONS)
        } finally {
            source.close()
        }
    }

    private fun decodeAndValidateRequest(
        requestMetadata: ByteArray,
        source: ParcelFileDescriptor,
    ): LuaExecutionRequest = try {
        LuaRuntimeCodec.decodeExecutionRequest(requestMetadata).also { request ->
            LuaRuntimeValidation.validateRequestAgainst(
                request,
                FakeLuaRuntimeFixture.runtimeInfo(),
                request.protocolVersion,
            )
        }
    } catch (error: Throwable) {
        closeQuietly(source)
        throw error
    }

    private fun awaitCreateRelease(
        generation: FakeLuaConformanceGeneration,
        incoming: ParcelFileDescriptor,
    ): TrackedSourceDescriptor {
        val stalledSource = generation.adoptIncoming(incoming)
        try {
            generation.awaitCreateExecutionRelease()
            if (scenario == FakeLuaRuntimeScenario.STALL_CREATE) {
                throw IllegalStateException("Stalled create-execution fixture was explicitly released")
            }
            return stalledSource.duplicate()
        } finally {
            stalledSource.close()
        }
    }

    private fun awaitZeroCreditOutputRelease(
        generation: FakeLuaConformanceGeneration,
        incoming: ParcelFileDescriptor,
        request: LuaExecutionRequest,
        callback: ILuaExecutionCallback,
    ): TrackedSourceDescriptor {
        val stalledSource = generation.adoptIncoming(incoming)
        try {
            generation.record(FakeLuaConformanceCounterContract.OUTPUT_CALLBACKS_SENT)
            callback.onOutput(
                LuaRuntimeCodec.encodeOutput(
                    FakeLuaRuntimeFixture.output(request, sequence = 0L, text = "without-credit"),
                ),
            )
            generation.awaitCreateExecutionRelease()
            return stalledSource.duplicate()
        } finally {
            stalledSource.close()
        }
    }

    private fun newSession(
        generation: FakeLuaConformanceGeneration,
        ownerUid: Int,
        request: LuaExecutionRequest,
        callback: ILuaExecutionCallback,
        busyOnStart: Boolean,
    ): RemoteFakeLuaSession {
        val session = RemoteFakeLuaSession(
            generation = generation,
            ownerUid = ownerUid,
            request = request,
            callback = callback,
            scenario = scenario,
            busyOnStart = busyOnStart,
            callerVerifier = callerVerifier,
            onFinished = { finished -> sessions.remove(finished) },
        )
        sessions += session
        generation.record(FakeLuaConformanceCounterContract.SESSIONS_CREATED)
        return session
    }

    @Suppress("DEPRECATION")
    private fun verifyManifestScenario() {
        val info = packageManager.getServiceInfo(
            ComponentName(this, javaClass),
            PackageManager.GET_META_DATA,
        )
        val declared = info.metaData?.getString(FakeLuaRuntimeScenario.METADATA_KEY)
            ?: error("Lua fixture service is missing scenario metadata")
        check(FakeLuaRuntimeScenario.fromMetadata(declared) == scenario) {
            "Lua fixture service scenario does not match its manifest metadata"
        }
    }

    private companion object {
        const val SOURCE_READ_BUFFER_BYTES = 8 * 1024
    }
}

class NormalLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.NORMAL
}

class MalformedRuntimeInfoLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.MALFORMED_RUNTIME_INFO
}

/** Returns a deterministic null binding without terminating the shared fixture process. */
class NullBindingLuaRuntimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == null && intent?.component == ComponentName(this, javaClass)) {
            FakeLuaConformanceState.capture().record(FakeLuaConformanceCounterContract.NULL_BINDING_CALLS)
        }
        return null
    }
}

class StallRuntimeInfoLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.STALL_RUNTIME_INFO
}

class StallCreateLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.STALL_CREATE
}

class LateCreateReturnSessionLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.LATE_CREATE_RETURN_SESSION
}

class CallbackBeforeStartLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.CALLBACK_BEFORE_START
}

class DuplicateTerminalLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.DUPLICATE_TERMINAL
}

class IgnoreCancelLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.IGNORE_CANCEL
}

class ZeroCreditOutputLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.ZERO_CREDIT_OUTPUT
}

class SequenceGapLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.SEQUENCE_GAP
}

class SequenceReplayLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.SEQUENCE_REPLAY
}

class WrongRequestIdLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.WRONG_REQUEST_ID
}

class BusySecondCreateLuaRuntimeService : FakeLuaRuntimeService() {
    override val scenario = FakeLuaRuntimeScenario.BUSY_SECOND_CREATE
}

private class RemoteFakeLuaSession(
    private val generation: FakeLuaConformanceGeneration,
    private val ownerUid: Int,
    private val request: LuaExecutionRequest,
    private val callback: ILuaExecutionCallback,
    private val scenario: FakeLuaRuntimeScenario,
    private val busyOnStart: Boolean,
    private val callerVerifier: HostCallerVerifier,
    private val onFinished: (RemoteFakeLuaSession) -> Unit,
) : ILuaExecutionSession.Stub() {
    private val started = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val outputCredits = AtomicInteger(0)

    override fun start() {
        callerVerifier.enforceSessionOwner(ownerUid)
        generation.record(FakeLuaConformanceCounterContract.START_CALLS)
        if (closed.get() || !started.compareAndSet(false, true)) return
        when (scenario) {
            FakeLuaRuntimeScenario.CALLBACK_BEFORE_START -> Unit
            FakeLuaRuntimeScenario.IGNORE_CANCEL -> sendStarted()
            FakeLuaRuntimeScenario.DUPLICATE_TERMINAL -> emitDuplicateTerminal()
            FakeLuaRuntimeScenario.SEQUENCE_GAP -> emitSequenceGap()
            FakeLuaRuntimeScenario.SEQUENCE_REPLAY -> emitSequenceReplay()
            FakeLuaRuntimeScenario.WRONG_REQUEST_ID -> emitWrongRequestId()
            FakeLuaRuntimeScenario.BUSY_SECOND_CREATE -> {
                if (busyOnStart) emitBusyTerminal() else sendStarted()
            }
            else -> emitNormalTerminal()
        }
    }

    override fun grantOutputCredits(count: Int) {
        callerVerifier.enforceSessionOwner(ownerUid)
        require(count > 0) { "Lua fixture output credits must be positive" }
        outputCredits.updateAndGet { current ->
            if (current > Int.MAX_VALUE - count) Int.MAX_VALUE else current + count
        }
    }

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        generation.record(FakeLuaConformanceCounterContract.CANCEL_CALLS)
        if (closed.get() || terminal.get() || scenario == FakeLuaRuntimeScenario.IGNORE_CANCEL) return
        if (terminal.compareAndSet(false, true)) {
            try {
                sendCancelled(
                    LuaRuntimeCodec.encodeCancellation(
                        LuaExecutionCancellation(
                            requestId = request.requestId,
                            reason = LuaCancellationReason.REQUESTED,
                            elapsedMillis = 0L,
                        ),
                    ),
                )
            } finally {
                finish()
            }
        }
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        generation.record(FakeLuaConformanceCounterContract.CLOSE_CALLS)
        if (closed.compareAndSet(false, true)) finish()
    }

    fun serviceDestroyed() {
        closed.set(true)
        finish()
    }

    private fun emitNormalTerminal() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            sendStarted()
            sendCompleted()
        } finally {
            finish()
        }
    }

    private fun emitDuplicateTerminal() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            sendStarted()
            sendCompleted()
            sendCompleted()
        } finally {
            finish()
        }
    }

    private fun emitSequenceGap() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            sendStarted()
            sendOutput(sequence = 1L, text = "gap")
            sendCompleted()
        } finally {
            finish()
        }
    }

    private fun emitSequenceReplay() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            sendStarted()
            sendOutput(sequence = 0L, text = "accepted")
            sendOutput(sequence = 0L, text = "replay")
            sendCompleted()
        } finally {
            finish()
        }
    }

    private fun emitWrongRequestId() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            sendStarted()
            generation.record(FakeLuaConformanceCounterContract.OUTPUT_CALLBACKS_SENT)
            callback.onOutput(
                LuaRuntimeCodec.encodeOutput(
                    FakeLuaRuntimeFixture.output(
                        request = request,
                        sequence = 0L,
                        text = "wrong-request-id",
                        requestId = FakeLuaRuntimeFixture.differentRequestId(request),
                    ),
                ),
            )
            sendCompleted()
        } finally {
            finish()
        }
    }

    private fun emitBusyTerminal() {
        if (!terminal.compareAndSet(false, true)) return
        try {
            generation.record(FakeLuaConformanceCounterContract.BUSY_REJECTIONS)
            generation.record(FakeLuaConformanceCounterContract.TERMINAL_CALLBACKS_SENT)
            callback.onFailed(LuaRuntimeCodec.encodeError(FakeLuaRuntimeFixture.busyError(request)))
        } finally {
            finish()
        }
    }

    private fun sendStarted() {
        generation.record(FakeLuaConformanceCounterContract.STARTED_CALLBACKS_SENT)
        callback.onStarted(encodedStarted())
    }

    private fun sendOutput(sequence: Long, text: String) {
        generation.record(FakeLuaConformanceCounterContract.OUTPUT_CALLBACKS_SENT)
        callback.onOutput(
            LuaRuntimeCodec.encodeOutput(FakeLuaRuntimeFixture.output(request, sequence, text)),
        )
    }

    private fun sendCompleted() {
        generation.record(FakeLuaConformanceCounterContract.TERMINAL_CALLBACKS_SENT)
        callback.onCompleted(encodedResult(), emptyArray<ParcelFileDescriptor>())
    }

    private fun sendCancelled(cancellation: ByteArray) {
        generation.record(FakeLuaConformanceCounterContract.TERMINAL_CALLBACKS_SENT)
        callback.onCancelled(cancellation)
    }

    private fun encodedStarted(): ByteArray =
        LuaRuntimeCodec.encodeStarted(FakeLuaRuntimeFixture.started(request))

    private fun encodedResult(): ByteArray =
        LuaRuntimeCodec.encodeResult(FakeLuaRuntimeFixture.result(request))

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        generation.record(FakeLuaConformanceCounterContract.SESSIONS_FINISHED)
        onFinished(this)
    }
}
