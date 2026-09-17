package org.autojs.autojs.core.plugin.jvm

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal interface JvmSourceOwnedDescriptor : Closeable

internal interface JvmSourceInputSnapshot : Closeable {
    val sizeBytes: Long
    val sha256Hex: String
    val sourceFileName: String
    val entryClassName: String
    val sourcePayloadKind: org.autojs.plugin.jvmsource.api.JvmSourcePayloadKind
        get() = org.autojs.plugin.jvmsource.api.JvmSourcePayloadKind.SINGLE_FILE
    val sourceFileCount: Int
        get() = 1
    val sourceContentBytes: Long
        get() = sizeBytes
    val language: org.autojs.plugin.jvmsource.api.JvmSourceLanguage
        get() = requireNotNull(
            org.autojs.plugin.jvmsource.api.JvmSourceLanguage.fromFileName(sourceFileName),
        )
    fun claimReadDescriptor(): JvmSourceOwnedDescriptor
}

internal data class JvmSourceCapturedOutput(
    val bytes: ByteArray,
    val sha256Hex: String,
) {
    val text: String
        get() = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
}

internal interface JvmSourceOutputCapture : Closeable {
    fun claimWriteDescriptor(): JvmSourceOwnedDescriptor
    fun await(timeoutMillis: Long): JvmSourceCapturedOutput
    fun abort(cause: Throwable? = null)
}

internal interface JvmSourceOutputObserver {
    fun onBytes(bytes: ByteArray, offset: Int, count: Int)
    fun onComplete()

    companion object {
        val NONE: JvmSourceOutputObserver = object : JvmSourceOutputObserver {
            override fun onBytes(bytes: ByteArray, offset: Int, count: Int) = Unit
            override fun onComplete() = Unit
        }
    }
}

internal interface JvmSourceArtifactStore : Closeable {
    /** Compatibility name retained for existing test doubles; the implementation admits Java and Kotlin. */
    fun snapshotJavaSource(file: java.io.File, maximumBytes: Long): JvmSourceInputSnapshot
    fun snapshotSource(
        file: java.io.File,
        maximumBytes: Long,
        entrySimpleName: String = JvmSourceR1Policy.ENTRY_SIMPLE_NAME,
    ): JvmSourceInputSnapshot {
        require(entrySimpleName == JvmSourceR1Policy.ENTRY_SIMPLE_NAME) {
            "This JVM source artifact store does not support an explicit entry name"
        }
        return snapshotJavaSource(file, maximumBytes)
    }
    fun snapshotSourcePackage(
        sourceRoot: java.io.File,
        entryFile: java.io.File,
        maximumBytes: Long,
        entrySimpleName: String = JvmSourceR1Policy.ENTRY_SIMPLE_NAME,
    ): JvmSourceInputSnapshot = throw UnsupportedOperationException("JVM source packages are not supported")
    fun outputCapture(
        maximumBytes: Long,
        observer: JvmSourceOutputObserver = JvmSourceOutputObserver.NONE,
    ): JvmSourceOutputCapture
    override fun close() = Unit
}

/**
 * Receives compiler diagnostics and program output as soon as they are available, independent of
 * the terminal outcome. A compilation failure carries its diagnostics through this sink; the
 * terminal exception itself stays a coarse, stable reason code.
 * zh-CN: 诊断与输出一旦可得即经此发布, 与终态无关; 编译失败的具体报错走这里, 异常本身仍只是稳定错误码.
 */
internal interface JvmSourceExecutionSink {
    fun publishDiagnostics(diagnostics: List<org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic>)
    fun publishOutput(stdout: String, stderr: String)
    fun publishObservation(observation: org.autojs.plugin.jvmsource.api.JvmSourceObservation) = Unit

    companion object {
        val NONE: JvmSourceExecutionSink = object : JvmSourceExecutionSink {
            override fun publishDiagnostics(
                diagnostics: List<org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic>,
            ) = Unit

            override fun publishOutput(stdout: String, stderr: String) = Unit
        }
    }
}

internal fun interface JvmSourceHostBridgeResponse {
    fun onResponse(encodedResponse: ByteArray)
}

internal interface JvmSourceHostBridgeEndpoint : Closeable {
    fun dispatch(encodedCall: ByteArray, callback: JvmSourceHostBridgeResponse)
    fun destroy(encodedReason: ByteArray)
}

internal interface JvmSourceRemoteCallback {
    fun onStarted(metadata: ByteArray)
    fun onDiagnostic(diagnostic: ByteArray)
    fun onCompleted(result: ByteArray, bridgeCallerPids: Set<Int>)
    fun onFailed(error: ByteArray)
    fun onCancelled(cancellation: ByteArray)
    fun onTransportViolation(cause: Throwable)
}

internal interface JvmSourceRemoteSession : Closeable {
    fun cancel()
}

internal interface JvmSourceRemoteConnection {
    fun getProviderInfo(): ByteArray
    fun getCapabilities(): ByteArray
    fun openSession(
        encodedRequest: ByteArray,
        sourceDescriptor: JvmSourceOwnedDescriptor,
        stdoutDescriptor: JvmSourceOwnedDescriptor,
        stderrDescriptor: JvmSourceOwnedDescriptor,
        hostBridge: JvmSourceHostBridgeEndpoint,
        callback: JvmSourceRemoteCallback,
    ): JvmSourceRemoteSession
}

internal interface JvmSourceBinding : Closeable

internal interface JvmSourceBindingCallback {
    fun onConnected(connection: JvmSourceRemoteConnection)
    fun onBindingFailure(cause: Throwable)
    fun onBinderDeath(loss: JvmSourceProviderTransportLoss)
}

/** Read-only package-manager boundary. It cannot bind or negotiate Binder capabilities. */
internal interface JvmSourceProviderInspector {
    fun discoverExactAction(action: String): List<JvmSourceServiceCandidate>
    fun inspectExact(component: JvmSourceComponent): JvmSourceServiceCandidate?
}

/** This boundary permits only the dedicated action and an explicit component bind. */
internal interface JvmSourceTransport : JvmSourceProviderInspector {
    fun bindExact(
        identity: JvmSourcePinnedIdentity,
        callback: JvmSourceBindingCallback,
    ): JvmSourceBinding
}
