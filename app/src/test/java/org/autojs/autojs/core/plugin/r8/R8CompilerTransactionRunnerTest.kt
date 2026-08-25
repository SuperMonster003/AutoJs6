package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class R8CompilerTransactionRunnerTest {
    @Test
    fun successfulDispatchAdoptsFiveArtifactsAndCommitsIndependentCache() = withRoots { work, cache ->
        val connection = R8CompilerTransactionTestFixtures.FakeConnection()
        val store = R8CompilerPersistentArtifactStore(cache)
        val outcome = run(connection, work, store)

        val published = outcome as R8CompilerTransactionOutcome.Published
        assertFalse(published.cacheHit)
        assertEquals(1, connection.openCount)
        assertEquals(R8ArtifactRole.values().toList(), published.artifact.result.artifactIdentities.map { it.role })
        R8ArtifactRole.values().forEach { role ->
            published.artifact.openArtifact(role).use { input ->
                assertEquals(published.artifact.result.artifactIdentities.single { it.role == role }.sizeBytes, input.readBytes().size.toLong())
            }
        }
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sameSemanticRequestUsesVerifiedR8CacheWithoutRedispatch() = withRoots { work, cache ->
        val connection = R8CompilerTransactionTestFixtures.FakeConnection()
        val store = R8CompilerPersistentArtifactStore(cache)
        val requestIds = R8CompilerTransactionTestFixtures.requestIds()
        val runner = runner(work, store, requestIds)

        val first = run(connection, runner)
        val second = run(connection, runner)

        assertFalse((first as R8CompilerTransactionOutcome.Published).cacheHit)
        assertTrue((second as R8CompilerTransactionOutcome.Published).cacheHit)
        assertEquals(1, connection.openCount)
        assertFalse(first.artifact.result.requestId == second.artifact.result.requestId)
    }

    @Test
    fun everyRemoteAndPostDispatchCorruptionFailsWithoutSemanticFallback() = withRoots { work, cache ->
        val behaviors = listOf(
            R8CompilerTransactionTestFixtures.ConnectionBehavior.THROW_ON_OPEN,
            R8CompilerTransactionTestFixtures.ConnectionBehavior.REMOTE_ERROR,
            R8CompilerTransactionTestFixtures.ConnectionBehavior.MALFORMED_COMPLETION,
            R8CompilerTransactionTestFixtures.ConnectionBehavior.CORRUPT_BUNDLE,
            R8CompilerTransactionTestFixtures.ConnectionBehavior.INVALID_DEX,
            R8CompilerTransactionTestFixtures.ConnectionBehavior.DUPLICATE_TERMINAL,
        )
        behaviors.forEachIndexed { index, behavior ->
            val attemptWork = File(work, "attempt-$index").apply { mkdirs() }
            val attemptCache = File(cache, "attempt-$index")
            val connection = R8CompilerTransactionTestFixtures.FakeConnection(behavior = behavior)
            val outcome = run(
                connection,
                attemptWork,
                R8CompilerPersistentArtifactStore(attemptCache),
            )
            val failed = outcome as R8CompilerTransactionOutcome.Failed
            assertTrue(failed.directive.propagateAsR8Failure)
            assertFalse(failed.directive.semanticFallbackAllowed)
            assertTrue(failed.directive.phase != R8CompilerAttemptPhase.COMPLETED)
        }
    }

    @Test
    fun cacheLookupAndPublicationFailuresRemainR8Failures() = withRoots { work, cache ->
        val base = R8CompilerPersistentArtifactStore(cache)
        val lookup = run(
            R8CompilerTransactionTestFixtures.FakeConnection(),
            File(work, "lookup").apply { mkdirs() },
            R8CompilerTransactionTestFixtures.FailingStore(base, failLookup = true),
        ) as R8CompilerTransactionOutcome.Failed
        assertEquals(R8CompilerTransactionFailureStage.CACHE_LOOKUP, lookup.stage)
        assertFalse(lookup.directive.semanticFallbackAllowed)

        val publication = run(
            R8CompilerTransactionTestFixtures.FakeConnection(),
            File(work, "publish").apply { mkdirs() },
            R8CompilerTransactionTestFixtures.FailingStore(base, failPublish = true),
        ) as R8CompilerTransactionOutcome.Failed
        assertEquals(R8CompilerTransactionFailureStage.CACHE_PUBLICATION, publication.stage)
        assertFalse(publication.directive.semanticFallbackAllowed)
    }

    @Test
    fun cancellationAndTerminalTimeoutNeverAuthorizeFallback() = withRoots { work, cache ->
        val cancelled = run(
            R8CompilerTransactionTestFixtures.FakeConnection(
                behavior = R8CompilerTransactionTestFixtures.ConnectionBehavior.CANCELLED,
            ),
            File(work, "cancelled").apply { mkdirs() },
            R8CompilerPersistentArtifactStore(File(cache, "cancelled")),
        ) as R8CompilerTransactionOutcome.Cancelled
        assertFalse(cancelled.directive.semanticFallbackAllowed)

        val silent = R8CompilerTransactionTestFixtures.FakeConnection(
            behavior = R8CompilerTransactionTestFixtures.ConnectionBehavior.SILENT,
        )
        val timedOut = run(
            silent,
            File(work, "timeout").apply { mkdirs() },
            R8CompilerPersistentArtifactStore(File(cache, "timeout")),
            R8CompilerTransactionTestFixtures.spec(timeoutMillis = 1),
        ) as R8CompilerTransactionOutcome.Failed
        assertEquals(R8CompilerTransactionFailureStage.CALLBACK, timedOut.stage)
        assertFalse(timedOut.directive.semanticFallbackAllowed)
        assertEquals(1, silent.cancelCount)
    }

    @Test
    fun binderDeathAfterDispatchIsATerminalR8TransportFailure() = withRoots { work, cache ->
        val abort = R8CompilerAttemptAbortSignal()
        val connection = R8CompilerTransactionTestFixtures.FakeConnection(
            behavior = R8CompilerTransactionTestFixtures.ConnectionBehavior.SILENT,
            onOpen = { abort.abort(IllegalStateException("synthetic Binder death")) },
        )
        val firewall = R8CompilerDispatchFirewall().apply { negotiated() }
        val outcome = runner(work, R8CompilerPersistentArtifactStore(cache)).run(
            connection,
            R8CompilerTransactionTestFixtures.negotiation(),
            R8CompilerTransactionTestFixtures.spec(),
            firewall,
            abort,
        ) as R8CompilerTransactionOutcome.Failed

        assertEquals(R8CompilerTransactionFailureStage.CALLBACK, outcome.stage)
        assertFalse(outcome.directive.semanticFallbackAllowed)
        assertEquals(R8CompilerAttemptPhase.FAILED, firewall.currentPhase)
    }

    @Test
    fun descriptorAndSessionCloseFailuresRemainR8FailuresAndDisposeTheSession() =
        withRoots { work, cache ->
            val descriptorConnection = R8CompilerTransactionTestFixtures.FakeConnection()
            val descriptorRunner = R8CompilerTransactionRunner(
                File(work, "descriptor").apply { mkdirs() },
                R8CompilerTransactionTestFixtures.descriptorFactory(failOnWriteClose = true),
                R8CompilerPersistentArtifactStore(File(cache, "descriptor")),
                R8CompilerTransactionTestFixtures.requestIds(),
            )
            val descriptorFailure = run(descriptorConnection, descriptorRunner)
                as R8CompilerTransactionOutcome.Failed
            assertEquals(R8CompilerTransactionFailureStage.DISPATCH, descriptorFailure.stage)
            assertFalse(descriptorFailure.directive.semanticFallbackAllowed)
            assertEquals(1, descriptorConnection.cancelCount)
            assertEquals(1, descriptorConnection.closeCount)

            val sessionConnection = R8CompilerTransactionTestFixtures.FakeConnection(
                behavior = R8CompilerTransactionTestFixtures.ConnectionBehavior.THROW_ON_SESSION_CLOSE,
            )
            val sessionFailure = run(
                sessionConnection,
                File(work, "session").apply { mkdirs() },
                R8CompilerPersistentArtifactStore(File(cache, "session")),
            ) as R8CompilerTransactionOutcome.Failed
            assertEquals(R8CompilerTransactionFailureStage.CALLBACK, sessionFailure.stage)
            assertFalse(sessionFailure.directive.semanticFallbackAllowed)
            assertEquals(1, sessionConnection.cancelCount)
            assertEquals(1, sessionConnection.closeCount)
        }

    private fun run(
        connection: R8CompilerTransactionTestFixtures.FakeConnection,
        workspace: File,
        store: R8CompilerArtifactStore,
        spec: R8CompilerHostCompileSpec = R8CompilerTransactionTestFixtures.spec(),
    ): R8CompilerTransactionOutcome = run(connection, runner(workspace, store), spec)

    private fun run(
        connection: R8CompilerTransactionTestFixtures.FakeConnection,
        runner: R8CompilerTransactionRunner,
        spec: R8CompilerHostCompileSpec = R8CompilerTransactionTestFixtures.spec(),
    ): R8CompilerTransactionOutcome {
        val firewall = R8CompilerDispatchFirewall().apply { negotiated() }
        return runner.run(
            connection,
            R8CompilerTransactionTestFixtures.negotiation(),
            spec,
            firewall,
            R8CompilerAttemptAbortSignal(),
        ).also {
            assertEquals(
                if (it is R8CompilerTransactionOutcome.Published) R8CompilerAttemptPhase.COMPLETED else R8CompilerAttemptPhase.FAILED,
                firewall.currentPhase,
            )
        }
    }

    private fun runner(
        workspace: File,
        store: R8CompilerArtifactStore,
        ids: () -> org.autojs.plugin.r8compiler.api.R8RequestId = R8CompilerTransactionTestFixtures.requestIds(),
    ) = R8CompilerTransactionRunner(
        workspace,
        R8CompilerTransactionTestFixtures.descriptorFactory(),
        store,
        ids,
    )

    private fun withRoots(action: (File, File) -> Unit) {
        val root = Files.createTempDirectory("r8-host-runner-").toFile()
        val work = File(root, "work").apply { mkdirs() }
        val cache = File(root, "cache")
        try {
            action(work, cache)
        } finally {
            root.deleteRecursively()
        }
    }
}
