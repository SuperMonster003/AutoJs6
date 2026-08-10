package org.autojs.autojs.util

import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LuaRestartRoutingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun persistedLuaPathKeepsItsExtensionAndRoutesBackToLua() {
        val script = temporaryFolder.root.resolve("restart.LuA")

        val resolved = IntentUtils.App.resolveRestartScriptFile(
            script = script.path,
            workingDir = temporaryFolder.root.path,
        )
        val launch = ScriptLaunchSourceFactory.createFileLaunch(
            file = resolved,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )

        assertEquals(script.absoluteFile, resolved.absoluteFile)
        assertTrue(launch.source is LuaFileSource)
        assertEquals(LuaScriptSource.ENGINE, launch.engineName)
    }

    @Test
    fun restartResolutionPreservesKnownSourceKindsAndDefaultsExtensionlessNamesToJavaScript() {
        val workingDir = temporaryFolder.root.path

        assertEquals("task.lua", IntentUtils.App.resolveRestartScriptFile("task.lua", workingDir).name)
        assertEquals("task.MJS", IntentUtils.App.resolveRestartScriptFile("task.MJS", workingDir).name)
        assertEquals("task.AUTO", IntentUtils.App.resolveRestartScriptFile("task.AUTO", workingDir).name)
        assertEquals("task.js", IntentUtils.App.resolveRestartScriptFile("task", workingDir).name)
    }
}
