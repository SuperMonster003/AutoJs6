package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.python.PythonRuntimeExecutionResult
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
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
 * Opt-in U1-R1 acceptance through the public Host [ScriptEngineService] boundary and the real
 * Python Runtime plugin. This is a focused device acceptance, not a soak or a direct Binder test.
 */
class PythonU1R1AcceptanceInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService
    private val console
        get() = AutoJs.instance.globalConsole
    private var serviceStateOwned = false

    @Before
    fun requireExplicitAcceptanceAndRealPlugin() {
        assumeTrue(
            "U1-R1 acceptance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "U1-R1 acceptance requires the normal AutoJs6 app host rather than a packaged INRT app",
            !BuildConfig.isInrt,
        )
        assertTrue("U1-R1 acceptance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real plugin admits only the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        serviceStateOwned = true
        service.stopAll()
        awaitNoRegisteredEngines("before U1-R1 acceptance")
    }

    @After
    fun settleAfter() {
        if (!serviceStateOwned) return
        service.stopAll()
        awaitNoRegisteredEngines("after U1-R1 acceptance")
    }

    @Test
    fun publicScriptEngineRunsStdinStdlibRelativeImportsAndIsolatesSequentialWorkspaces() {
        val projectRoots = mutableListOf<File>()
        val firstRoot = freshProjectRoot("first").also(projectRoots::add)
        val secondRoot = freshProjectRoot("second").also(projectRoots::add)
        val firstToken = "U1R1_FIRST_${UUID.randomUUID()}"
        val secondToken = "U1R1_SECOND_${UUID.randomUUID()}"
        val createdEngineClasses = CopyOnWriteArrayList<Class<*>>()
        val lifecycle = object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<*>) {
                createdEngineClasses += engine.javaClass
            }

            override fun onEngineRemove(engine: ScriptEngine<*>) = Unit
        }

        service.registerEngineLifecycleCallback(lifecycle)
        try {
            writePrimaryProject(firstRoot, firstToken)
            val firstExpected =
                "$INPUT_PROMPT$firstToken|python=$EXPECTED_PYTHON_VERSION|" +
                    "stdin=你好🙂/第二行/final-no-newline|" +
                    "stdlib=$STDLIB_MARKER|relative=relative-A|workspace=workspace-A\n"
            executeProject(
                projectRoot = firstRoot,
                stdinSnapshot = PRIMARY_STDIN,
                expectedStdout = firstExpected,
                label = "first U1-R1 workspace",
            )

            writeIsolationProject(secondRoot, secondToken)
            val secondExpected =
                "$secondToken|relative=relative-B|workspace=workspace-B\n"
            executeProject(
                projectRoot = secondRoot,
                stdinSnapshot = null,
                expectedStdout = secondExpected,
                label = "second U1-R1 workspace",
            )

            assertEquals(
                "Public ScriptEngineService must create exactly two engines for the sequential runs",
                2,
                createdEngineClasses.size,
            )
            assertTrue(
                "U1-R1 projects escaped the Python engine: $createdEngineClasses",
                createdEngineClasses.all { it == PythonPluginScriptEngine::class.java },
            )
            assertFalse(
                "U1-R1 projects fell back to a JavaScript engine: $createdEngineClasses",
                createdEngineClasses.any { JavaScriptEngine::class.java.isAssignableFrom(it) },
            )
        } finally {
            service.unregisterEngineLifecycleCallback(lifecycle)
            service.stopAll()
            awaitNoRegisteredEngines("during U1-R1 acceptance cleanup")
            projectRoots.forEach { projectRoot ->
                check(projectRoot.deleteRecursively() || !projectRoot.exists()) {
                    "Unable to remove U1-R1 acceptance directory: $projectRoot"
                }
            }
        }
    }

    private fun executeProject(
        projectRoot: File,
        stdinSnapshot: String?,
        expectedStdout: String,
        label: String,
    ): PythonExecutionResult {
        val entry = File(projectRoot, ENTRY_POINT)
        val source = PythonProjectSource.create(projectRoot, entry)
        val config = ExecutionConfig(workingDirectory = projectRoot.canonicalPath).apply {
            stdinSnapshot?.let { setArgument(STDIN_SNAPSHOT_ARGUMENT, it) }
        }
        val pending = PendingProjectExecution(projectRoot.canonicalPath)
        val checkpoint = consoleCheckpoint()
        service.registerGlobalScriptExecutionListener(pending.listener)
        try {
            val submitted = service.execute(source, config)
            assertTrue(
                "$label did not reach the unified engine start callback",
                pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val execution = requireNotNull(pending.execution.get()) {
                "$label started without exposing a ScriptExecution"
            }
            assertEquals("$label returned a different ScriptExecution", submitted.id, execution.id)
            assertEquals("$label did not preserve its ExecutionConfig", config, execution.config)
            assertEquals(
                "$label did not preserve the finite stdin snapshot argument",
                stdinSnapshot,
                execution.config.getArgument(STDIN_SNAPSHOT_ARGUMENT),
            )
            if (!pending.completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                execution.engine.forceStop()
                throw AssertionError("$label did not reach a terminal Host callback")
            }
            awaitEngineRemoved(execution.id, label)

            assertEquals("$label did not start exactly once", 1, pending.startCount.get())
            assertEquals("$label emitted more than one success callback", 1, pending.successCount.get())
            assertEquals("$label emitted an exception callback", 0, pending.exceptionCount.get())
            assertEquals("$label returned an error", null, pending.error.get())
            assertEquals(
                "$label did not preserve PythonProjectSource",
                PythonProjectSource::class.java,
                pending.sourceClass.get(),
            )
            assertEquals(
                "$label source routing drifted",
                PythonScriptSource.ENGINE,
                pending.sourceEngineName.get(),
            )
            assertEquals(
                "$label did not use PythonPluginScriptEngine",
                PythonPluginScriptEngine::class.java,
                pending.engineClass.get(),
            )
            val hostResult = pending.result.get() as? PythonRuntimeExecutionResult
            assertNotNull("$label did not return a Host-owned PythonRuntimeExecutionResult", hostResult)
            requireNotNull(hostResult)
            val result = hostResult.terminal
            assertEquals("$label unexpectedly returned structured JSON", null, hostResult.structuredJson)
            assertTrue("$label unexpectedly returned output artifacts", hostResult.outputArtifacts.isEmpty())
            assertEquals("$label returned a non-zero exit code", 0, result.exitCode)
            assertEquals(
                "$label stdout byte accounting drifted",
                expectedStdout.toByteArray(Charsets.UTF_8).size.toLong(),
                result.stdoutBytes,
            )
            assertEquals("$label unexpectedly emitted stderr", 0L, result.stderrBytes)
            assertConsoleSince(checkpoint, expectedStdout.trimEnd())
            assertPrivateTransportSnapshotsRemoved(label)
            return result
        } finally {
            service.unregisterGlobalScriptExecutionListener(pending.listener)
        }
    }

    private fun writePrimaryProject(projectRoot: File, token: String) {
        writeProjectEnvelope(
            projectRoot = projectRoot,
            relativeValue = "relative-A",
            workspaceValue = "workspace-A",
        )
        File(projectRoot, ENTRY_POINT).writeText(
            """
            import asyncio
            import collections
            import datetime
            import decimal
            import fractions
            import importlib
            import json
            import math
            import pathlib
            import re
            import sys
            from .relative_value import VALUE as relative_value

            first = input("$INPUT_PROMPT")
            remaining = sys.stdin.readlines()
            assert first == "你好🙂"
            assert remaining == ["第二行\n", "final-no-newline"]
            try:
                input()
            except EOFError:
                pass
            else:
                raise AssertionError("finite stdin did not reach EOF")
            assert sys.version_info[:3] == (3, 13, 9)
            assert json.loads('{"ok": true}')["ok"] is True
            assert pathlib.PurePosixPath("alpha/beta").name == "beta"
            assert re.fullmatch(r"[a-z]+", "autojs") is not None
            assert math.factorial(6) == 720
            assert datetime.date(2026, 8, 13).isoformat() == "2026-08-13"
            assert collections.Counter("abca")["a"] == 2
            assert decimal.Decimal("0.1") + decimal.Decimal("0.2") == decimal.Decimal("0.3")
            assert fractions.Fraction(1, 3) + fractions.Fraction(2, 3) == 1

            async def async_probe():
                return "asyncio-ok"

            assert asyncio.run(async_probe()) == "asyncio-ok"
            workspace = importlib.import_module("$WORKSPACE_MODULE")
            assert relative_value == "relative-A"
            assert workspace.VALUE == "workspace-A"
            version = ".".join(str(part) for part in sys.version_info[:3])
            print(
                "$token|python=" + version +
                "|stdin=" + first + "/" + remaining[0].rstrip("\n") + "/" + remaining[1] +
                "|stdlib=$STDLIB_MARKER" +
                "|relative=" + relative_value +
                "|workspace=" + workspace.VALUE,
                flush=True,
            )
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun writeIsolationProject(projectRoot: File, token: String) {
        writeProjectEnvelope(
            projectRoot = projectRoot,
            relativeValue = "relative-B",
            workspaceValue = "workspace-B",
        )
        File(projectRoot, ENTRY_POINT).writeText(
            """
            import importlib
            import sys
            from .relative_value import VALUE as relative_value

            assert sys.stdin.read() == ""
            try:
                input()
            except EOFError:
                pass
            else:
                raise AssertionError("omitted stdin did not produce EOF")
            workspace = importlib.import_module("$WORKSPACE_MODULE")
            assert relative_value == "relative-B"
            assert workspace.VALUE == "workspace-B"
            print(
                "$token|relative=" + relative_value + "|workspace=" + workspace.VALUE,
                flush=True,
            )
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun writeProjectEnvelope(
        projectRoot: File,
        relativeValue: String,
        workspaceValue: String,
    ) {
        File(projectRoot, "project.json").writeText(
            """{"type":"python","main":"$ENTRY_POINT"}""",
            Charsets.UTF_8,
        )
        File(projectRoot, "$WORKSPACE_MODULE.py").writeText(
            "VALUE = \"$workspaceValue\"\n",
            Charsets.UTF_8,
        )
        val packageRoot = File(projectRoot, PACKAGE_NAME).apply {
            check(mkdirs()) { "Unable to create U1-R1 package directory: $this" }
        }
        File(packageRoot, "__init__.py").writeText("", Charsets.UTF_8)
        File(packageRoot, "relative_value.py").writeText(
            "VALUE = \"$relativeValue\"\n",
            Charsets.UTF_8,
        )
    }

    private fun freshProjectRoot(label: String): File = File(
        context.cacheDir,
        "python-u1r1-$label-${UUID.randomUUID()}",
    ).apply {
        check(mkdirs()) { "Unable to create U1-R1 acceptance directory: $this" }
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

    private fun assertPrivateTransportSnapshotsRemoved(label: String) {
        val directory = File(context.noBackupFilesDir, "python-runtime-preview")
        val leftovers = directory.listFiles().orEmpty()
            .filter { file ->
                TRANSPORT_SNAPSHOT_PREFIXES.any { prefix -> file.name.startsWith(prefix) }
            }
        assertTrue("$label left private transport snapshots: $leftovers", leftovers.isEmpty())
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
        const val ARG_ENABLED = "autojs.python.u1r1.acceptance.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val STDIN_SNAPSHOT_ARGUMENT = "autojs6.python.stdinSnapshot"

        const val PACKAGE_NAME = "u1pkg"
        const val ENTRY_POINT = "$PACKAGE_NAME/main.py"
        const val WORKSPACE_MODULE = "workspace_shadow"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val INPUT_PROMPT = "U1R1 input> "
        const val PRIMARY_STDIN = "你好🙂\r\n第二行\r\nfinal-no-newline"
        const val STDLIB_MARKER =
            "json,pathlib,re,math,datetime,collections,decimal,fractions,asyncio,importlib"

        val TRANSPORT_SNAPSHOT_PREFIXES = listOf(
            "source-",
            "workspace-",
            "stdin-",
            "host-capabilities-",
        )

        const val START_TIMEOUT_SECONDS = 20L
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
    }
}
