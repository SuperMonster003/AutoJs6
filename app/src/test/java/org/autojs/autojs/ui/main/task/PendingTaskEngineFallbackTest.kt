package org.autojs.autojs.ui.main.task

import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.PythonScriptSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingTaskEngineFallbackTest {

    @Test
    fun explicitPythonContextKeepsPythonSemanticsWhenInvalidOrStale() {
        assertFallback(PythonScriptSource.ENGINE, "missing.txt", "python_file_v1", null)
        assertFallback(PythonScriptSource.ENGINE, "old-entry.py", "python_project_v1", null)
        assertFallback(PythonScriptSource.ENGINE, "old-entry.py", null, "/stale/project")
        assertFallback(PythonScriptSource.ENGINE, "old-entry.py", "unknown_v1", "/stale/project")
    }

    @Test
    fun legacyNullContextKeepsExistingExtensionFallbacks() {
        assertFallback(AutoFileSource.ENGINE, "legacy.py", null, null)
        assertFallback(JavaScriptSource.ENGINE_NODEJS, "legacy.mjs", null, null)
        assertFallback(JavaScriptSource.ENGINE, "legacy.js", null, null)
    }

    private fun assertFallback(
        expected: String,
        scriptPath: String,
        sourceKind: String?,
        projectRoot: String?,
    ) {
        assertEquals(
            expected,
            Task.PendingTask.resolveFallbackEngineName(scriptPath, sourceKind, projectRoot),
        )
    }
}
