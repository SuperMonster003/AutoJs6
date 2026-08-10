package org.autojs.autojs.core.plugin.dex

import java.io.Closeable
import java.io.File
import java.io.InputStream

/** A descriptor has one owner and may be transferred to exactly one openSession invocation. */
internal interface DexCompilerOwnedDescriptor : Closeable

internal interface DexCompilerInputSnapshot : Closeable {
    val sizeBytes: Long
    val sha256: String
    fun claimReadDescriptor(): DexCompilerOwnedDescriptor

    /** Keeps the exact host snapshot alive for a possible local fallback. */
    fun retainForLocalFallback(): DexCompilerRetainedInput
}

internal interface DexCompilerRetainedInput : Closeable {
    val sizeBytes: Long
    val sha256: String

    /** Resolves only this still-live host-private snapshot, after size and digest revalidation. */
    fun resolve(): File?
}

/**
 * An in-process capability for one verified publication generation.
 *
 * It is deliberately not parcelable or serializable. Callers cannot resolve arbitrary cache keys,
 * and a stale handle cannot invalidate a newer publication of the same semantic key.
 */
internal interface DexCompilerVerifiedArtifact {
    val cacheKeySha256: String
    val outputSha256: String
    val sizeBytes: Long

    /** Opens this exact publication generation before the store releases its generation lock. */
    fun openRead(): InputStream?
    fun invalidate(): Boolean
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
    fun publishVerified(cacheKeySha256: String, bytes: ByteArray): DexCompilerVerifiedArtifact
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

/**
 * Looks up only a fully finalized semantic key after provider authentication and negotiation.
 * Implementations must return a capability for the exact publication generation, never a path
 * reconstructed from caller-controlled input.
 */
internal fun interface DexCompilerVerifiedCache {
    fun lookup(cacheKeySha256: String): DexCompilerVerifiedArtifact?
}
