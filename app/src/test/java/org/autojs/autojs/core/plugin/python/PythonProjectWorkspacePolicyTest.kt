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
            checkNotNull(parentFile).mkdirs()
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
    fun workspaceLimitsExposeTheProjectLocalPackageProfile() {
        assertEquals(64L * 1024L * 1024L, PythonProjectWorkspacePolicy.MAX_ARCHIVE_BYTES)
        assertEquals(8_192, PythonProjectWorkspacePolicy.MAX_ENTRIES)
        assertEquals(128L * 1024L * 1024L, PythonProjectWorkspacePolicy.MAX_UNCOMPRESSED_BYTES)
    }

    @Test
    fun projectPlanAcceptsMoreThanTheLegacyWorkspaceEntryLimit() {
        val root = temporaryFolder.newFolder()
        val entry = File(root, "main.py").apply { writeText("print('ok')\n") }
        repeat(1_025) { index ->
            File(root, "vendored_$index.py").writeText("VALUE = $index\n")
        }

        val plan = PythonProjectWorkspacePolicy.plan(PythonProjectSource.create(root, entry))

        assertEquals(1_026, plan.files.size)
        assertEquals(1_025, plan.files.count { !it.entryPoint })
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
            checkNotNull(parentFile).mkdirs()
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

    @Test
    fun publishedArtifactDirectoryIsReservedAndExcludedFromLaterSnapshots() {
        val root = temporaryFolder.newFolder()
        val entry = File(root, "main.py").apply { writeText("print('ok')\n") }
        File(root, "project.json").writeText("""{"type":"python","main":"main.py"}""")
        File(root, "${PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME}/execution-1/reports/result.bin")
            .apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(0, 1, 2))
            }

        val plan = PythonProjectWorkspacePolicy.plan(PythonProjectSource.create(root, entry))

        assertEquals(listOf("main.py", "project.json"), plan.files.map { it.relativePath })
    }

    @Test
    fun projectEntryPointCannotUseTheReservedArtifactDirectory() {
        val root = temporaryFolder.newFolder()
        val entry = File(root, "${PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME}/main.py").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("print('not source')\n")
        }
        val source = PythonProjectSource.create(root, entry)

        assertRejected { PythonProjectWorkspacePolicy.plan(source) }
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
