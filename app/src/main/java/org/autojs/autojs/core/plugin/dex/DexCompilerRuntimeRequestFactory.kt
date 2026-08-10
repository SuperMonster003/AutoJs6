package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** Builds the immutable V1 request from the already pinned host snapshot. */
internal object DexCompilerRuntimeRequestFactory {

    fun create(
        snapshot: DexCompilerInputSnapshot,
        minApi: Int,
        timeoutMillis: Long = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
    ): DexCompileRequest {
        require(minApi > 0)
        require(timeoutMillis in 1L..DexCompilerContract.MAX_TIMEOUT_MILLIS)
        require(snapshot.sizeBytes in 1L..DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES)
        return DexCompileRequest(
            requestId = DexRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = DexProtocolVersion(
                DexCompilerContract.PROTOCOL_MAJOR,
                DexCompilerContract.PROTOCOL_MINOR,
            ),
            inputFormat = DexCompilerInputFormat.JAR,
            outputFormat = DexCompilerOutputFormat.DEX_ZIP,
            mode = DexCompilerMode.RELEASE,
            minApi = minApi,
            programSizeBytes = snapshot.sizeBytes,
            programSha256 = DexSha256.fromBytes(snapshot.sha256.decodeLowercaseSha256()),
            maxOutputBytes = DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES,
            runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            // This valid placeholder is replaced only after authenticated capability negotiation.
            expectedRuntimeLibraryFingerprint = DexSha256.fromBytes(ByteArray(DexSha256.BYTE_COUNT)),
            canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
            diagnosticByteLimit = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
            timeoutMillis = timeoutMillis,
        )
    }
}

internal fun interface DexCompilerAttemptLauncher {
    fun start(
        invocation: DexCompilerExperimentalInvocation,
        onOutcome: (DexCompilerExperimentalOutcome) -> Unit,
    ): Closeable
}

/** Interruptible synchronous adapter; class loading itself remains on the calling script thread. */
internal class DexCompilerSynchronousAttemptRunner(
    private val launcher: DexCompilerAttemptLauncher,
) {
    @Throws(InterruptedException::class)
    fun run(invocation: DexCompilerExperimentalInvocation): DexCompilerExperimentalOutcome {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("DEX compilation was interrupted")
        val terminal = CountDownLatch(1)
        val outcome = AtomicReference<DexCompilerExperimentalOutcome?>()
        val handle = launcher.start(invocation) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        try {
            terminal.await()
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("DEX compilation was interrupted after completion")
            }
            return checkNotNull(outcome.get()) { "DEX compiler completed without a terminal outcome" }
        } catch (interrupted: InterruptedException) {
            runCatching { handle.close() }
            throw interrupted
        }
    }
}

private fun String.decodeLowercaseSha256(): ByteArray {
    require(length == DexSha256.BYTE_COUNT * 2 && all { it in '0'..'9' || it in 'a'..'f' }) {
        "Snapshot digest must be lowercase SHA-256"
    }
    return ByteArray(DexSha256.BYTE_COUNT) { index ->
        val high = this[index * 2].digitToInt(16)
        val low = this[index * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}
