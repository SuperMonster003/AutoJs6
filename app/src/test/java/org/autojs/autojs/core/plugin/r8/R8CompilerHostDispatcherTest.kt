package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class R8CompilerHostDispatcherTest {
    @Test
    fun explicitSelectionTraversesBindNegotiationDispatchAdoptionAndCache() = withDispatcher { fixture ->
        val outcome = fixture.dispatcher.compile(R8CompilerTransactionTestFixtures.spec())

        assertTrue(outcome is R8CompilerHostDispatchOutcome.Published)
        assertEquals(1, fixture.transport.discoverCount)
        assertEquals(1, fixture.transport.bindCount)
        assertEquals(1, fixture.transport.inspectCount)
        assertEquals(1, fixture.connection.openCount)
    }

    @Test
    fun bindingFailureIsAnR8FailureAndCannotFallBack() = withDispatcher(bindingFailure = true) { fixture ->
        val outcome = fixture.dispatcher.compile(R8CompilerTransactionTestFixtures.spec())
            as R8CompilerHostDispatchOutcome.Failed

        assertEquals(R8CompilerHostFailureStage.BINDING, outcome.stage)
        assertTrue(outcome.directive.propagateAsR8Failure)
        assertFalse(outcome.directive.semanticFallbackAllowed)
        assertEquals(0, fixture.connection.openCount)
    }

    @Test
    fun binderDeathAfterOpenSessionReachesTheTransactionAbortGate() {
        lateinit var transport: ImmediateTransport
        val connection = R8CompilerTransactionTestFixtures.FakeConnection(
            behavior = R8CompilerTransactionTestFixtures.ConnectionBehavior.SILENT,
            onOpen = { transport.die() },
        )
        withDispatcher(connection = connection, transportFactory = { candidate ->
            ImmediateTransport(candidate, connection).also { transport = it }
        }) { fixture ->
            val outcome = fixture.dispatcher.compile(R8CompilerTransactionTestFixtures.spec())
                as R8CompilerHostDispatchOutcome.Failed

            assertEquals(R8CompilerHostFailureStage.TRANSACTION, outcome.stage)
            assertEquals(R8CompilerTransactionFailureStage.CALLBACK, outcome.transaction?.stage)
            assertFalse(outcome.directive.semanticFallbackAllowed)
        }
    }

    @Test
    fun mainThreadAndRejectedExplicitSelectionNeverBindOrFallBack() = withDispatcher(
        isMainThread = true,
    ) { fixture ->
        val main = fixture.dispatcher.compile(R8CompilerTransactionTestFixtures.spec())
            as R8CompilerHostDispatchOutcome.Failed
        assertEquals(R8CompilerHostFailureStage.MAIN_THREAD, main.stage)
        assertFalse(main.directive.semanticFallbackAllowed)
        assertEquals(0, fixture.transport.bindCount)
    }

    private fun withDispatcher(
        bindingFailure: Boolean = false,
        isMainThread: Boolean = false,
        connection: R8CompilerTransactionTestFixtures.FakeConnection =
            R8CompilerTransactionTestFixtures.FakeConnection(),
        transportFactory: ((R8CompilerServiceCandidate) -> ImmediateTransport)? = null,
        action: (DispatcherFixture) -> Unit,
    ) {
        val root = Files.createTempDirectory("r8-dispatcher-").toFile()
        try {
            val candidate = R8CompilerHostFixtures.candidate()
            val transport = transportFactory?.invoke(candidate)
                ?: ImmediateTransport(candidate, connection, bindingFailure)
            val controlPlane = R8CompilerHostControlPlane(
                transport,
                { R8CompilerSelectionOptions(true, candidate.component) },
                hostVersionCode = 5_276,
                hostRuntimeApi = 24,
            )
            val runner = R8CompilerTransactionRunner(
                File(root, "work").apply { mkdirs() },
                R8CompilerTransactionTestFixtures.descriptorFactory(),
                R8CompilerPersistentArtifactStore(File(root, "cache")),
                R8CompilerTransactionTestFixtures.requestIds(),
            )
            action(
                DispatcherFixture(
                    R8CompilerHostDispatcher(controlPlane, runner, { isMainThread }, 100),
                    transport,
                    connection,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private data class DispatcherFixture(
        val dispatcher: R8CompilerHostDispatcher,
        val transport: ImmediateTransport,
        val connection: R8CompilerTransactionTestFixtures.FakeConnection,
    )

    private class ImmediateTransport(
        private val candidate: R8CompilerServiceCandidate,
        private val connection: R8CompilerRemoteConnection,
        private val bindingFailure: Boolean = false,
    ) : R8CompilerTransport {
        var discoverCount = 0
        var inspectCount = 0
        var bindCount = 0
        private var bindingCallback: R8CompilerBindingCallback? = null

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
            bindingCallback = callback
            if (bindingFailure) {
                callback.onBindingFailure(IllegalStateException("synthetic binding failure"))
            } else {
                callback.onConnected(connection)
            }
            return object : R8CompilerBinding {
                override fun close() = Unit
            }
        }

        fun die() {
            bindingCallback?.onBinderDeath()
        }
    }
}
