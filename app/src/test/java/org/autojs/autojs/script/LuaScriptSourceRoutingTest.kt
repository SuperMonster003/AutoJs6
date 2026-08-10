package org.autojs.autojs.script

import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.api.Engines
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.ui.main.task.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LuaScriptSourceRoutingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun luaFileSourceOwnsItsLanguageIdentityAndUtf8Content() {
        val file = temporaryFolder.newFile("main.LUA").apply {
            writeText("return '你好'", Charsets.UTF_8)
        }
        val source = LuaFileSource(file)

        assertEquals("lua", source.engineName)
        assertEquals("lua", source.extension)
        assertEquals("main.lua", source.fullName)
        assertEquals("return '你好'", source.script)
        assertEquals("return '你好'", source.nonNullScriptReader.use { it.readText() })
        assertTrue(LuaScriptSource.isLuaFileName(file.name))
        assertFalse(LuaScriptSource.isLuaFileName("main.lua.js"))
        assertFalse(LuaScriptSource.isLuaFileName(null))
    }

    @Test
    fun fileAndRawPathLaunchesRouteLuaBeforeOtherEngines() {
        val file = temporaryFolder.newFile("script.LuA")
        val fallback = temporaryFolder.newFolder("fallback")

        val fileLaunch = ScriptLaunchSourceFactory.createFileLaunch(
            file,
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )
        val pathLaunch = ScriptLaunchSourceFactory.createPathLaunch(
            file.path,
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )

        listOf(fileLaunch, pathLaunch).forEach { launch ->
            assertTrue(launch.source is LuaFileSource)
            assertEquals(LuaScriptSource.ENGINE, launch.engineName)
            assertEquals(file.canonicalFile.parent, launch.workingDirectory)
            assertNull(launch.nodeProjectLaunchSource)
        }
    }

    @Test
    fun sourceLaunchUsesLuaFileParentAsWorkingDirectory() {
        val file = temporaryFolder.newFile("main.lua")
        val fallback = temporaryFolder.newFolder("fallback")

        val launch = ScriptLaunchSourceFactory.createSourceLaunch(
            LuaFileSource(file),
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )

        assertEquals(file.parent, launch.workingDirectory)
    }

    @Test
    fun missingLuaFileStillRoutesWithoutAutoFallback() {
        val file = temporaryFolder.root.resolve("missing.LUA")

        val launch = ScriptLaunchSourceFactory.createFileLaunch(
            file,
            workingDirectory = temporaryFolder.root.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )

        assertFalse(file.exists())
        assertTrue(launch.source is LuaFileSource)
        assertEquals(LuaScriptSource.ENGINE, launch.engineName)
    }

    @Test
    fun luaRejectsJavaScriptPreExecuteWithoutFallback() {
        val file = temporaryFolder.newFile("main.lua")

        val error = assertThrows(IllegalArgumentException::class.java) {
            ScriptLaunchSourceFactory.createPathLaunch(
                path = file.path,
                preExecuteScript = "console.log('must not run')",
                workingDirectory = file.parent,
                defaultWorkingDirectory = file.parent,
            )
        }

        assertTrue(error.message.orEmpty().contains("Lua"))
        assertTrue(error.message.orEmpty().contains("preExecuteScript"))
    }

    @Test
    fun existingJavaScriptAndAutoRoutesRemainDistinct() {
        val javaScript = temporaryFolder.newFile("main.js")
        val auto = temporaryFolder.newFile("main.auto")

        val javaScriptLaunch = ScriptLaunchSourceFactory.createFileLaunch(
            javaScript,
            workingDirectory = javaScript.parent,
            defaultWorkingDirectory = javaScript.parent,
        )
        val autoLaunch = ScriptLaunchSourceFactory.createFileLaunch(
            auto,
            workingDirectory = auto.parent,
            defaultWorkingDirectory = auto.parent,
        )

        assertTrue(javaScriptLaunch.source is JavaScriptFileSource)
        assertTrue(autoLaunch.source is AutoFileSource)
        assertEquals("js", javaScriptLaunch.source.extension)
        assertEquals("auto", autoLaunch.source.extension)
    }

    @Test
    fun enginesExecutionConfigAndTaskListLaunchKeepTheSharedLuaRoute() {
        val file = temporaryFolder.newFile("consumer.lua")
        val launch = ScriptLaunchSourceFactory.createPathLaunch(
            path = file.path,
            defaultWorkingDirectory = temporaryFolder.root.path,
        )

        val enginesConfig = Engines.createExecScriptFileExecutionConfig(launch, ExecutionConfig())
        assertEquals(file.parent, enginesConfig.workingDirectory)
        assertTrue(launch.source is LuaFileSource)

        val intentTask = IntentTask().apply { scriptPath = file.path }
        val pendingTask = object : Task.PendingTask(null, intentTask) {
            override fun createLaunch(): ScriptLaunchSourceFactory.Launch = launch
        }
        assertEquals(LuaScriptSource.ENGINE, pendingTask.engineName)
        assertSame(launch, pendingTask.createLaunch())
    }
}
