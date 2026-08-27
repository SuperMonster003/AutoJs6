package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.autojs.plugin.r8compiler.api.R8RetraceError
import org.autojs.plugin.r8compiler.api.R8RetraceErrorCode
import org.autojs.plugin.r8compiler.api.R8RetraceFailurePhase
import org.autojs.plugin.r8compiler.api.R8RetraceProgress
import org.autojs.plugin.r8compiler.api.R8RetraceProgressStage
import org.autojs.plugin.r8compiler.api.R8RetraceResult
import org.autojs.plugin.r8compiler.api.R8RetraceStarted
import org.autojs.plugin.r8compiler.api.R8Sha256
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class R8RetraceSessionCallbackGateTest {
    @Test
    fun orderedStartedProgressAndCompletionAreAccepted() = withGate { gate, request, capabilities ->
        gate.onStarted(R8RetraceCodec.encodeStarted(started(request, capabilities)))
        gate.onProgress(
            R8RetraceCodec.encodeProgress(
                R8RetraceProgress(request.requestId, 1, R8RetraceProgressStage.RETRACING),
            ),
        )
        gate.onCompleted(R8RetraceCodec.encodeResult(result(request, capabilities)))

        assertTrue(gate.awaitTerminal(100) is R8RetraceSessionTerminal.Completed)
    }

    @Test
    fun progressBeforeStartedAndIdentityDriftBecomeTerminalViolations() = withGate { gate, request, _ ->
        gate.onProgress(
            R8RetraceCodec.encodeProgress(
                R8RetraceProgress(request.requestId, 1, R8RetraceProgressStage.RETRACING),
            ),
        )
        assertTrue(gate.awaitTerminal(100) is R8RetraceSessionTerminal.Violation)
    }.also {
        withGate { gate, request, capabilities ->
            gate.onStarted(
                R8RetraceCodec.encodeStarted(
                    started(request, capabilities).copy(requestId = R8RequestId.fromUuid(UUID.randomUUID())),
                ),
            )
            assertTrue(gate.awaitTerminal(100) is R8RetraceSessionTerminal.Violation)
        }
    }

    @Test
    fun duplicateTerminalIsNeverSilentlyAccepted() = withGate { gate, request, capabilities ->
        gate.onStarted(R8RetraceCodec.encodeStarted(started(request, capabilities)))
        gate.onCompleted(R8RetraceCodec.encodeResult(result(request, capabilities)))
        gate.onFailed(
            R8RetraceCodec.encodeError(
                R8RetraceError(
                    request.requestId,
                    R8RetraceErrorCode.INTERNAL,
                    R8RetraceFailurePhase.CLEANUP,
                    "duplicate terminal",
                    1,
                ),
            ),
        )

        assertTrue(gate.awaitTerminal(100) is R8RetraceSessionTerminal.Violation)
    }

    private fun started(
        request: org.autojs.plugin.r8compiler.api.R8RetraceRequest,
        capabilities: org.autojs.plugin.r8compiler.api.R8RetraceCapabilities,
    ) = R8RetraceStarted(
        request.requestId,
        0,
        R8CompilerContract.PROTOCOL_V1_1,
        capabilities.compilerVersion,
        capabilities.capabilityFingerprint,
        request.mappingProvenanceId,
        0,
    )

    private fun result(
        request: org.autojs.plugin.r8compiler.api.R8RetraceRequest,
        capabilities: org.autojs.plugin.r8compiler.api.R8RetraceCapabilities,
    ): R8RetraceResult {
        val output = "sample.Main.crash(Main.java:42)\n".toByteArray()
        return R8RetraceResult(
            request.requestId,
            R8CompilerContract.PROTOCOL_V1_1,
            capabilities.compilerVersion,
            capabilities.capabilityFingerprint,
            request.mappingProvenanceId,
            request.inputIdentities.single {
                it.role == org.autojs.plugin.r8compiler.api.R8RetraceInputRole.OBFUSCATED_STACK_TRACE
            }.contentSha256,
            capabilities.outputLayout,
            output.size.toLong(),
            R8Sha256.digest(output),
            1,
        )
    }

    private fun <T> withGate(
        action: (
            R8RetraceSessionCallbackGate,
            org.autojs.plugin.r8compiler.api.R8RetraceRequest,
            org.autojs.plugin.r8compiler.api.R8RetraceCapabilities,
        ) -> T,
    ): T {
        val root = Files.createTempDirectory("r8-retrace-gate-").toFile()
        return try {
            val capabilities = R8RetraceHostFixtures.capabilities()
            R8RetraceTransactionWorkspace.create(root).use { workspace ->
                val request = R8RetraceInputSnapshotter.snapshot(
                    R8RetraceHostFixtures.spec(capabilities = capabilities),
                    capabilities,
                    workspace,
                    R8RequestId.fromUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174002")),
                )
                action(R8RetraceSessionCallbackGate(request, capabilities), request, capabilities)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
