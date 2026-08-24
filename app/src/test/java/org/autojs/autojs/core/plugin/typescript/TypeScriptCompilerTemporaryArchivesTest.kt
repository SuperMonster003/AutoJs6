package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypeScriptCompilerTemporaryArchivesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesProcessOrphansButPreservesUnrelatedCacheFiles() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val orphanInput = File(cacheDirectory, "typescript-input-orphan.zip").apply {
            writeText("clear-text source")
        }
        val orphanOutput = File(cacheDirectory, "typescript-output-orphan.zip").apply {
            writeText("compiler output")
        }
        val unrelated = File(cacheDirectory, "other.zip").apply { writeText("keep") }

        TypeScriptCompilerTemporaryArchives.create(cacheDirectory).use { active ->
            assertFalse(orphanInput.exists())
            assertFalse(orphanOutput.exists())
            assertTrue(unrelated.isFile)
            assertTrue(active.inputFile.isFile)
            assertTrue(active.outputFile.isFile)
        }
        assertTrue(unrelated.isFile)
    }

    @Test
    fun preservesArchivesOwnedByConcurrentCallers() {
        val cacheDirectory = temporaryFolder.newFolder("concurrent-cache")
        val first = TypeScriptCompilerTemporaryArchives.create(cacheDirectory)
        try {
            val second = TypeScriptCompilerTemporaryArchives.create(cacheDirectory)
            try {
                assertTrue(first.inputFile.isFile)
                assertTrue(first.outputFile.isFile)
                assertTrue(second.inputFile.isFile)
                assertTrue(second.outputFile.isFile)
            } finally {
                second.close()
            }
        } finally {
            first.close()
        }
        assertFalse(
            cacheDirectory.listFiles().orEmpty().any { file ->
                file.name.startsWith("typescript-input-") ||
                    file.name.startsWith("typescript-output-")
            },
        )
    }

    @Test(expected = IOException::class)
    fun refusesToContinueWhenAnOrphanCannotBeSafelyDeleted() {
        val cacheDirectory = temporaryFolder.newFolder("unsafe-cache")
        File(cacheDirectory, "typescript-input-not-an-archive.zip").mkdir()

        TypeScriptCompilerTemporaryArchives.create(cacheDirectory)
    }
}
