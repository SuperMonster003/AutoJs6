package org.autojs.autojs.core.plugin.r8

import java.io.Closeable

/** A descriptor has one owner and may be transferred to exactly one R8 session dispatch. */
internal interface R8CompilerOwnedDescriptor : Closeable

internal interface R8CompilerRemoteSession : Closeable {
    fun cancel()
}

internal interface R8CompilerRemoteCallback {
    fun onStarted(metadata: ByteArray)
    fun onProgress(progress: ByteArray)
    fun onCompleted(result: ByteArray)
    fun onFailed(error: ByteArray)
    fun onCancelled(cancellation: ByteArray)
    fun onTransportViolation(cause: Throwable?)
}

internal interface R8CompilerRemoteConnection {
    fun getCompilerInfo(): ByteArray
    fun getCapabilities(): ByteArray

    fun openSession(
        encodedRequest: ByteArray,
        inputBundleDescriptor: R8CompilerOwnedDescriptor,
        outputBundleDescriptor: R8CompilerOwnedDescriptor,
        callback: R8CompilerRemoteCallback,
    ): R8CompilerRemoteSession
}

internal interface R8CompilerBinding : Closeable

internal interface R8CompilerBindingCallback {
    fun onConnected(connection: R8CompilerRemoteConnection)
    fun onBindingFailure(cause: Throwable?)
    fun onBinderDeath()
}

/**
 * Platform boundary for the explicit R8 path. Implementations query only the supplied R8 action
 * and bind only the exact pinned component; callers must re-inspect identity before negotiation.
 */
internal interface R8CompilerTransport {
    fun discoverExactAction(action: String): List<R8CompilerServiceCandidate>
    fun inspectExact(component: R8CompilerComponent): R8CompilerServiceCandidate?

    fun bindExact(
        identity: R8CompilerPinnedIdentity,
        callback: R8CompilerBindingCallback,
    ): R8CompilerBinding
}

internal object R8CompilerTransportCallerPolicy {
    fun isExpectedUid(pinnedPackageUid: Int, callingUid: Int): Boolean =
        pinnedPackageUid >= 0 && callingUid == pinnedPackageUid
}
