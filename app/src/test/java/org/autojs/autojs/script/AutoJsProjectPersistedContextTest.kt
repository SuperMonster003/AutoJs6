package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutoJsProjectPersistedContextTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun markerDirectoryCapturesCanonicalProjectContext() {
        val root = temporaryFolder.newFolder()
        File(root, "project.json").writeText("{}")

        val captured = ScriptLaunchSourceFactory.capturePersistedLaunchContext(root.path)

        assertEquals(PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1, captured.sourceKind)
        assertEquals(root.canonicalPath, captured.projectRoot)
    }

    @Test
    fun ordinaryDirectoryDoesNotAcquireProjectContext() {
        val root = temporaryFolder.newFolder()

        val captured = ScriptLaunchSourceFactory.capturePersistedLaunchContext(root.path)

        assertNull(captured.sourceKind)
        assertNull(captured.projectRoot)
    }

    @Test
    fun staleMixedAndPrefixedProjectContextsFailClosed() {
        val root = temporaryFolder.newFolder().apply {
            File(this, "project.json").writeText("{}")
        }
        val other = temporaryFolder.newFolder("other")

        assertProjectContextInvalid {
            persistedLaunch(root, PersistedScriptLaunchContext(PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1, null))
        }
        assertProjectContextInvalid {
            persistedLaunch(root, PersistedScriptLaunchContext(PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1, other.path))
        }
        assertProjectContextInvalid {
            ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
                path = root.path,
                sourceKind = PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1,
                projectRoot = root.path,
                preExecuteScript = "bootstrap()",
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
    }

    private fun persistedLaunch(
        root: File,
        context: PersistedScriptLaunchContext,
    ): ScriptLaunchSourceFactory.Launch = ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
        path = root.path,
        sourceKind = context.sourceKind,
        projectRoot = context.projectRoot,
        preExecuteScript = null,
        workingDirectory = null,
        defaultWorkingDirectory = "default",
        pythonPreviewEnabled = false,
    )

    private fun assertProjectContextInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected persisted AutoJs6 project context rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().startsWith("AUTOJS_PROJECT_CONTEXT_INVALID:"))
        }
    }
}
