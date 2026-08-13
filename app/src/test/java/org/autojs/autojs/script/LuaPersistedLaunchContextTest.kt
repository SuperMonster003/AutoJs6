package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LuaPersistedLaunchContextTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun capturePinsLuaFileIdentityAndLegacyPathStillRoutesByExtension() {
        val file = temporaryFolder.newFile("entry.LuA")
        val captured = ScriptLaunchSourceFactory.capturePersistedLaunchContext(file.path)

        assertEquals(PersistedScriptLaunchPolicy.LUA_FILE_V1, captured.sourceKind)
        assertNull(captured.projectRoot)

        val persisted = persistedLaunch(file, captured)
        val legacy = persistedLaunch(file, PersistedScriptLaunchContext(null, null))

        assertTrue(persisted.source is LuaFileSource)
        assertTrue(legacy.source is LuaFileSource)
        assertEquals(file.canonicalFile.parent, persisted.workingDirectory)
    }

    @Test
    fun staleOrMixedLuaContextFailsClosed() {
        val lua = temporaryFolder.newFile("entry.lua")
        val renamed = temporaryFolder.newFile("entry.js")

        assertLuaContextInvalid {
            persistedLaunch(
                renamed,
                PersistedScriptLaunchContext(PersistedScriptLaunchPolicy.LUA_FILE_V1, null),
            )
        }
        assertLuaContextInvalid {
            ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
                path = lua.path,
                sourceKind = PersistedScriptLaunchPolicy.LUA_FILE_V1,
                projectRoot = temporaryFolder.root.path,
                preExecuteScript = null,
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
        assertLuaContextInvalid {
            ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
                path = lua.path,
                sourceKind = PersistedScriptLaunchPolicy.LUA_FILE_V1,
                projectRoot = null,
                preExecuteScript = "bootstrap()",
                workingDirectory = null,
                defaultWorkingDirectory = "default",
                pythonPreviewEnabled = true,
            )
        }
    }

    private fun persistedLaunch(
        file: File,
        context: PersistedScriptLaunchContext,
    ): ScriptLaunchSourceFactory.Launch = ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
        path = file.path,
        sourceKind = context.sourceKind,
        projectRoot = context.projectRoot,
        preExecuteScript = null,
        workingDirectory = null,
        defaultWorkingDirectory = "default",
        pythonPreviewEnabled = true,
    )

    private fun assertLuaContextInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected persisted Lua context rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().startsWith("LUA_PERSISTED_CONTEXT_INVALID:"))
        }
    }
}
