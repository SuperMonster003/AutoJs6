package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class R8CompilerPersistentArtifactStoreTest {
    @Test
    fun corruptGenerationIsEvictedAndSameR8ProviderIsRedispatched() = withRoots { work, cache ->
        val connection = R8CompilerTransactionTestFixtures.FakeConnection()
        val store = R8CompilerPersistentArtifactStore(cache)
        val runner = R8CompilerTransactionRunner(
            work,
            R8CompilerTransactionTestFixtures.descriptorFactory(),
            store,
            R8CompilerTransactionTestFixtures.requestIds(),
        )

        assertFalse(run(runner, connection).cacheHit)
        val generation = cache.listFiles().orEmpty().single { it.name.startsWith("entry-") }
        val mapping = File(generation, "mapping.txt")
        RandomAccessFile(mapping, "rw").use { random ->
            random.seek(0)
            val original = random.read()
            random.seek(0)
            random.write(original.xor(1))
        }

        assertFalse(run(runner, connection).cacheHit)
        assertEquals(2, connection.openCount)
        assertEquals(1, cache.listFiles().orEmpty().count { it.name.startsWith("entry-") })
    }

    @Test
    fun startupRecoveryDeletesOnlyRecognizedPartialGeneration() {
        val root = Files.createTempDirectory("r8-cache-recovery-").toFile()
        try {
            val partial = File(root, "partial-123e4567-e89b-12d3-a456-426614174000").apply {
                mkdirs()
                File(this, "junk").writeText("partial")
            }
            val unrelated = File(root, "user-data").apply { mkdirs() }

            R8CompilerPersistentArtifactStore(root)

            assertFalse(partial.exists())
            assertTrue(unrelated.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun openedArtifactIsRehashedAfterLookupAndRejectsLaterMutation() = withRoots { work, cache ->
        val connection = R8CompilerTransactionTestFixtures.FakeConnection()
        val store = R8CompilerPersistentArtifactStore(cache)
        val published = run(
            R8CompilerTransactionRunner(
                work,
                R8CompilerTransactionTestFixtures.descriptorFactory(),
                store,
                R8CompilerTransactionTestFixtures.requestIds(),
            ),
            connection,
        )
        val generation = cache.listFiles().orEmpty().single { it.name.startsWith("entry-") }
        val mapping = File(generation, "mapping.txt")
        RandomAccessFile(mapping, "rw").use { random ->
            random.seek(0)
            random.write(random.read().xor(1))
        }

        val failure = try {
            published.artifact.openArtifact(R8ArtifactRole.MAPPING_TEXT).use { it.readBytes() }
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertTrue(failure?.message?.contains("digest changed") == true)
    }

    private fun run(
        runner: R8CompilerTransactionRunner,
        connection: R8CompilerTransactionTestFixtures.FakeConnection,
    ): R8CompilerTransactionOutcome.Published {
        val firewall = R8CompilerDispatchFirewall().apply { negotiated() }
        return runner.run(
            connection,
            R8CompilerTransactionTestFixtures.negotiation(),
            R8CompilerTransactionTestFixtures.spec(),
            firewall,
            R8CompilerAttemptAbortSignal(),
        ) as R8CompilerTransactionOutcome.Published
    }

    private fun withRoots(action: (File, File) -> Unit) {
        val root = Files.createTempDirectory("r8-cache-test-").toFile()
        try {
            action(
                File(root, "work").apply { mkdirs() },
                File(root, "cache"),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
