package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.project.ProjectLauncher
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in R4 project vertical slice. This deliberately starts at [ProjectLauncher] and proves
 * the minimum workspace behavior plus the R5 immutable AutoJs6 snapshot API: a two-key manifest,
 * sibling import, scoped data read, plugin-private project cwd, and no JavaScript fallback.
 */
class PythonR4ProjectSmokeInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService
    private val console
        get() = AutoJs.instance.globalConsole
    private var serviceStateOwned = false

    @Before
    fun requireExplicitSmokeAndRealPlugin() {
        assumeTrue(
            "R4 project smoke is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "R4 project smoke requires the normal AutoJs6 app host rather than a packaged INRT app",
            !BuildConfig.isInrt,
        )
        assertTrue("R4 project smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real plugin admits only the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        serviceStateOwned = true
        service.stopAll()
        awaitNoRegisteredEngines("before R4 project smoke")
    }

    @After
    fun settleAfter() {
        if (!serviceStateOwned) return
        service.stopAll()
        awaitNoRegisteredEngines("after R4 project smoke")
    }

    @Test
    fun projectLauncherRunsImportedModuleAndRelativeDataInPythonWorkspaceWithoutFallback() {
        val projectRoot = freshProjectRoot()
        val token = "R4P2_PROJECT_${UUID.randomUUID()}"
        val expectedLine =
            "$token|import=helper-ok|cwd=true|data=workspace-ok|" +
                "api=$HOST_PACKAGE/${Build.VERSION.SDK_INT}/true/main.py\n"
        val createdEngineClasses = CopyOnWriteArrayList<Class<*>>()
        val lifecycle = object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<*>) {
                createdEngineClasses += engine.javaClass
            }

            override fun onEngineRemove(engine: ScriptEngine<*>) = Unit
        }
        val pending = PendingProjectExecution(projectRoot.canonicalPath)
        val checkpoint = consoleCheckpoint()
        service.registerEngineLifecycleCallback(lifecycle)
        service.registerGlobalScriptExecutionListener(pending.listener)
        try {
            writeMinimalProject(projectRoot, token)
            ProjectLauncher(projectRoot.absolutePath).launch(service)

            assertTrue(
                "ProjectLauncher did not reach the unified engine start callback",
                pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val execution = requireNotNull(pending.execution.get()) {
                "ProjectLauncher started without exposing a ScriptExecution"
            }
            if (!pending.completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                execution.engine.forceStop()
                throw AssertionError("Python project did not reach a terminal host callback")
            }
            awaitEngineRemoved(execution.id, "R4 project execution")

            assertEquals("Python project did not start exactly once", 1, pending.startCount.get())
            assertEquals("Python project emitted more than one success callback", 1, pending.successCount.get())
            assertEquals("Python project emitted an exception callback", 0, pending.exceptionCount.get())
            assertEquals("Python project returned an error", null, pending.error.get())
            assertEquals(
                "ProjectLauncher did not preserve PythonProjectSource",
                PythonProjectSource::class.java,
                pending.sourceClass.get(),
            )
            assertEquals(
                "Python project source routing drifted",
                PythonScriptSource.ENGINE,
                pending.sourceEngineName.get(),
            )
            assertEquals(
                "Python project did not use PythonPluginScriptEngine",
                PythonPluginScriptEngine::class.java,
                pending.engineClass.get(),
            )
            val result = pending.result.get() as? PythonExecutionResult
            assertNotNull("Python project did not return a typed PythonExecutionResult", result)
            requireNotNull(result)
            assertEquals("Python project returned a non-zero exit code", 0, result.exitCode)
            assertEquals(
                "Python project stdout byte accounting drifted",
                expectedLine.toByteArray(Charsets.UTF_8).size.toLong(),
                result.stdoutBytes,
            )
            assertEquals("Python project unexpectedly emitted stderr", 0L, result.stderrBytes)
            assertConsoleSince(checkpoint, expectedLine.trimEnd())

            assertEquals(
                "ProjectLauncher must create exactly one engine for this explicit run",
                1,
                createdEngineClasses.size,
            )
            assertTrue(
                "Python project escaped the Python engine: $createdEngineClasses",
                createdEngineClasses.all { it == PythonPluginScriptEngine::class.java },
            )
            assertFalse(
                "Python project fell back to a JavaScript engine: $createdEngineClasses",
                createdEngineClasses.any { JavaScriptEngine::class.java.isAssignableFrom(it) },
            )
        } finally {
            service.unregisterGlobalScriptExecutionListener(pending.listener)
            service.unregisterEngineLifecycleCallback(lifecycle)
            service.stopAll()
            awaitNoRegisteredEngines("during R4 project cleanup")
            check(projectRoot.deleteRecursively() || !projectRoot.exists()) {
                "Unable to remove R4 project smoke directory: $projectRoot"
            }
        }
    }

    private fun writeMinimalProject(projectRoot: File, token: String) {
        File(projectRoot, "project.json").writeText(
            """{"type":"python","main":"main.py"}""",
            Charsets.UTF_8,
        )
        File(projectRoot, "helper.py").writeText(
            "def decorate(value):\n    return \"helper-\" + value\n",
            Charsets.UTF_8,
        )
        File(projectRoot, "data").apply {
            check(mkdirs()) { "Unable to create R4 project data directory: $this" }
        }.resolve("message.txt").writeText("workspace-ok\n", Charsets.UTF_8)
        File(projectRoot, "main.py").writeText(
            """
            from pathlib import Path
            from helper import decorate
            from autojs6 import app, device, execution, project

            cwd_ok = Path.cwd().resolve() == Path(__file__).resolve().parent
            payload = project.read_text("data/message.txt").strip()
            app_info = app.snapshot()
            device_info = device.snapshot()
            execution_info = execution.snapshot()
            print(
                "$token|import=" + decorate("ok") +
                "|cwd=" + str(cwd_ok).lower() +
                "|data=" + payload +
                "|api=" + app_info["packageName"] +
                "/" + str(device_info["sdkInt"]) +
                "/" + str(execution_info["project"]).lower() +
                "/" + execution_info["entryPoint"],
                flush=True,
            )
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun freshProjectRoot(): File = File(
        context.cacheDir,
        "python-r4-project-smoke-${UUID.randomUUID()}",
    ).apply {
        check(mkdirs()) { "Unable to create R4 project smoke directory: $this" }
    }

    private fun assertConsoleSince(checkpoint: Int, expected: String) {
        val text = synchronized(console.logEntries) {
            console.logEntries
                .filter { entry -> entry.id >= checkpoint }
                .joinToString("\n") { entry -> entry.content.toString() }
        }
        assertTrue("Global console did not contain '$expected' after checkpoint $checkpoint:\n$text", text.contains(expected))
    }

    private fun consoleCheckpoint(): Int = synchronized(console.logEntries) {
        (console.logEntries.maxOfOrNull { entry -> entry.id } ?: -1) + 1
    }

    private fun awaitEngineRemoved(executionId: Int, label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        val engines = service.engines.joinToString { engine -> "${engine.id}:${engine.javaClass.name}" }
        throw AssertionError("Engine $executionId remained registered after $label: [$engines]")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty()) {
                assertTrue(
                    "$label left tracked script executions: ${service.scriptExecutions}",
                    service.scriptExecutions.isEmpty(),
                )
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        val engines = service.engines.joinToString { engine -> "${engine.id}:${engine.javaClass.name}" }
        throw AssertionError("$label left engines=[$engines], executions=${service.scriptExecutions}")
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private inner class PendingProjectExecution(
        private val expectedProjectRoot: String,
    ) {
        val execution = AtomicReference<ScriptExecution?>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val startCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val engineClass = AtomicReference<Class<*>?>()
        val sourceClass = AtomicReference<Class<*>?>()
        val sourceEngineName = AtomicReference<String?>()
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                startCount.incrementAndGet()
                engineClass.compareAndSet(null, execution.engine.javaClass)
                sourceClass.compareAndSet(null, execution.source.javaClass)
                sourceEngineName.compareAndSet(null, execution.source.engineName)
                started.countDown()
            }

            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                successCount.incrementAndGet()
                result.set(value)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, throwable: Throwable) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                exceptionCount.incrementAndGet()
                error.set(throwable)
                completed.countDown()
            }
        }

        private fun matches(execution: ScriptExecution): Boolean {
            val source = execution.source as? PythonProjectSource ?: return false
            return runCatching { source.projectRoot.canonicalPath }
                .getOrElse { source.projectRoot.absolutePath } == expectedProjectRoot
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r4p2.smoke.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"

        const val START_TIMEOUT_SECONDS = 20L
        const val EXECUTION_TIMEOUT_SECONDS = 75L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
    }
}
