package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.autojs.plugin.r8compiler.api.R8RetraceInputBundleCodec
import org.autojs.plugin.r8compiler.api.R8RetraceInputRole
import org.autojs.plugin.r8compiler.api.R8RetraceProgress
import org.autojs.plugin.r8compiler.api.R8RetraceProgressStage
import org.autojs.plugin.r8compiler.api.R8RetraceResult
import org.autojs.plugin.r8compiler.api.R8RetraceStarted
import org.autojs.plugin.r8compiler.api.R8Sha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class R8RetraceHostDispatcherTest {
    @Test
    fun selectedProviderTraversesNegotiationBundleDispatchAndOutputAdoption() = withDispatcher { fixture ->
        val outcome = fixture.dispatcher.retrace(R8RetraceHostFixtures.spec())

        assertTrue(outcome is R8RetraceHostDispatchOutcome.Completed)
        assertTrue((outcome as R8RetraceHostDispatchOutcome.Completed).stackTrace.contains("Main.crash"))
        assertEquals(1, fixture.transport.discoverCount)
        assertEquals(1, fixture.transport.inspectCount)
        assertEquals(1, fixture.transport.bindCount)
        assertEquals(1, fixture.connection.openCount)
        assertTrue(fixture.workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun protocol10ProviderIsRejectedBeforeRetraceSessionOpen() = withDispatcher(
        connection = FakeRetraceConnection(protocol11 = false),
    ) { fixture ->
        val outcome = fixture.dispatcher.retrace(R8RetraceHostFixtures.spec())
            as R8RetraceHostDispatchOutcome.Failed

        assertEquals(R8RetraceHostFailureStage.NEGOTIATION, outcome.stage)
        assertEquals(0, fixture.connection.openCount)
    }

    @Test
    fun outputMutationAfterTerminalFailsAdoptionAndNeverReturnsText() = withDispatcher(
        connection = FakeRetraceConnection(corruptOutput = true),
    ) { fixture ->
        val outcome = fixture.dispatcher.retrace(R8RetraceHostFixtures.spec())
            as R8RetraceHostDispatchOutcome.Failed

        assertEquals(R8RetraceHostFailureStage.OUTPUT_ADOPTION, outcome.stage)
        assertEquals(1, fixture.connection.openCount)
        assertTrue(fixture.workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sessionCloseFailureIsAVisibleCleanupFailure() = withDispatcher(
        connection = FakeRetraceConnection(failOnClose = true),
    ) { fixture ->
        val outcome = fixture.dispatcher.retrace(R8RetraceHostFixtures.spec())
            as R8RetraceHostDispatchOutcome.Failed

        assertEquals(R8RetraceHostFailureStage.CLEANUP, outcome.stage)
        assertTrue(fixture.workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun disabledSelectionAndMainThreadNeverBind() = withDispatcher(enabled = false) { fixture ->
        assertTrue(
            fixture.dispatcher.retrace(R8RetraceHostFixtures.spec()) is
                R8RetraceHostDispatchOutcome.NotSelected,
        )
        assertEquals(0, fixture.transport.bindCount)
    }.also {
        withDispatcher(isMainThread = true) { fixture ->
            val outcome = fixture.dispatcher.retrace(R8RetraceHostFixtures.spec())
                as R8RetraceHostDispatchOutcome.Failed
            assertEquals(R8RetraceHostFailureStage.MAIN_THREAD, outcome.stage)
            assertEquals(0, fixture.transport.bindCount)
        }
    }

    private fun withDispatcher(
        enabled: Boolean = true,
        isMainThread: Boolean = false,
        connection: FakeRetraceConnection = FakeRetraceConnection(),
        action: (Fixture) -> Unit,
    ) {
        val root = Files.createTempDirectory("r8-retrace-dispatcher-").toFile()
        try {
            val candidate = R8CompilerHostFixtures.candidate()
            val transport = ImmediateTransport(candidate, connection)
            val controlPlane = R8CompilerHostControlPlane(
                transport,
                { R8CompilerSelectionOptions(enabled, candidate.component) },
                hostVersionCode = 5_276,
                hostRuntimeApi = 24,
            )
            val workspace = File(root, "work")
            action(
                Fixture(
                    R8RetraceHostDispatcher(
                        controlPlane,
                        workspace,
                        R8CompilerTransactionTestFixtures.descriptorFactory(),
                        { isMainThread },
                        100,
                        R8CompilerTransactionTestFixtures.requestIds(),
                    ),
                    transport,
                    connection,
                    workspace,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private data class Fixture(
        val dispatcher: R8RetraceHostDispatcher,
        val transport: ImmediateTransport,
        val connection: FakeRetraceConnection,
        val workspaceRoot: java.io.File,
    )

    private class FakeRetraceConnection(
        private val protocol11: Boolean = true,
        private val corruptOutput: Boolean = false,
        private val failOnClose: Boolean = false,
    ) : R8CompilerRemoteConnection {
        private val capabilities = R8RetraceHostFixtures.capabilities()
        var openCount = 0

        override fun getCompilerInfo(): ByteArray = R8CompilerCodec.encodeInfo(
            R8CompilerHostFixtures.info(
                protocolMax = if (protocol11) {
                    R8CompilerContract.PROTOCOL_V1_1
                } else {
                    R8CompilerContract.PROTOCOL_V1
                },
            ),
        )

        override fun getCapabilities(): ByteArray = error("Compile capabilities were not requested")

        override fun getRetraceCapabilities(): ByteArray = R8RetraceCodec.encodeCapabilities(capabilities)

        override fun openSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputBundleDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession = error("Compile session was not requested")

        override fun openRetraceSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputStackTraceDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession {
            openCount++
            val input = inputBundleDescriptor as R8CompilerTransactionTestFixtures.TestDescriptor
            val output = outputStackTraceDescriptor as R8CompilerTransactionTestFixtures.TestDescriptor
            val request = R8RetraceCodec.decodeRequest(encodedRequest)
            val bundle = FileInputStream(input.file).use { stream ->
                R8RetraceInputBundleCodec.read(stream, request, capabilities)
            }
            val resultBytes = "java.lang.IllegalStateException: broken\n" +
                "    at sample.Main.crash(Main.java:42)\n"
            val admittedOutput = resultBytes.toByteArray()
            FileOutputStream(output.file, false).use { stream ->
                stream.write(if (corruptOutput) admittedOutput + 1 else admittedOutput)
                stream.fd.sync()
            }
            callback.onStarted(
                R8RetraceCodec.encodeStarted(
                    R8RetraceStarted(
                        request.requestId,
                        0,
                        request.protocolVersion,
                        capabilities.compilerVersion,
                        capabilities.capabilityFingerprint,
                        request.mappingProvenanceId,
                        0,
                    ),
                ),
            )
            callback.onProgress(
                R8RetraceCodec.encodeProgress(
                    R8RetraceProgress(request.requestId, 1, R8RetraceProgressStage.RETRACING),
                ),
            )
            callback.onCompleted(
                R8RetraceCodec.encodeResult(
                    R8RetraceResult(
                        request.requestId,
                        request.protocolVersion,
                        capabilities.compilerVersion,
                        capabilities.capabilityFingerprint,
                        request.mappingProvenanceId,
                        bundle.summary.identities.single {
                            it.role == R8RetraceInputRole.OBFUSCATED_STACK_TRACE
                        }.contentSha256,
                        capabilities.outputLayout,
                        admittedOutput.size.toLong(),
                        R8Sha256.digest(admittedOutput),
                        1,
                    ),
                ),
            )
            return object : R8CompilerRemoteSession {
                private val closed = AtomicBoolean(false)
                override fun cancel() = Unit
                override fun close() {
                    if (failOnClose) {
                        error("synthetic retrace session close failure")
                    }
                    closed.compareAndSet(false, true)
                }
            }
        }
    }

    private class ImmediateTransport(
        private val candidate: R8CompilerServiceCandidate,
        private val connection: R8CompilerRemoteConnection,
    ) : R8CompilerTransport {
        var discoverCount = 0
        var inspectCount = 0
        var bindCount = 0

        override fun discoverExactAction(action: String): List<R8CompilerServiceCandidate> {
            discoverCount++
            return listOf(candidate)
        }

        override fun inspectExact(component: R8CompilerComponent): R8CompilerServiceCandidate? {
            inspectCount++
            return candidate.takeIf { it.component == component }
        }

        override fun bindExact(
            identity: R8CompilerPinnedIdentity,
            callback: R8CompilerBindingCallback,
        ): R8CompilerBinding {
            bindCount++
            callback.onConnected(connection)
            return object : R8CompilerBinding {
                override fun close() = Unit
            }
        }
    }
}
