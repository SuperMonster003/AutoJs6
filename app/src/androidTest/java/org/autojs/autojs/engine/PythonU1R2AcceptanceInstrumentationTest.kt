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
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
 * Opt-in U1-R2 acceptance through the public Host engine and the exact real CPython plugin.
 *
 * The test deliberately keeps stdout diagnostic-only. The explicit JSON value and the artifact
 * are returned through the Host-owned result surface, and the artifact bytes are checked after the
 * Host has enforced descriptor length, EOF, and SHA-256.
 */
class PythonU1R2AcceptanceInstrumentationTest {

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
            "U1-R2 public acceptance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "U1-R2 acceptance requires the normal AutoJs6 host rather than an INRT package",
            !BuildConfig.isInrt,
        )
        assertTrue("U1-R2 acceptance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("U1-R2 acceptance requires the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        serviceStateOwned = true
        service.stopAll()
        awaitNoRegisteredEngines("before U1-R2 public acceptance")
    }

    @After
    fun settleAfter() {
        if (!serviceStateOwned) return
        service.stopAll()
        awaitNoRegisteredEngines("after U1-R2 public acceptance")
    }

    @Test
    fun publicModuleEngineReturnsExplicitJsonAndExactArtifactWithoutInferringStdout() {
        val projectRoot = freshProjectRoot()
        val token = "U1R2_PUBLIC_${UUID.randomUUID()}"
        val createdEngineClasses = CopyOnWriteArrayList<Class<*>>()
        val lifecycle = object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<*>) {
                createdEngineClasses += engine.javaClass
            }

            override fun onEngineRemove(engine: ScriptEngine<*>) = Unit
        }

        service.registerEngineLifecycleCallback(lifecycle)
        try {
            writeProject(projectRoot, token)
            val entry = File(projectRoot, ENTRY_POINT)
            val source = PythonProjectSource.create(projectRoot, entry)
            val config = ExecutionConfig(workingDirectory = projectRoot.canonicalPath).apply {
                setArgument(ENTRY_MODE_ARGUMENT, "module")
            }
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                val submitted = service.execute(source, config)
                assertTrue(
                    "U1-R2 module execution did not reach the unified start callback",
                    pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                val execution = requireNotNull(pending.execution.get()) {
                    "U1-R2 module execution started without exposing ScriptExecution"
                }
                assertEquals(submitted.id, execution.id)
                assertEquals("module", execution.config.getArgument(ENTRY_MODE_ARGUMENT))
                if (!pending.completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    execution.engine.forceStop()
                    throw AssertionError("U1-R2 module execution did not reach a Host terminal callback")
                }
                awaitEngineRemoved(execution.id, "U1-R2 public module acceptance")

                assertEquals(1, pending.startCount.get())
                assertEquals(1, pending.successCount.get())
                assertEquals(0, pending.exceptionCount.get())
                assertEquals(null, pending.error.get())
                assertEquals(PythonProjectSource::class.java, pending.sourceClass.get())
                assertEquals(PythonPluginScriptEngine::class.java, pending.engineClass.get())

                val result = pending.result.get() as? PythonRuntimeExecutionResult
                assertNotNull("Public Python engine did not return the Host-owned result type", result)
                requireNotNull(result)
                val expectedStdout = "$token\n$STDOUT_JSON_LOOKALIKE"
                assertEquals(0, result.terminal.exitCode)
                assertEquals(expectedStdout.toByteArray(Charsets.UTF_8).size.toLong(), result.terminal.stdoutBytes)
                assertEquals(EXPECTED_STDERR.toByteArray(Charsets.UTF_8).size.toLong(), result.terminal.stderrBytes)
                assertEquals(EXPECTED_STRUCTURED_JSON, result.structuredJson)
                assertFalse(
                    "A JSON-looking stdout line was incorrectly inferred as the explicit result",
                    result.structuredJson == STDOUT_JSON_LOOKALIKE.trim(),
                )
                assertEquals(1, result.outputArtifacts.size)
                val artifact = result.outputArtifacts.single()
                assertEquals(ARTIFACT_PATH, artifact.path)
                assertEquals(ARTIFACT_BYTES.size.toLong(), artifact.sizeBytes)
                assertEquals(PythonSha256.digest(ARTIFACT_BYTES), artifact.sha256)
                assertArrayEquals(ARTIFACT_BYTES, artifact.toByteArray())
                val mutatedCopy = artifact.toByteArray().also { bytes -> if (bytes.isNotEmpty()) bytes[0] = 0 }
                assertFalse("Mutating a returned artifact copy unexpectedly changed no bytes", mutatedCopy.contentEquals(ARTIFACT_BYTES))
                assertArrayEquals(
                    "Host-owned artifact bytes must be defensive copies",
                    ARTIFACT_BYTES,
                    artifact.toByteArray(),
                )

                assertConsoleSince(checkpoint, token)
                assertConsoleSince(checkpoint, STDOUT_JSON_LOOKALIKE.trim())
                assertConsoleSince(checkpoint, EXPECTED_STDERR.trim())
                assertPrivateTransportSnapshotsRemoved()
                assertEquals(listOf(PythonPluginScriptEngine::class.java), createdEngineClasses)
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            service.unregisterEngineLifecycleCallback(lifecycle)
            service.stopAll()
            awaitNoRegisteredEngines("during U1-R2 public acceptance cleanup")
            check(projectRoot.deleteRecursively() || !projectRoot.exists()) {
                "Unable to remove U1-R2 acceptance directory: $projectRoot"
            }
        }
    }

    private fun writeProject(projectRoot: File, token: String) {
        File(projectRoot, "project.json").writeText(
            """{"type":"python","main":"$ENTRY_POINT"}""",
            Charsets.UTF_8,
        )
        val packageRoot = File(projectRoot, PACKAGE_NAME).apply {
            check(mkdirs()) { "Unable to create U1-R2 package directory: $this" }
        }
        File(packageRoot, "__init__.py").writeText("", Charsets.UTF_8)
        File(packageRoot, "relative_value.py").writeText("VALUE = \"relative-r2\"\n", Charsets.UTF_8)
        File(projectRoot, ENTRY_POINT).writeText(
            """
            import sys
            from autojs6 import artifacts, result
            from .relative_value import VALUE

            assert __name__ == "__main__"
            assert __package__ == "$PACKAGE_NAME"
            assert __spec__ is not None
            assert __spec__.name == "$PACKAGE_NAME.main"
            assert VALUE == "relative-r2"

            print("$token", flush=True)
            print('$STDOUT_JSON_LOOKALIKE_ESCAPED', flush=True)
            sys.stderr.write("$STDERR_MARKER\\n")
            sys.stderr.flush()
            artifact_path = artifacts.path("$ARTIFACT_PATH")
            with open(artifact_path, "wb") as output:
                output.write(b"U1-R2 artifact bytes\\x00\\xff")
            result.set({
                "artifact": "$ARTIFACT_PATH",
                "module": __spec__.name,
                "package": __package__,
                "relative": VALUE,
                "stdoutIsResult": False,
            })
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun freshProjectRoot(): File = File(
        context.cacheDir,
        "python-u1r2-public-${UUID.randomUUID()}",
    ).apply {
        check(mkdirs()) { "Unable to create U1-R2 acceptance directory: $this" }
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

    private fun assertPrivateTransportSnapshotsRemoved() {
        val directory = File(context.noBackupFilesDir, "python-runtime-preview")
        val leftovers = directory.listFiles().orEmpty().filter { file ->
            TRANSPORT_SNAPSHOT_PREFIXES.any { prefix -> file.name.startsWith(prefix) }
        }
        assertTrue("U1-R2 public execution left private Host snapshots: $leftovers", leftovers.isEmpty())
    }

    private fun awaitEngineRemoved(executionId: Int, label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Engine $executionId remained registered after $label: ${service.engines}")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty()) {
                assertTrue("$label left tracked script executions", service.scriptExecutions.isEmpty())
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("$label left engines=${service.engines}, executions=${service.scriptExecutions}")
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private inner class PendingProjectExecution(private val expectedProjectRoot: String) {
        val execution = AtomicReference<ScriptExecution?>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val startCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val engineClass = AtomicReference<Class<*>?>()
        val sourceClass = AtomicReference<Class<*>?>()
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                startCount.incrementAndGet()
                engineClass.compareAndSet(null, execution.engine.javaClass)
                sourceClass.compareAndSet(null, execution.source.javaClass)
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
        const val ARG_ENABLED = "autojs.python.u1r2.public.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val ENTRY_MODE_ARGUMENT = "autojs6.python.entryMode"
        const val PACKAGE_NAME = "u1r2case"
        const val ENTRY_POINT = "$PACKAGE_NAME/main.py"
        const val ARTIFACT_PATH = "reports/u1-r2.bin"
        const val STDERR_MARKER = "U1R2_STDERR_DIAGNOSTIC"
        const val STDOUT_JSON_LOOKALIKE = "{\"stdout\":\"diagnostic-only\"}\n"
        const val STDOUT_JSON_LOOKALIKE_ESCAPED = "{\\\"stdout\\\":\\\"diagnostic-only\\\"}"
        const val EXPECTED_STDERR = "$STDERR_MARKER\n"
        const val EXPECTED_STRUCTURED_JSON =
            "{\"artifact\":\"$ARTIFACT_PATH\",\"module\":\"$PACKAGE_NAME.main\"," +
                "\"package\":\"$PACKAGE_NAME\",\"relative\":\"relative-r2\",\"stdoutIsResult\":false}"
        val ARTIFACT_BYTES = "U1-R2 artifact bytes".toByteArray(Charsets.UTF_8) + byteArrayOf(0, -1)

        val TRANSPORT_SNAPSHOT_PREFIXES = listOf("source-", "workspace-", "stdin-", "host-capabilities-")
        const val START_TIMEOUT_SECONDS = 20L
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
    }
}
