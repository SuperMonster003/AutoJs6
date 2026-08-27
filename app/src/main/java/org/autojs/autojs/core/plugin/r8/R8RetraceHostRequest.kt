package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8MappingProvenanceId
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RetraceCapabilities
import org.autojs.plugin.r8compiler.api.R8RetraceInputBundleCodec
import org.autojs.plugin.r8compiler.api.R8RetraceInputRole
import org.autojs.plugin.r8compiler.api.R8RetraceRequest
import org.autojs.plugin.r8compiler.api.R8RetraceValidation
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal class R8RetraceHostSpec(
    val mapping: R8CompilerHostSource,
    val retraceMetadata: R8CompilerHostSource,
    val obfuscatedStackTrace: String,
    val timeoutMillis: Long? = null,
    val terminalGraceMillis: Long = R8CompilerHostCompileSpec.DEFAULT_TERMINAL_GRACE_MILLIS,
    val maximumOutputBytes: Long? = null,
    val diagnosticByteLimit: Int? = null,
) {
    init {
        require(obfuscatedStackTrace.isNotBlank()) { "R8 obfuscated stack trace must not be blank" }
        timeoutMillis?.let { require(it in 1..R8CompilerContract.MAX_RETRACE_TIMEOUT_MILLIS) }
        require(terminalGraceMillis in 0..R8CompilerHostCompileSpec.MAX_TERMINAL_GRACE_MILLIS)
        maximumOutputBytes?.let {
            require(it in 1..R8CompilerContract.MAX_RETRACED_STACK_TRACE_BYTES)
        }
        diagnosticByteLimit?.let { require(it in 1..R8CompilerContract.MAX_DIAGNOSTIC_BYTES) }
    }
}

internal object R8RetraceInputSnapshotter {
    fun snapshot(
        spec: R8RetraceHostSpec,
        capabilities: R8RetraceCapabilities,
        workspace: R8RetraceTransactionWorkspace,
        requestId: R8RequestId,
    ): R8RetraceRequest {
        val mapping = spec.mapping.openInputStream().use { input ->
            readBounded(input, capabilities.limits.maxMappingBytes, "R8 mapping")
        }
        val metadata = spec.retraceMetadata.openInputStream().use { input ->
            readBounded(input, capabilities.limits.maxRetraceMetadataBytes, "R8 retrace metadata")
        }
        val stack = canonicalStack(spec.obfuscatedStackTrace)
        require(stack.size.toLong() <= capabilities.limits.maxObfuscatedStackTraceBytes) {
            "R8 obfuscated stack trace exceeds provider limit"
        }
        val summary = FileOutputStream(workspace.inputBundleFile, false).use { output ->
            R8RetraceInputBundleCodec.write(
                output,
                mapping,
                metadata,
                stack,
                capabilities,
            ).also { output.fd.sync() }
        }
        val mappingIdentity = summary.identities.single {
            it.role == R8RetraceInputRole.MAPPING_TEXT
        }
        val metadataIdentity = summary.identities.single {
            it.role == R8RetraceInputRole.RETRACE_METADATA
        }
        val request = R8RetraceRequest(
            requestId = requestId,
            protocolVersion = R8CompilerContract.PROTOCOL_V1_1,
            inputLayout = capabilities.inputLayout,
            inputIdentities = summary.identities,
            inputBundleSizeBytes = summary.sizeBytes,
            inputBundleSha256 = summary.contentSha256,
            mappingProvenanceId = R8MappingProvenanceId.compute(
                mappingIdentity.contentSha256,
                metadataIdentity.contentSha256,
            ),
            expectedCompilerVersion = capabilities.compilerVersion,
            expectedCapabilityFingerprint = capabilities.capabilityFingerprint,
            outputLayout = capabilities.outputLayout,
            maxOutputBytes = minOf(
                spec.maximumOutputBytes ?: capabilities.limits.maxRetracedStackTraceBytes,
                capabilities.limits.maxRetracedStackTraceBytes,
            ),
            diagnosticByteLimit = minOf(
                spec.diagnosticByteLimit ?: capabilities.limits.maxDiagnosticBytes,
                capabilities.limits.maxDiagnosticBytes,
            ),
            timeoutMillis = minOf(
                spec.timeoutMillis ?: capabilities.limits.defaultTimeoutMillis,
                capabilities.limits.maxTimeoutMillis,
            ),
        )
        R8RetraceValidation.validateRequestAgainst(request, capabilities)
        return request
    }

    private fun readBounded(input: InputStream, maximumBytes: Long, label: String): ByteArray {
        require(maximumBytes in 1..Int.MAX_VALUE.toLong()) { "$label limit cannot be materialized" }
        val output = ByteArrayOutputStream(minOf(maximumBytes.toInt(), DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total = Math.addExact(total, read.toLong())
            require(total <= maximumBytes) { "$label exceeds provider limit" }
            output.write(buffer, 0, read)
        }
        require(total > 0L) { "$label must not be empty" }
        return output.toByteArray()
    }

    private fun canonicalStack(value: String): ByteArray {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
            .let { text -> if (text.endsWith('\n')) text else "$text\n" }
        require(normalized.any { !it.isWhitespace() }) { "R8 obfuscated stack trace must not be blank" }
        return try {
            val encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(normalized))
            ByteArray(encoded.remaining()).also(encoded::get)
        } catch (error: Exception) {
            throw IllegalArgumentException("R8 obfuscated stack trace is invalid Unicode", error)
        }
    }
}
