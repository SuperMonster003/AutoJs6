package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PythonPreviewSourceRoutingTest {

    @Test
    fun previewSourceUsesPythonIdentityAndRealExtension() {
        val source = PythonFileSource(File("scripts/main.py"))

        assertEquals(PythonScriptSource.ENGINE, source.engineName)
        assertEquals("py", source.extension)
        assertEquals("main.py", source.fullName)
    }

    @Test
    fun enabledPreviewRoutesOnlyLocalPythonFiles() {
        val fileLaunch = ScriptLaunchSourceFactory.createFileLaunchForPythonPreview(
            file = File("scripts/MAIN.PY"),
            defaultWorkingDirectory = "default",
            pythonPreviewEnabled = true,
        )
        val pathLaunch = ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
            path = "scripts/main.py",
            defaultWorkingDirectory = "default",
            pythonPreviewEnabled = true,
        )

        assertTrue(fileLaunch.source is PythonFileSource)
        assertTrue(pathLaunch.source is PythonFileSource)
        assertEquals(PythonScriptSource.ENGINE, fileLaunch.engineName)
        assertEquals(PythonScriptSource.ENGINE, pathLaunch.engineName)
        assertTrue(fileLaunch.workingDirectory.endsWith("scripts"))
        assertTrue(pathLaunch.workingDirectory.endsWith("scripts"))
    }

    @Test
    fun disabledPreviewAndUnsupportedPythonShapesRemainFailClosed() {
        assertBoundaryError(PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED) {
            ScriptLaunchSourceFactory.createFileLaunchForPythonPreview(
                file = File("main.py"),
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = false,
            )
        }
        assertBoundaryError(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED) {
            ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
                path = "main.pyc",
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
        assertBoundaryError(PythonLaunchBoundaryError.URI_UNSUPPORTED) {
            ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
                path = "content://authority/main.py",
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
        assertBoundaryError(PythonLaunchBoundaryError.PRE_EXECUTE_UNSUPPORTED) {
            ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
                path = "main.py",
                preExecuteScript = "prepare()",
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
    }

    @Test
    fun sourceAndProjectEntryGuardsAreNotOpenedByPreviewRouting() {
        val pythonSource = PythonFileSource(File("main.py"))
        assertBoundaryError(PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED) {
            ScriptLaunchSourceFactory.createSourceLaunch(
                source = pythonSource,
                defaultWorkingDirectory = "default",
            )
        }
        assertBoundaryError(PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED) {
            ScriptLaunchSourceFactory.createProjectEntryLaunch(
                projectRoot = File("project"),
                entryFile = File("project/main.py"),
                defaultWorkingDirectory = "default",
            )
        }
    }

    private fun assertBoundaryError(
        expected: PythonLaunchBoundaryError,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected PythonLaunchBoundaryException")
        } catch (failure: PythonLaunchBoundaryException) {
            assertEquals(expected, failure.error)
        }
    }
}
