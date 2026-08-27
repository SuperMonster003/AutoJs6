package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8BundleException
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RetraceInputBundleCodec
import org.autojs.plugin.r8compiler.api.R8RetraceInputRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.util.UUID

class R8RetraceHostRequestTest {
    @Test
    fun snapshotCanonicalizesStackAndBindsAllThreeInputs(): Unit = withWorkspace { workspace ->
        val capabilities = R8RetraceHostFixtures.capabilities(maxOutputBytes = 99)
        val request = R8RetraceInputSnapshotter.snapshot(
            R8RetraceHostFixtures.spec(
                capabilities = capabilities,
                stackTrace = "java.lang.Error: broken\r\n\tat a.a(SourceFile:7)",
                timeoutMillis = R8CompilerContract.MAX_RETRACE_TIMEOUT_MILLIS,
            ),
            capabilities,
            workspace,
            requestId(),
        )

        val bundle = FileInputStream(workspace.inputBundleFile).use { input ->
            R8RetraceInputBundleCodec.read(input, request, capabilities)
        }
        assertEquals(R8CompilerContract.PROTOCOL_V1_1, request.protocolVersion)
        assertEquals(99L, request.maxOutputBytes)
        assertEquals(capabilities.limits.maxTimeoutMillis, request.timeoutMillis)
        assertEquals(R8RetraceInputRole.values().toList(), request.inputIdentities.map { it.role })
        assertArrayEquals(
            "java.lang.Error: broken\n\tat a.a(SourceFile:7)\n".toByteArray(),
            bundle.obfuscatedStackTraceBytes,
        )
        assertTrue(request.inputBundleSha256 == bundle.summary.contentSha256)
    }

    @Test
    fun mismatchedMappingAndMetadataFailBeforeRemoteDispatch(): Unit = withWorkspace { workspace ->
        val capabilities = R8RetraceHostFixtures.capabilities()
        val spec = R8RetraceHostFixtures.spec(
            capabilities = capabilities,
            mappingBytes = R8RetraceHostFixtures.mapping + "# changed\n".toByteArray(),
            metadataBytes = R8RetraceHostFixtures.metadata(capabilities = capabilities),
        )

        assertThrows(R8BundleException::class.java) {
            R8RetraceInputSnapshotter.snapshot(spec, capabilities, workspace, requestId())
        }
    }

    @Test
    fun providerInputCeilingsAreAppliedBeforeMaterialization(): Unit = withWorkspace { workspace ->
        val capabilities = R8RetraceHostFixtures.capabilities().let { value ->
            val small = value.copy(
                limits = value.limits.copy(maxObfuscatedStackTraceBytes = 8),
            )
            small.copy(
                capabilityFingerprint = org.autojs.plugin.r8compiler.api.R8RetraceCapabilityFingerprint.compute(small),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            R8RetraceInputSnapshotter.snapshot(
                R8RetraceHostFixtures.spec(capabilities = capabilities),
                capabilities,
                workspace,
                requestId(),
            )
        }
    }

    private fun requestId() = R8RequestId.fromUuid(
        UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
    )

    private fun <T> withWorkspace(action: (R8RetraceTransactionWorkspace) -> T): T {
        val root = Files.createTempDirectory("r8-retrace-request-").toFile()
        return try {
            R8RetraceTransactionWorkspace.create(root).use(action)
        } finally {
            root.deleteRecursively()
        }
    }
}
