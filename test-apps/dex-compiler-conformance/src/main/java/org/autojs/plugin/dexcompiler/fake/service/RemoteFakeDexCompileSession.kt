package org.autojs.plugin.dexcompiler.fake.service

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import org.autojs.plugin.dexcompiler.api.DexCompileCancellation
import org.autojs.plugin.dexcompiler.api.DexCompileError
import org.autojs.plugin.dexcompiler.api.DexCompileProgress
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompileResult
import org.autojs.plugin.dexcompiler.api.DexCompileStarted
import org.autojs.plugin.dexcompiler.api.DexCompilerCancellationReason
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerContractException
import org.autojs.plugin.dexcompiler.api.DexCompilerContractViolation
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerProgressStage
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.IDexCompilerCallback
import org.autojs.plugin.dexcompiler.api.IDexCompilerSession
import org.autojs.plugin.dexcompiler.fake.FakeDexCompileFailure
import org.autojs.plugin.dexcompiler.fake.FakeDexCompilerEngine
import org.autojs.plugin.dexcompiler.fake.FakeDexCompilerFixture
import org.autojs.plugin.dexcompiler.fake.FakeDexCompilerScenario
import org.autojs.plugin.dexcompiler.fake.SingleShotByteWriter
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class RemoteFakeDexCompileSession(
    private val ownerUid: Int,
    requestMetadata: ByteArray,
    private val descriptors: OwnedParcelFileDescriptors,
    private val callback: IDexCompilerCallback,
    private val scenario: FakeDexCompilerScenario,
    private val callerVerifier: HostCallerVerifier,
    private val worker: ExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteFakeDexCompileSession) -> Unit,
) : IDexCompilerSession.Stub() {
    private val decodedRequest = runCatching { DexCompilerCodec.decodeRequest(requestMetadata) }
    private val requestIdHint = decodedRequest.getOrNull()?.requestId ?: ZERO_REQUEST_ID
    private val engine = FakeDexCompilerEngine()
    private val outputWriter = SingleShotByteWriter()
    private val terminal = AtomicBoolean(false)
    private val cleaned = AtomicBoolean(false)
    private val callbackDeathLinked = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val createdAtMillis = SystemClock.elapsedRealtime()
    private val callbackBinder = callback.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient { callbackDied() }

    @Volatile
    private var workerFuture: Future<*>? = null

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        finishCancellation(DexCompilerCancellationReason.REQUESTED)
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        finishCancellation(DexCompilerCancellationReason.SESSION_CLOSED)
    }

    fun start() {
        if (!linkCallbackDeath()) {
            return
        }
        submitWorker(::runCompilation)
    }

    fun rejectBusy() {
        if (!linkCallbackDeath()) {
            return
        }
        finishError(
            requestId = requestIdHint,
            code = DexCompilerErrorCode.BUSY,
            phase = DexCompilerFailurePhase.QUEUE,
            message = "DEX compiler fixture already has an active session",
        )
    }

    fun serviceDestroyed() {
        if (terminal.compareAndSet(false, true)) {
            cleanup(cancelWorker = true)
        }
    }

    private fun runCompilation() {
        val request = try {
            decodedRequest.getOrThrow().also { value ->
                DexCompilerValidation.validateRequestAgainst(
                    value,
                    FakeDexCompilerFixture.capabilities,
                    FakeDexCompilerFixture.protocolVersion,
                )
            }
        } catch (error: DexCompilerContractException) {
            finishContractError(error)
            return
        } catch (error: Throwable) {
            finishError(
                requestIdHint,
                DexCompilerErrorCode.INVALID_REQUEST,
                DexCompilerFailurePhase.INPUT_VALIDATION,
                error.message ?: "Compile request metadata is malformed",
            )
            return
        }

        try {
            ensureActive()
            if (scenario == FakeDexCompilerScenario.PROGRESS_BEFORE_START) {
                emitProgress(request, DexCompilerProgressStage.QUEUED)
            }
            emitStarted(request)
            emitProgress(request, DexCompilerProgressStage.VALIDATING)

            if (scenario == FakeDexCompilerScenario.STALL) {
                return
            }

            ensureActive()
            emitProgress(request, DexCompilerProgressStage.COMPILING)
            val input = ParcelFileDescriptor.AutoCloseInputStream(descriptors.program)
            val artifact = try {
                engine.compile(request, input, scenario).also {
                    descriptors.program.checkError()
                }
            } finally {
                input.close()
            }

            ensureActive()
            emitProgress(request, DexCompilerProgressStage.WRITING)
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(descriptors.output).use { output ->
                    outputWriter.write(artifact.bytes(), output)
                }
            } catch (error: IOException) {
                throw FakeDexCompileFailure(
                    DexCompilerErrorCode.STORAGE_EXHAUSTED,
                    DexCompilerFailurePhase.OUTPUT_WRITE,
                    "Failed to write the fixture DEX ZIP",
                    error,
                )
            }

            ensureActive()
            finishCompleted(request, artifact)
        } catch (_: SessionStopped) {
        } catch (error: FakeDexCompileFailure) {
            finishError(request.requestId, error.code, error.phase, error.message ?: "Fixture compilation failed")
        } catch (error: Throwable) {
            if (!terminal.get()) {
                finishError(
                    request.requestId,
                    DexCompilerErrorCode.INTERNAL,
                    DexCompilerFailurePhase.COMPILATION,
                    error.message ?: "Unexpected fixture compiler failure",
                )
            }
        }
    }

    private fun emitStarted(request: DexCompileRequest) {
        val payload = DexCompilerCodec.encodeStarted(
            DexCompileStarted(
                requestId = request.requestId,
                sequence = sequence.getAndIncrement(),
                protocolVersion = request.protocolVersion,
                compilerFamily = DexCompilerFamily.D8,
                compilerVersion = FakeDexCompilerFixture.PROVIDER_VERSION,
                runtimeLibraryModel = request.runtimeLibraryModel,
                runtimeLibraryFingerprint = FakeDexCompilerFixture.runtimeLibraryFingerprint,
                queueElapsedMillis = elapsedMillis(),
            )
        )
        dispatchCallback("started") { callback.onStarted(payload) }
    }

    private fun emitProgress(request: DexCompileRequest, stage: DexCompilerProgressStage) {
        val payload = DexCompilerCodec.encodeProgress(
            DexCompileProgress(
                requestId = request.requestId,
                sequence = sequence.getAndIncrement(),
                stage = stage,
            )
        )
        dispatchCallback("progress") { callback.onProgress(payload) }
    }

    private fun finishCompleted(
        request: DexCompileRequest,
        artifact: org.autojs.plugin.dexcompiler.fake.FakeDexArtifact,
    ) {
        if (!terminal.compareAndSet(false, true)) {
            return
        }
        try {
            val payload = DexCompilerCodec.encodeResult(
                DexCompileResult(
                    requestId = request.requestId,
                    compilerFamily = DexCompilerFamily.D8,
                    compilerVersion = FakeDexCompilerFixture.PROVIDER_VERSION,
                    mode = request.mode,
                    minApi = request.minApi,
                    outputFormat = request.outputFormat,
                    runtimeLibraryFingerprint = FakeDexCompilerFixture.runtimeLibraryFingerprint,
                    outputSizeBytes = artifact.outputSizeBytes,
                    outputSha256 = artifact.outputSha256,
                    dexEntryCount = artifact.dexEntryCount,
                    elapsedMillis = elapsedMillis(),
                )
            )
            val terminalCount = if (scenario == FakeDexCompilerScenario.DUPLICATE_TERMINAL) 2 else 1
            repeat(terminalCount) {
                dispatchCallback("completed") { callback.onCompleted(payload) }
            }
        } finally {
            cleanup(cancelWorker = false)
        }
    }

    private fun finishContractError(error: DexCompilerContractException) {
        val (code, phase) = when (error.violation) {
            DexCompilerContractViolation.PROTOCOL_INCOMPATIBLE ->
                DexCompilerErrorCode.UNSUPPORTED_PROTOCOL to DexCompilerFailurePhase.NEGOTIATION
            DexCompilerContractViolation.CAPABILITY_INCOMPATIBLE ->
                DexCompilerErrorCode.UNSUPPORTED_CAPABILITY to DexCompilerFailurePhase.NEGOTIATION
            else -> DexCompilerErrorCode.INVALID_REQUEST to DexCompilerFailurePhase.INPUT_VALIDATION
        }
        finishError(requestIdHint, code, phase, error.message ?: "Compile request is incompatible")
    }

    private fun finishError(
        requestId: DexRequestId,
        code: DexCompilerErrorCode,
        phase: DexCompilerFailurePhase,
        message: String,
    ) {
        if (!terminal.compareAndSet(false, true)) {
            return
        }
        try {
            val payload = DexCompilerCodec.encodeError(
                DexCompileError(
                    requestId = requestId,
                    code = code,
                    phase = phase,
                    message = boundedErrorMessage(message),
                    retryable = code == DexCompilerErrorCode.BUSY,
                )
            )
            dispatchCallback("failed") { callback.onFailed(payload) }
        } finally {
            cleanup(cancelWorker = false)
        }
    }

    private fun finishCancellation(reason: DexCompilerCancellationReason) {
        if (!terminal.compareAndSet(false, true)) {
            return
        }
        try {
            val payload = DexCompilerCodec.encodeCancellation(
                DexCompileCancellation(
                    requestId = requestIdHint,
                    reason = reason,
                    phase = DexCompilerFailurePhase.CLEANUP,
                    elapsedMillis = elapsedMillis(),
                )
            )
            dispatchCallback("cancelled") { callback.onCancelled(payload) }
        } finally {
            cleanup(cancelWorker = true)
        }
    }

    private fun dispatchCallback(label: String, block: () -> Unit) {
        callbackLane.dispatch(label, block) { callbackFailed() }
    }

    private fun callbackFailed() {
        if (terminal.compareAndSet(false, true)) {
            cleanup(cancelWorker = true)
        }
    }

    private fun callbackDied() {
        if (terminal.compareAndSet(false, true)) {
            cleanup(cancelWorker = true)
        }
    }

    private fun linkCallbackDeath(): Boolean {
        if (!callbackDeathLinked.compareAndSet(false, true)) {
            return !terminal.get()
        }
        try {
            callbackBinder.linkToDeath(callbackDeathRecipient, 0)
        } catch (_: RemoteException) {
            callbackDeathLinked.set(false)
            callbackDied()
            return false
        }
        if (!callbackBinder.isBinderAlive) {
            callbackDied()
            return false
        }
        return true
    }

    private fun unlinkCallbackDeath() {
        if (!callbackDeathLinked.compareAndSet(true, false)) {
            return
        }
        runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
    }

    private fun submitWorker(block: () -> Unit) {
        if (terminal.get()) {
            cleanup(cancelWorker = false)
            return
        }
        val future = try {
            worker.submit(block)
        } catch (error: RejectedExecutionException) {
            finishError(
                requestIdHint,
                DexCompilerErrorCode.INTERNAL,
                DexCompilerFailurePhase.QUEUE,
                "Fixture worker is unavailable",
            )
            return
        }
        workerFuture = future
        if (terminal.get()) {
            future.cancel(true)
        }
    }

    private fun cleanup(cancelWorker: Boolean) {
        if (!cleaned.compareAndSet(false, true)) {
            return
        }
        descriptors.close()
        unlinkCallbackDeath()
        if (cancelWorker) {
            workerFuture?.cancel(true)
        }
        onFinished(this)
    }

    private fun ensureActive() {
        if (terminal.get() || Thread.currentThread().isInterrupted) {
            throw SessionStopped()
        }
    }

    private fun elapsedMillis(): Long = (SystemClock.elapsedRealtime() - createdAtMillis).coerceAtLeast(0L)

    private fun boundedErrorMessage(message: String): String {
        val nonBlankMessage = message.takeIf(String::isNotBlank) ?: "Fixture compiler failure"
        val codePointCount = nonBlankMessage.codePointCount(0, nonBlankMessage.length)
        if (codePointCount <= MAX_ERROR_MESSAGE_CODE_POINTS) {
            return nonBlankMessage
        }
        val endIndex = nonBlankMessage.offsetByCodePoints(0, MAX_ERROR_MESSAGE_CODE_POINTS)
        return nonBlankMessage.substring(0, endIndex)
    }

    private class SessionStopped : RuntimeException()

    private companion object {
        const val MAX_ERROR_MESSAGE_CODE_POINTS = 512
        val ZERO_REQUEST_ID = DexRequestId.fromBytes(ByteArray(DexRequestId.BYTE_COUNT))
    }
}
