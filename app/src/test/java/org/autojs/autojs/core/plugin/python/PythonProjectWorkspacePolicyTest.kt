package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonProjectSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PythonProjectWorkspacePolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun projectPlanIsSortedAndMarksTheSeparatelyTransportedEntryPoint() {
        val root = temporaryFolder.newFolder()
        val entry = File(root, "src/main.py").apply {
            parentFile.mkdirs()
            writeText("from lib import value")
        }
        File(root, "lib.py").writeText("value = 42")
        File(root, "project.json").writeText("""{"type":"python","main":"src/main.py"}""")
        val source = PythonProjectSource.create(root, entry)

        val plan = PythonProjectWorkspacePolicy.plan(source)

        assertEquals(listOf("lib.py", "project.json", "src/main.py"), plan.files.map { it.relativePath })
        assertEquals(listOf("src/main.py"), plan.files.filter { it.entryPoint }.map { it.relativePath })
        assertFalse(plan.files.filterNot { it.entryPoint }.any { it.relativePath == source.entryPoint })
    }

    @Test
    fun archivePathsRejectTraversalBackslashCaseCollisionAndPrefixConflict() {
        listOf(
            listOf("../escape.py"),
            listOf("/absolute.py"),
            listOf("C:/absolute.py"),
            listOf("folder\\file.py"),
            listOf("bad\u0000name.py"),
            listOf("cafe\u0301.py"),
            listOf("A.py", "a.py"),
            listOf("package", "package/module.py"),
        ).forEach { paths ->
            assertRejected { PythonProjectWorkspacePolicy.validateArchivePaths(paths) }
        }
    }

    @Test
    fun workspaceLimitsRemainTheR4MinimumProfile() {
        assertEquals(16L * 1024L * 1024L, PythonProjectWorkspacePolicy.MAX_ARCHIVE_BYTES)
        assertEquals(1_024, PythonProjectWorkspacePolicy.MAX_ENTRIES)
        assertEquals(32L * 1024L * 1024L, PythonProjectWorkspacePolicy.MAX_UNCOMPRESSED_BYTES)
    }

    @Test
    fun ordinaryNestedPathsAreAccepted() {
        PythonProjectWorkspacePolicy.validateArchivePaths(
            listOf("project.json", "package/__init__.py", "package/module.py", "data/input.txt"),
        )
    }

    @Test
    fun editorOverlaySourceIsSeparateFromTheWorkspaceEntry() {
        val root = temporaryFolder.newFolder()
        val entry = File(root, "src/main.py").apply {
            parentFile.mkdirs()
            writeText("print('disk')")
        }
        File(root, "project.json").writeText("""{"type":"python","main":"src/main.py"}""")
        val overlay = temporaryFolder.newFile("editor-cache.js").apply {
            writeText("print('unsaved')")
        }
        val source = PythonProjectSource.createWithSource(root, entry, overlay)

        val plan = PythonProjectWorkspacePolicy.plan(source)
        val sourceInput = PythonProjectWorkspacePolicy.planSource(source)

        assertEquals(entry.canonicalFile, plan.files.single { it.entryPoint }.file)
        assertEquals(overlay.canonicalFile, sourceInput.file)
        assertEquals("src/main.py", sourceInput.relativePath)
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected workspace path rejection")
        } catch (_: IllegalStateException) {
            // Expected fail-closed policy result.
        }
    }
}
