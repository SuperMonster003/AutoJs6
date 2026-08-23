package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class PythonHostEngineScopeTest {
    @Test
    fun reportsBoundedCurrentIdentityAndLaunchesScopedNonPythonScripts() = withTempRoot { root ->
        val entry = File(root, "main.py").apply { writeText("pass\n") }
        File(root, "child.lua").writeText("print('child')\n")
        val stopped = AtomicInteger(0)
        val launches = mutableListOf<Pair<String, String>>()
        val scope = PythonHostEngineScope(
            source = PythonFileSource(entry),
            hostFiles = PythonHostFileScope(root),
            engineId = 17,
            engineStartedAtMillis = 123456789L,
            stopSelf = { stopped.incrementAndGet() },
            scriptLauncher = { launch, config ->
                launches += launch.engineName to config.workingDirectory
                PythonHostEngineLaunch(
                    id = 18,
                    engineName = launch.engineName,
                    sourceName = launch.source.name,
                    path = "ignored-by-scope",
                )
            },
        )

        assertEquals(
            PythonHostEngineInfo(
                id = 17,
                engineName = "python",
                sourceName = "main",
                entryPoint = "main.py",
                project = false,
                startedAtMillis = 123456789L,
            ),
            scope.current(),
        )
        val launched = scope.runScript("child.lua")
        assertEquals(18, launched.id)
        assertEquals("child.lua", launched.path)
        assertTrue(launched.engineName.isNotBlank())
        assertEquals(listOf(launched.engineName to root.canonicalPath), launches)

        scope.requestStopSelf()
        assertEquals(1, stopped.get())
    }

    @Test
    fun reportsProjectEntryWithoutExposingItsAbsoluteRoot() = withTempRoot { root ->
        val packageDirectory = File(root, "pkg").apply { assertTrue(mkdir()) }
        val entry = File(packageDirectory, "main.py").apply { writeText("pass\n") }
        val scope = PythonHostEngineScope(
            source = PythonProjectSource.create(root, entry),
            hostFiles = PythonHostFileScope(root),
            engineId = 3,
            engineStartedAtMillis = 7L,
            stopSelf = {},
            scriptLauncher = { _, _ -> error("No child launch expected") },
        )

        val info = scope.current()
        assertTrue(info.project)
        assertEquals("pkg/main.py", info.entryPoint)
        assertFalse(info.toJson().toString().contains(root.canonicalPath))
    }

    @Test
    fun rejectsTraversalDirectoriesNestedPythonAndLaunchesBeyondQuota() = withTempRoot { root ->
        val entry = File(root, "main.py").apply { writeText("pass\n") }
        val child = File(root, "child.lua").apply { writeText("print('child')\n") }
        File(root, "child.py").writeText("pass\n")
        File(root, "directory").apply { assertTrue(mkdir()) }
        val launchId = AtomicInteger(20)
        val scope = PythonHostEngineScope(
            source = PythonFileSource(entry),
            hostFiles = PythonHostFileScope(root),
            engineId = 19,
            engineStartedAtMillis = 1L,
            stopSelf = {},
            scriptLauncher = { launch, _ ->
                PythonHostEngineLaunch(
                    id = launchId.getAndIncrement(),
                    engineName = launch.engineName,
                    sourceName = launch.source.name,
                    path = child.name,
                )
            },
        )

        assertThrows(IllegalArgumentException::class.java) { scope.runScript("../outside.js") }
        assertThrows(PythonHostCapabilityPathNotFoundException::class.java) {
            scope.runScript("missing.js")
        }
        assertThrows(PythonHostCapabilityPathTypeException::class.java) {
            scope.runScript("directory")
        }
        assertThrows(PythonHostCapabilityNestedPythonException::class.java) {
            scope.runScript("child.py")
        }
        repeat(PythonHostEnginePolicy.MAX_SCRIPT_LAUNCHES) {
            scope.runScript("child.lua")
        }
        assertThrows(PythonHostCapabilityEngineLaunchLimitException::class.java) {
            scope.runScript("child.lua")
        }
    }

    @Test
    fun failedLaunchDoesNotConsumeTheSuccessfulLaunchQuota() = withTempRoot { root ->
        val entry = File(root, "main.py").apply { writeText("pass\n") }
        File(root, "child.lua").writeText("print('child')\n")
        val attempt = AtomicInteger(0)
        val scope = PythonHostEngineScope(
            source = PythonFileSource(entry),
            hostFiles = PythonHostFileScope(root),
            engineId = 1,
            engineStartedAtMillis = 1L,
            stopSelf = {},
            scriptLauncher = { launch, _ ->
                val number = attempt.getAndIncrement()
                if (number == 0) error("synthetic launch failure")
                PythonHostEngineLaunch(
                    id = number,
                    engineName = launch.engineName,
                    sourceName = launch.source.name,
                    path = "child.lua",
                )
            },
        )

        assertThrows(IllegalStateException::class.java) { scope.runScript("child.lua") }
        repeat(PythonHostEnginePolicy.MAX_SCRIPT_LAUNCHES) {
            scope.runScript("child.lua")
        }
        assertThrows(PythonHostCapabilityEngineLaunchLimitException::class.java) {
            scope.runScript("child.lua")
        }
    }

    private fun withTempRoot(action: (File) -> Unit) {
        val root = Files.createTempDirectory("python-host-engines-").toFile().canonicalFile
        try {
            action(root)
        } finally {
            assertTrue("Unable to remove Python Host engine test root: $root", root.deleteRecursively())
        }
    }
}
