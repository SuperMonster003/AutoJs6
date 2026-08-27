package org.autojs.plugin.python.runtime.fake.service

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonCancellationReason
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionCancellation
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonOutputChunk
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonSessionStarted
import org.autojs.plugin.python.runtime.api.PythonTracebackFrame
import org.autojs.plugin.python.runtime.api.PythonTracebackOrigin
import org.autojs.plugin.python.runtime.fake.FakePythonCaseId
import org.autojs.plugin.python.runtime.fake.FakePythonFixture
import org.autojs.plugin.python.runtime.fake.FakePythonSessionMachine
import org.autojs.plugin.python.runtime.fake.FakePythonSnapshotMaterializer
import org.autojs.plugin.python.runtime.fake.FakePythonSnapshotSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteFakePythonExecutionSession(
    private val ownerUid: Int,
    private val request: PythonExecutionRequest,
    private val descriptors: OwnedParcelFileDescriptors,
    private val admissionCallback: IPythonSessionOpenCallback,
    private val executionCallback: IPythonExecutionCallback,
    private val callerVerifier: HostCallerVerifier,
    private val worker: ExecutorService,
    private val scheduler: ScheduledExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteFakePythonExecutionSession) -> Unit,
) : IPythonExecutionSession.Stub() {
    private val machine = FakePythonSessionMachine(
        maximumCredits = FakePythonFixture.capabilities.limits.maxOutstandingOutputCredits,
        firstOutputSequence = FakePythonFixture.FIRST_OUTPUT_SEQUENCE,
    )
    private val cleaned = AtomicBoolean(false)
    private val callbackDeathsLinked = AtomicBoolean(false)
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val signal = java.lang.Object()
    private val createdAtMillis = SystemClock.elapsedRealtime()
    private val admissionBinder = admissionCallback.asBinder()
    private val executionBinder = executionCallback.asBinder()
    private val deathRecipient = IBinder.DeathRecipient(::callbackDied)

    @Volatile
    private var workerFuture: Future<*>? = null

    @Volatile
    private var deadlineFuture: Future<*>? = null

    @Volatile
    private var snapshots: FakePythonSnapshotSet? = null

    override fun start() {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!machine.start()) return
        deadlineFuture = scheduler.schedule(
            { finishFailed(PythonErrorCode.TIMEOUT, PythonFailurePhase.EXECUTION, "Fake execution timed out") },
            request.timeoutMillis,
            TimeUnit.MILLISECONDS,
        )
        submitWorker(::runExecution)
    }

    override fun grantOutputCredits(count: Int) {
        callerVerifier.enforceSessionOwner(ownerUid)
        try {
            machine.grantCredits(count)
            synchronized(signal) { signal.notifyAll() }
        } catch (_: RuntimeException) {
            if (machine.state == FakePythonSessionMachine.State.STARTED) {
                finishFailed(PythonErrorCode.INTERNAL, PythonFailurePhase.OUTPUT, "Invalid output credit grant")
            } else {
                forceClose()
            }
        }
    }

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!machine.cancel()) return
        synchronized(signal) { signal.notifyAll() }
        workerFuture?.cancel(true)
        dispatchTerminal {
            executionCallback.onCancelled(
                PythonRuntimeCodec.encodeCancellation(
                    PythonExecutionCancellation(
                        requestId = request.requestId,
                        reason = PythonCancellationReason.REQUESTED,
                        phase = PythonFailurePhase.CANCELLATION,
                        elapsedMillis = elapsedMillis(),
                    ),
                ),
            )
        }
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        forceClose()
    }

    override fun replyInput(reply: ByteArray?) {
        callerVerifier.enforceSessionOwner(ownerUid)
        requireNotNull(reply) { "Python input reply is missing" }
        forceClose()
    }

    fun linkCallbackDeaths(): Boolean {
        if (!callbackDeathsLinked.compareAndSet(false, true)) return !cleaned.get()
        return try {
            admissionBinder.linkToDeath(deathRecipient, 0)
            executionBinder.linkToDeath(deathRecipient, 0)
            if (!admissionBinder.isBinderAlive || !executionBinder.isBinderAlive) {
                callbackDied()
                false
            } else {
                true
            }
        } catch (_: RemoteException) {
            callbackDied()
            false
        }
    }

    fun serviceDestroyed() = forceClose()

    fun forceClose() {
        machine.close()
        cleanup(cancelWorker = true)
    }

    private fun runExecution() {
        try {
            snapshots = FakePythonSnapshotMaterializer.materialize(request) { reference ->
                val maximum = when (reference.kind) {
                    PythonPayloadKind.SOURCE -> FakePythonFixture.capabilities.limits.maxSourceBytes
                    PythonPayloadKind.WORKSPACE_ARCHIVE -> FakePythonFixture.capabilities.limits.maxWorkspaceArchiveBytes
                    PythonPayloadKind.STDIN -> FakePythonFixture.capabilities.limits.maxStdinBytes
                    PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT ->
                        FakePythonFixture.capabilities.limits.maxHostCapabilitySnapshotBytes
                }
                descriptors.read(reference, maximum, ::isStopped)
            }
            descriptors.close()
            if (isStopped()) return
            runCase(FakePythonCaseId.fromArguments(request.arguments))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (!isStopped()) {
                finishFailed(
                    code = when {
                        error.message.orEmpty().contains("STDIN") -> PythonErrorCode.STDIN_REJECTED
                        error.message.orEmpty().contains("CAPABILITY", ignoreCase = true) ->
                            PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED
                        else -> PythonErrorCode.SOURCE_REJECTED
                    },
                    phase = PythonFailurePhase.INPUT_VALIDATION,
                    message = "Fake input snapshot was rejected",
                )
            }
        }
    }

    private fun runCase(caseId: FakePythonCaseId) {
        when (caseId) {
            FakePythonCaseId.NORMAL -> {
                emitStarted()
                val data = "fake-python-ok\n".toByteArray()
                val sequence = awaitCredit()
                if (sequence == null) return
                emitOutput(sequence, PythonOutputStream.STDOUT, data)
                finishResult(stdoutBytes = data.size.toLong(), stdoutChunks = 1L)
            }
            FakePythonCaseId.STRUCTURED_EXCEPTION -> {
                emitStarted()
                finishPythonException()
            }
            FakePythonCaseId.STALL -> awaitStop()
            FakePythonCaseId.OUTPUT_BEFORE_START -> {
                emitOutput(FakePythonFixture.FIRST_OUTPUT_SEQUENCE, PythonOutputStream.STDOUT, byteArrayOf(1))
                emitStarted()
                finishResult(stdoutBytes = 1L, stdoutChunks = 1L)
            }
            FakePythonCaseId.OUTPUT_WITHOUT_CREDIT -> {
                emitStarted()
                emitOutput(FakePythonFixture.FIRST_OUTPUT_SEQUENCE, PythonOutputStream.STDOUT, byteArrayOf(1))
                finishResult(stdoutBytes = 1L, stdoutChunks = 1L)
            }
            FakePythonCaseId.OVERSIZED_CHUNK -> {
                emitStarted()
                val size = minOf(
                    PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES,
                    request.maxOutputChunkBytes + 1,
                )
                val data = ByteArray(size.coerceAtLeast(1))
                emitOutput(FakePythonFixture.FIRST_OUTPUT_SEQUENCE, PythonOutputStream.STDOUT, data)
                finishResult(stdoutBytes = data.size.toLong(), stdoutChunks = 1L)
            }
            FakePythonCaseId.REORDERED_CHUNKS -> {
                emitStarted()
                emitOutput(FakePythonFixture.FIRST_OUTPUT_SEQUENCE + 1L, PythonOutputStream.STDOUT, byteArrayOf(1))
                emitOutput(FakePythonFixture.FIRST_OUTPUT_SEQUENCE, PythonOutputStream.STDERR, byteArrayOf(2))
                finishResult(stdoutBytes = 1L, stderrBytes = 1L, stdoutChunks = 1L, stderrChunks = 1L)
            }
            FakePythonCaseId.DUPLICATE_TERMINAL -> {
                emitStarted()
                finishResult()
                callbackLane.dispatch(callback = {
                    executionCallback.onResult(encodedEmptyResult(), emptyArray())
                })
            }
            FakePythonCaseId.LATE_CALLBACK -> {
                emitStarted()
                finishResult()
                scheduler.schedule(
                    {
                        callbackLane.dispatch(callback = {
                            emitOutputDirect(
                                FakePythonFixture.FIRST_OUTPUT_SEQUENCE,
                                PythonOutputStream.STDOUT,
                                byteArrayOf(1),
                            )
                        })
                    },
                    LATE_CALLBACK_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }

    private fun emitStarted() {
        val encoded = PythonRuntimeCodec.encodeSessionStarted(
            PythonSessionStarted(
                requestId = request.requestId,
                protocolVersion = request.protocolVersion,
                pythonVersion = FakePythonFixture.PYTHON_VERSION,
                firstOutputSequence = FakePythonFixture.FIRST_OUTPUT_SEQUENCE,
                effectiveMaxOutputBytes = request.maxOutputBytes,
                effectiveMaxOutputChunkBytes = request.maxOutputChunkBytes,
                effectiveMaxOutputChunks = request.maxOutputChunks,
                runtimeGeneration = FakePythonFixture.RUNTIME_GENERATION,
            ),
        )
        callbackLane.dispatch({ executionCallback.onStarted(encoded) }, ::callbackFailed)
    }

    private fun emitOutput(sequence: Long, stream: PythonOutputStream, bytes: ByteArray) {
        callbackLane.dispatch({ emitOutputDirect(sequence, stream, bytes) }, ::callbackFailed)
    }

    private fun emitOutputDirect(sequence: Long, stream: PythonOutputStream, bytes: ByteArray) {
        val encoded = PythonRuntimeCodec.encodeOutputChunk(
            PythonOutputChunk(request.requestId, sequence, stream, bytes),
        )
        when (stream) {
            PythonOutputStream.STDOUT -> executionCallback.onStdout(encoded)
            PythonOutputStream.STDERR -> executionCallback.onStderr(encoded)
        }
    }

    private fun finishResult(
        stdoutBytes: Long = 0L,
        stderrBytes: Long = 0L,
        stdoutChunks: Long = 0L,
        stderrChunks: Long = 0L,
    ) {
        if (!machine.finish(FakePythonSessionMachine.Terminal.RESULT)) return
        val encoded = PythonRuntimeCodec.encodeExecutionResult(
            PythonExecutionResult(
                requestId = request.requestId,
                exitCode = 0,
                elapsedMillis = elapsedMillis(),
                stdoutBytes = stdoutBytes,
                stderrBytes = stderrBytes,
                stdoutChunks = stdoutChunks,
                stderrChunks = stderrChunks,
            ),
        )
        dispatchTerminal { executionCallback.onResult(encoded, emptyArray()) }
    }

    private fun encodedEmptyResult(): ByteArray = PythonRuntimeCodec.encodeExecutionResult(
        PythonExecutionResult(request.requestId, 0, elapsedMillis(), 0L, 0L, 0L, 0L),
    )

    private fun finishPythonException() {
        if (!machine.finish(FakePythonSessionMachine.Terminal.FAILED)) return
        val encoded = PythonRuntimeCodec.encodeExecutionError(
            PythonExecutionError(
                requestId = request.requestId,
                code = PythonErrorCode.PYTHON_EXCEPTION,
                phase = PythonFailurePhase.EXECUTION,
                message = "Fake Python exception",
                retryableBeforeDispatch = false,
                exceptionType = "RuntimeError",
                exceptionMessage = "deterministic fake failure",
                traceback = listOf(
                    PythonTracebackFrame(
                        origin = PythonTracebackOrigin.PROJECT,
                        fileName = request.entryPoint,
                        lineNumber = 1,
                        functionName = "<module>",
                        sourceLine = "raise RuntimeError()",
                    ),
                ),
            ),
        )
        dispatchTerminal { executionCallback.onFailed(encoded) }
    }

    private fun finishFailed(code: PythonErrorCode, phase: PythonFailurePhase, message: String) {
        if (!machine.finish(FakePythonSessionMachine.Terminal.FAILED)) return
        synchronized(signal) { signal.notifyAll() }
        workerFuture?.cancel(true)
        val encoded = PythonRuntimeCodec.encodeExecutionError(
            PythonExecutionError(request.requestId, code, phase, message, retryableBeforeDispatch = false),
        )
        dispatchTerminal { executionCallback.onFailed(encoded) }
    }

    private fun dispatchTerminal(callback: () -> Unit) {
        callbackLane.dispatch(
            callback = {
                try {
                    callback()
                } finally {
                    cleanup(cancelWorker = false)
                }
            },
            onFailure = { cleanup(cancelWorker = true) },
        )
    }

    private fun awaitCredit(): Long? {
        synchronized(signal) {
            while (!isStopped() && machine.outstandingCredits == 0) signal.wait(100L)
        }
        if (isStopped()) return null
        return machine.consumeOutputCredit()
    }

    private fun awaitStop() {
        synchronized(signal) {
            while (!isStopped()) signal.wait(100L)
        }
    }

    private fun submitWorker(block: () -> Unit) {
        try {
            workerFuture = worker.submit(block)
        } catch (_: RejectedExecutionException) {
            finishFailed(PythonErrorCode.INTERNAL, PythonFailurePhase.RUNTIME_START, "Fake worker unavailable")
        }
    }

    private fun callbackFailed(@Suppress("UNUSED_PARAMETER") error: Throwable) = callbackDied()

    private fun callbackDied() = forceClose()

    private fun cleanup(cancelWorker: Boolean) {
        if (!cleaned.compareAndSet(false, true)) return
        deadlineFuture?.cancel(false)
        if (cancelWorker) workerFuture?.cancel(true)
        descriptors.close()
        snapshots?.close()
        unlinkCallbackDeaths()
        synchronized(signal) { signal.notifyAll() }
        onFinished(this)
    }

    private fun unlinkCallbackDeaths() {
        if (!callbackDeathsLinked.compareAndSet(true, false)) return
        runCatching { admissionBinder.unlinkToDeath(deathRecipient, 0) }
        runCatching { executionBinder.unlinkToDeath(deathRecipient, 0) }
    }

    private fun isStopped(): Boolean =
        cleaned.get() || machine.state == FakePythonSessionMachine.State.TERMINAL ||
            machine.state == FakePythonSessionMachine.State.CLOSED || Thread.currentThread().isInterrupted

    private fun elapsedMillis(): Long = (SystemClock.elapsedRealtime() - createdAtMillis).coerceAtLeast(0L)

    private companion object {
        const val LATE_CALLBACK_DELAY_MILLIS = 100L
    }
}
