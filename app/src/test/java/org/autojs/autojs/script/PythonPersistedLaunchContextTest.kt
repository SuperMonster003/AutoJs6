package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PythonPersistedLaunchContextTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun capturedProjectContextIsExplicitWhileLegacyPathStaysSingleFile() {
        val (root, entry) = newProject()
        val captured = ScriptLaunchSourceFactory.capturePersistedLaunchContext(entry.path)

        assertEquals(PersistedScriptLaunchPolicy.PYTHON_PROJECT_V1, captured.sourceKind)
        assertEquals(root.canonicalPath, captured.projectRoot)

        val projectLaunch = persistedLaunch(entry, captured)
        val legacyLaunch = persistedLaunch(entry, PersistedScriptLaunchContext(null, null))

        assertTrue(projectLaunch.source is PythonProjectSource)
        assertEquals(root.canonicalPath, projectLaunch.workingDirectory)
        assertTrue(legacyLaunch.source is PythonFileSource)
        assertTrue(legacyLaunch.source !is PythonProjectSource)
    }

    @Test
    fun editorOverlayKeepsLatestBytesAndSemanticProjectEntry() {
        val (root, entry) = newProject("src/main.py")
        val overlay = temporaryFolder.newFile("editor-source.js").apply {
            writeText("print('unsaved')")
        }

        val launch = ScriptLaunchSourceFactory.createSemanticFileLaunchForPythonPreview(
            sourceFile = overlay,
            semanticPath = entry.path,
            semanticUri = entry.toURI().toString(),
            semanticDisplayName = entry.name,
            semanticMimeType = "text/x-python",
            workingDirectory = entry.parent,
            defaultWorkingDirectory = "default",
            pythonPreviewEnabled = true,
        )
        val source = launch.source as PythonProjectSource

        assertEquals(root.canonicalFile, source.projectRoot)
        assertEquals(entry.canonicalFile, source.projectEntryFile)
        assertEquals(overlay.canonicalFile, source.file)
        assertEquals("src/main.py", source.entryPoint)
        assertEquals(root.canonicalPath, launch.workingDirectory)
    }

    @Test
    fun stalePartialUnknownAndHelperContextsFailClosed() {
        val (root, entry) = newProject()
        val helper = File(root, "helper.py").apply { writeText("value = 1") }

        assertProjectError(PythonProjectLaunchError.ENTRY_NOT_DECLARED) {
            ScriptLaunchSourceFactory.capturePersistedLaunchContext(helper.path)
        }
        assertProjectError(PythonProjectLaunchError.CONTEXT_INVALID) {
            persistedLaunch(entry, PersistedScriptLaunchContext("unknown_v1", root.path))
        }
        assertProjectError(PythonProjectLaunchError.CONTEXT_INVALID) {
            persistedLaunch(entry, PersistedScriptLaunchContext(null, root.path))
        }

        File(root, "project.json").writeText("""{"type":"python","main":"helper.py"}""")
        assertProjectError(PythonProjectLaunchError.CONTEXT_INVALID) {
            persistedLaunch(
                entry,
                PersistedScriptLaunchContext(PersistedScriptLaunchPolicy.PYTHON_PROJECT_V1, root.path),
            )
        }
    }

    @Test
    fun contentPythonExecutionRemainsClosedButSingleFileImportKeepsPy() {
        assertBoundaryError(PythonLaunchBoundaryError.URI_UNSUPPORTED) {
            ScriptLaunchSourceFactory.createSemanticFileLaunchForPythonPreview(
                sourceFile = temporaryFolder.newFile("payload.py"),
                semanticPath = null,
                semanticUri = "content://provider/document/main.py",
                semanticDisplayName = "main.py",
                semanticMimeType = "text/x-python",
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }

        assertEquals(
            "py",
            ScriptLaunchSourceFactory.resolvePythonImportExtension(
                "main.py", "/document/main.py", null, "text/x-python",
            ),
        )
        assertEquals(
            "py",
            ScriptLaunchSourceFactory.resolvePythonImportExtension(
                "main", "/document/main", null, "application/x-python",
            ),
        )
        assertNull(ScriptLaunchSourceFactory.resolvePythonImportExtension("main.js", null, null, null))
        assertBoundaryError(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED) {
            ScriptLaunchSourceFactory.resolvePythonImportExtension("main.pyc", null, null, null)
        }
        assertBoundaryError(PythonLaunchBoundaryError.PROJECT_IMPORT_UNSUPPORTED) {
            ScriptLaunchSourceFactory.resolvePythonImportExtension(
                "project.json", "/document/project.json", "application/json", null,
            )
        }
    }

    @Test
    fun implicitProjectDescriptorAndPythonPreExecuteNeverReachLegacyEngines() {
        val (root, entry) = newProject()

        assertProjectError(PythonProjectLaunchError.CONTEXT_REQUIRED) {
            ScriptLaunchSourceFactory.createFileLaunchForPythonPreview(
                file = File(root, "project.json"),
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
        assertBoundaryError(PythonLaunchBoundaryError.PRE_EXECUTE_UNSUPPORTED) {
            ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
                path = entry.path,
                preExecuteScript = "bootstrap()",
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
    }

    @Test
    fun projectAncestorSearchLimitFailsClosedInsteadOfBecomingSingleFile() {
        var directory = temporaryFolder.newFolder("deep")
        repeat(65) {
            directory = File(directory, "d").apply { assertTrue(mkdir()) }
        }
        val entry = File(directory, "main.py").apply { writeText("print('deep')") }

        assertProjectError(PythonProjectLaunchError.CONTEXT_INVALID) {
            ScriptLaunchSourceFactory.capturePersistedLaunchContext(entry.path)
        }
    }

    private fun newProject(entryPoint: String = "main.py"): Pair<File, File> {
        val root = temporaryFolder.newFolder()
        val entry = File(root, entryPoint).apply {
            checkNotNull(parentFile).mkdirs()
            writeText("print('disk')")
        }
        File(root, "project.json").writeText("""{"type":"python","main":"$entryPoint"}""")
        return root to entry
    }

    private fun persistedLaunch(
        entry: File,
        context: PersistedScriptLaunchContext,
    ): ScriptLaunchSourceFactory.Launch = ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
        path = entry.path,
        sourceKind = context.sourceKind,
        projectRoot = context.projectRoot,
        preExecuteScript = null,
        workingDirectory = null,
        defaultWorkingDirectory = "default",
        pythonPreviewEnabled = true,
    )

    private fun assertProjectError(expected: PythonProjectLaunchError, block: () -> Unit) {
        try {
            block()
            fail("Expected PythonProjectLaunchException")
        } catch (failure: PythonProjectLaunchException) {
            assertSame(expected, failure.error)
        }
    }

    private fun assertBoundaryError(expected: PythonLaunchBoundaryError, block: () -> Unit) {
        try {
            block()
            fail("Expected PythonLaunchBoundaryException")
        } catch (failure: PythonLaunchBoundaryException) {
            assertSame(expected, failure.error)
        }
    }
}
