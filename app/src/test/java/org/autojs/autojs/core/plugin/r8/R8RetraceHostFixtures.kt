package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8CompilerProfile
import org.autojs.plugin.r8compiler.api.R8RetraceCapabilities
import org.autojs.plugin.r8compiler.api.R8RetraceCapabilityFingerprint
import org.autojs.plugin.r8compiler.api.R8RetraceInputLayout
import org.autojs.plugin.r8compiler.api.R8RetraceMetadata
import org.autojs.plugin.r8compiler.api.R8RetraceOutputLayout
import org.autojs.plugin.r8compiler.api.R8RetraceResourceLimits
import org.autojs.plugin.r8compiler.api.R8Sha256
import java.io.ByteArrayInputStream

internal object R8RetraceHostFixtures {
    val mapping = (
        "sample.Main -> a:\n" +
            "    7:7:void crash():42:42 -> a\n"
        ).toByteArray()
    val stack = "java.lang.IllegalStateException: broken\n    at a.a(SourceFile:7)\n"

    fun capabilities(
        compilerVersion: String = "8.13.17",
        maxOutputBytes: Long = 4L * 1024 * 1024,
    ): R8RetraceCapabilities {
        val limits = R8RetraceResourceLimits(
            maxMappingBytes = 16L * 1024 * 1024,
            maxRetraceMetadataBytes = 256L * 1024,
            maxObfuscatedStackTraceBytes = 1024L * 1024,
            maxInputBundleBytes = 16L * 1024 * 1024 + 256L * 1024 + 1024L * 1024 + 160L,
            maxRetracedStackTraceBytes = maxOutputBytes,
            maxDiagnosticBytes = 64 * 1024,
            maxConcurrentSessions = 1,
            defaultTimeoutMillis = 30_000,
            maxTimeoutMillis = 120_000,
        )
        fun create(fingerprint: R8Sha256) = R8RetraceCapabilities(
            R8CompilerFamily.R8,
            compilerVersion,
            R8CompilerContract.MAPPING_FORMAT_ID,
            "1",
            R8RetraceInputLayout.MAPPING_METADATA_AND_STACK_BUNDLE_V1,
            R8RetraceOutputLayout.UTF8_LF_STACK_TRACE_V1,
            limits,
            fingerprint,
        )
        val provisional = create(R8Sha256.ZERO)
        return create(R8RetraceCapabilityFingerprint.compute(provisional))
    }

    fun metadata(
        mappingBytes: ByteArray = mapping,
        capabilities: R8RetraceCapabilities = capabilities(),
    ) = R8CompilerCodec.encodeRetraceMetadata(
        R8RetraceMetadata(
            R8Sha256.digest(mappingBytes),
            capabilities.mappingFormatId,
            capabilities.mappingFormatVersion,
            capabilities.compilerVersion,
            R8Sha256.digest("compile-capability".toByteArray()),
            R8Sha256.digest("runtime".toByteArray()),
            R8Sha256.digest("inputs".toByteArray()),
            24,
            R8CompilerProfile.FULL_RELEASE,
        ),
    )

    fun spec(
        capabilities: R8RetraceCapabilities = capabilities(),
        mappingBytes: ByteArray = mapping,
        metadataBytes: ByteArray = metadata(mappingBytes, capabilities),
        stackTrace: String = stack,
        timeoutMillis: Long = 1_000,
        terminalGraceMillis: Long = 0,
    ) = R8RetraceHostSpec(
        mapping = R8CompilerHostSource { ByteArrayInputStream(mappingBytes) },
        retraceMetadata = R8CompilerHostSource { ByteArrayInputStream(metadataBytes) },
        obfuscatedStackTrace = stackTrace,
        timeoutMillis = timeoutMillis,
        terminalGraceMillis = terminalGraceMillis,
    )
}
