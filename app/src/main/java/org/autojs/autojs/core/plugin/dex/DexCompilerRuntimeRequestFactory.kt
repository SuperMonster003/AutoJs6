package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerInputLayout
import org.autojs.plugin.dexcompiler.api.DexCompilerInputSetFingerprint
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

/** Builds an immutable V1.0 raw-JAR or V1.1 ordered-classpath request from pinned snapshots. */
internal object DexCompilerRuntimeRequestFactory {

    fun create(
        snapshot: DexCompilerInputSnapshot,
        minApi: Int,
        timeoutMillis: Long = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
    ): DexCompileRequest {
        require(minApi > 0)
        require(timeoutMillis in 1L..DexCompilerContract.MAX_TIMEOUT_MILLIS)
        require(snapshot.sizeBytes in 1L..DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES)
        val identities = snapshot.inputIdentities
        val hasClasspathBundle = identities.isNotEmpty()
        if (hasClasspathBundle) {
            require(identities.size >= 2) { "Classpath bundle must contain a program and classpath" }
            require(identities.first().sizeBytes == snapshot.sizeBytes)
            require(identities.first().contentSha256.toHexString() == snapshot.sha256)
            require(snapshot.wireSizeBytes in 1L..DexCompilerContract.MAX_COMPRESSED_INPUT_BUNDLE_BYTES)
        } else {
            // These equalities preserve the legacy raw-program descriptor contract.
            require(snapshot.wireSizeBytes == snapshot.sizeBytes)
            require(snapshot.wireSha256 == snapshot.sha256)
        }
        return DexCompileRequest(
            requestId = DexRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = if (hasClasspathBundle) {
                DexCompilerContract.PROTOCOL_V1_1
            } else {
                DexProtocolVersion(
                    DexCompilerContract.PROTOCOL_MAJOR,
                    DexCompilerContract.PROTOCOL_MINOR,
                )
            },
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
            inputLayout = DexCompilerInputLayout.PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1
                .takeIf { hasClasspathBundle },
            inputIdentities = identities,
            inputSetFingerprint = if (hasClasspathBundle) {
                DexCompilerInputSetFingerprint.compute(identities)
            } else {
                null
            },
            inputBundleSizeBytes = snapshot.wireSizeBytes.takeIf { hasClasspathBundle },
            inputBundleSha256 = if (hasClasspathBundle) {
                DexSha256.fromBytes(snapshot.wireSha256.decodeLowercaseSha256())
            } else {
                null
            },
        )
    }
}

internal fun interface DexCompilerAttemptLauncher {
    fun start(
        invocation: DexCompilerExperimentalInvocation,
        onOutcome: (DexCompilerExperimentalOutcome) -> Unit,
    ): Closeable
}

/**
 * A one-shot bridge between single-flight abandonment and the asynchronously created attempt.
 *
 * A request may win before [bind]. In that case the newly bound handle is cancelled before
 * [bind] returns. The handle is claimed at most once and is always closed outside this object's
 * monitor because closing it may enqueue or synchronously execute harness control work.
 */
internal class DexCompilerAttemptCancellation {
    private var requested = false
    private var handleBound = false
    private var handleCloseClaimed = false
    private var handle: Closeable? = null

    fun request() {
        val handleToClose = synchronized(this) {
            requested = true
            claimHandleForCloseLocked()
        }
        closeCancellationHandle(handleToClose)
    }

    fun bind(handle: Closeable) {
        val handleToClose = synchronized(this) {
            check(!handleBound) { "DEX compiler attempt cancellation handle was bound more than once" }
            handleBound = true
            this.handle = handle
            claimHandleForCloseLocked()
        }
        closeCancellationHandle(handleToClose)
    }

    fun isRequested(): Boolean = synchronized(this) { requested }

    private fun claimHandleForCloseLocked(): Closeable? {
        if (!requested || handleCloseClaimed) return null
        val current = handle ?: return null
        handleCloseClaimed = true
        return current
    }

    private fun closeCancellationHandle(handle: Closeable?) {
        if (handle == null) return
        val wasInterrupted = Thread.currentThread().isInterrupted
        var closeWasInterrupted = false
        try {
            handle.close()
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (failure: Throwable) {
            closeWasInterrupted = failure is InterruptedException
            // Cancellation is best effort; the attempt deadline remains the terminal backstop.
        } finally {
            if (wasInterrupted || closeWasInterrupted) Thread.currentThread().interrupt()
        }
    }
}

/** Interruptible synchronous adapter; class loading itself remains on the calling script thread. */
internal class DexCompilerSynchronousAttemptRunner(
    private val launcher: DexCompilerAttemptLauncher,
) {
    @Throws(InterruptedException::class)
    fun run(
        invocation: DexCompilerExperimentalInvocation,
        cancellation: DexCompilerAttemptCancellation? = null,
    ): DexCompilerExperimentalOutcome {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("DEX compilation was interrupted")
        val terminal = CountDownLatch(1)
        val outcome = AtomicReference<DexCompilerExperimentalOutcome?>()
        val handle = launcher.start(invocation) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        cancellation?.bind(handle)
        try {
            terminal.await()
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("DEX compilation was interrupted after completion")
            }
            return checkNotNull(outcome.get()) { "DEX compiler completed without a terminal outcome" }
        } catch (interrupted: InterruptedException) {
            if (cancellation == null) {
                try {
                    handle.close()
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (_: Throwable) {
                    // The original interruption remains the caller-visible terminal.
                }
            } else {
                cancellation.request()
            }
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
