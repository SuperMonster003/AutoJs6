package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class R8CompilerArtifactExporterTest {
    @Test
    fun fourReportsPublishTogetherAndDexNeverCrossesTheExportBoundary() = withArtifact { root, artifact ->
        val destination = File(root, "reports")

        val published = R8CompilerArtifactExporter.export(artifact, destination)

        assertEquals(destination.canonicalFile, published)
        assertEquals(
            setOf("mapping.txt", "seeds.txt", "usage.txt", "retrace-metadata.bin"),
            destination.listFiles().orEmpty().map { it.name }.toSet(),
        )
        assertArrayEquals(
            artifact.openArtifact(R8ArtifactRole.MAPPING_TEXT).use(InputStream::readBytes),
            File(destination, "mapping.txt").readBytes(),
        )
        assertFalse(File(destination, "dex.zip").exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.contains(".partial-") })
    }

    @Test
    fun existingDestinationIsNeverMergedOrOverwritten() = withArtifact { root, artifact ->
        val destination = File(root, "reports").apply { mkdir() }
        val sentinel = File(destination, "keep.txt").apply { writeText("owned") }

        assertThrows(IllegalArgumentException::class.java) {
            R8CompilerArtifactExporter.export(artifact, destination)
        }

        assertEquals("owned", sentinel.readText())
        assertEquals(setOf("keep.txt"), destination.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test
    fun changedSourceFailsClosedAndLeavesNoDestinationOrStaging() = withArtifact { root, artifact ->
        val changed = object : R8CompilerStoredArtifact by artifact {
            override fun openArtifact(role: R8ArtifactRole): InputStream =
                if (role == R8ArtifactRole.MAPPING_TEXT) {
                    ByteArrayInputStream("tampered mapping\n".toByteArray())
                } else {
                    artifact.openArtifact(role)
                }
        }
        val destination = File(root, "reports")

        assertThrows(IllegalArgumentException::class.java) {
            R8CompilerArtifactExporter.export(changed, destination)
        }

        assertFalse(destination.exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.contains(".partial-") })
    }

    private fun <T> withArtifact(action: (File, R8CompilerStoredArtifact) -> T): T {
        val root = Files.createTempDirectory("r8-export-").toFile()
        return try {
            val transaction = R8CompilerTransactionRunner(
                File(root, "work"),
                R8CompilerTransactionTestFixtures.descriptorFactory(),
                R8CompilerPersistentArtifactStore(File(root, "cache")),
                R8CompilerTransactionTestFixtures.requestIds(),
            ).run(
                connection = R8CompilerTransactionTestFixtures.FakeConnection(),
                negotiation = R8CompilerTransactionTestFixtures.negotiation(),
                spec = R8CompilerTransactionTestFixtures.spec(),
                firewall = R8CompilerDispatchFirewall().apply { negotiated() },
                abortSignal = R8CompilerAttemptAbortSignal(),
            )
            check(transaction is R8CompilerTransactionOutcome.Published)
            action(root, transaction.artifact)
        } finally {
            root.deleteRecursively()
        }
    }
}
