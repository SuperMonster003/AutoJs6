package org.autojs.autojs.core.plugin.dex

import java.io.Closeable

/** A descriptor has one owner and may be transferred to exactly one openSession invocation. */
internal interface DexCompilerOwnedDescriptor : Closeable

internal interface DexCompilerInputSnapshot : Closeable {
    val sizeBytes: Long
    val sha256: String
    fun claimReadDescriptor(): DexCompilerOwnedDescriptor
}

internal interface DexCompilerOutputTransaction : Closeable {
    /** Starts the single bounded host-side drain before the remote writer can be dispatched. */
    fun beginDrain(maxBytes: Long, onTerminal: (DexCompilerOutputDrainTerminal) -> Unit)
    fun claimWriteDescriptor(): DexCompilerOwnedDescriptor
    fun readBytes(maxBytes: Long): ByteArray

    /**
     * Copies the already verified bytes into a host-only publication inode. The remote-writable
     * staging inode must never be renamed or linked into the cache.
     */
    fun publishVerified(cacheKeySha256: String, bytes: ByteArray)
    fun rollback()
}

internal sealed class DexCompilerOutputDrainTerminal {
    data class Success(val summary: DexCompilerStreamSummary) : DexCompilerOutputDrainTerminal()
    data class Failure(val cause: Throwable?) : DexCompilerOutputDrainTerminal()
}

internal interface DexCompilerRemoteSession : Closeable {
    fun cancel()
}

internal interface DexCompilerRemoteCallback {
    fun onStarted(metadata: ByteArray)
    fun onProgress(progress: ByteArray)
    fun onCompleted(result: ByteArray)
    fun onFailed(error: ByteArray)
    fun onCancelled(cancellation: ByteArray)
    fun onTransportViolation(cause: Throwable?)
}

internal interface DexCompilerRemoteConnection {
    fun getCompilerInfo(): ByteArray
    fun getCapabilities(): ByteArray
    fun openSession(
        encodedRequest: ByteArray,
        programDescriptor: DexCompilerOwnedDescriptor,
        outputDescriptor: DexCompilerOwnedDescriptor,
        callback: DexCompilerRemoteCallback,
    ): DexCompilerRemoteSession
}

internal interface DexCompilerBinding : Closeable

internal interface DexCompilerBindingCallback {
    fun onConnected(connection: DexCompilerRemoteConnection)
    fun onBindingFailure(cause: Throwable?)
    fun onBinderDeath()
}

/**
 * Platform adapter boundary. Implementations must query only the supplied action and bind an
 * explicit component. The harness independently repeats inspection after connection.
 */
internal interface DexCompilerExperimentalTransport {
    fun discoverExactAction(action: String): List<DexCompilerServiceCandidate>
    fun inspectExact(component: DexCompilerComponent): DexCompilerServiceCandidate?
    fun bindExact(
        identity: DexCompilerPinnedIdentity,
        callback: DexCompilerBindingCallback,
    ): DexCompilerBinding
}

internal fun interface DexCompilerDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface DexCompilerThreadProbe {
    fun isMainThread(): Boolean
}

internal fun interface DexCompilerOutputVerifier {
    fun verify(bytes: ByteArray, limits: DexIndexedZipLimits): DexIndexedZipValidationResult
}
