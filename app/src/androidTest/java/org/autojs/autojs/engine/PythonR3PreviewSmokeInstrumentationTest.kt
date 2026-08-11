package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.python.PythonRuntimeDispatchRegistry
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
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
 * R3-P3 representative-device smoke for the default-off single-file Python preview.
 *
 * This intentionally enters through [Scripts.run], rather than talking to the Python Binder
 * protocol directly. One opt-in method covers only the missing vertical slice: local `.py`
 * routing, the production [ScriptEngineService], the real plugin, host-visible output and
 * exception handling, [PythonPluginScriptEngine.forceStop], and one fresh execution after
 * process retirement. The broader R2 protocol/device matrix is deliberately not repeated here.
 */
class PythonR3PreviewSmokeInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService
    private val console
        get() = AutoJs.instance.globalConsole
    private var serviceStateOwned = false

    @Before
    fun requireExplicitPreviewAndRealPlugin() {
        assumeTrue(
            "R3-P3 real-plugin smoke is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "R3-P3 requires a Debug host built with -Pautojs.python.preview.enabled=true",
            BuildConfig.DEBUG && BuildConfig.PYTHON_RUNTIME_PREVIEW_ENABLED,
        )
        assertTrue("R3-P3 requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real plugin admits only the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        serviceStateOwned = true
        service.stopAll()
        awaitNoRegisteredEngines("before R3-P3 smoke")
    }

    @After
    fun settleAfter() {
        if (!serviceStateOwned) return
        service.stopAll()
        awaitNoRegisteredEngines("after R3-P3 smoke")
    }

    @Test
    fun localPyUsesPythonEngineForOutputFailureForceStopAndRecoveryWithoutFallback() {
        val workDir = freshWorkDir()
        val createdEngineClasses = CopyOnWriteArrayList<Class<*>>()
        val lifecycle = object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<*>) {
                createdEngineClasses += engine.javaClass
            }

            override fun onEngineRemove(engine: ScriptEngine<*>) = Unit
        }
        service.registerEngineLifecycleCallback(lifecycle)
        try {
            val successToken = "R3P_SUCCESS_${UUID.randomUUID()}"
            val successCheckpoint = consoleCheckpoint()
            val success = runToTerminal(
                writeSource(workDir, "success.py", "print(\"$successToken\", flush=True)\n"),
            )
            assertPythonSuccess("normal output", success, successToken)
            assertConsoleSince(successCheckpoint, successToken)
            settleRetiredPluginProcess()

            val failureToken = "R3P_EXCEPTION_${UUID.randomUUID()}"
            val failureCheckpoint = consoleCheckpoint()
            val failure = runToTerminal(
                writeSource(
                    workDir,
                    "failure.py",
                    "raise RuntimeError(\"$failureToken\")\n",
                ),
            )
            assertPythonFailure(failure)
            assertConsoleSince(failureCheckpoint, "PYTHON_EXCEPTION/EXECUTION")
            assertConsoleSince(failureCheckpoint, "RuntimeError")
            assertConsoleSince(failureCheckpoint, failureToken)
            settleRetiredPluginProcess()

            val interrupted = launchViaScripts(
                writeSource(
                    workDir,
                    "force_stop.py",
                    "value = 0\nwhile True:\n    value = (value + 1) & 0x7fffffff\n",
                ),
            )
            if (!interrupted.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                interrupted.execution.get()?.engine?.forceStop()
                interrupted.dispose()
                throw AssertionError("Python force-stop source did not reach the unified engine start callback")
            }
            val interruptedExecution = requireNotNull(interrupted.execution.get()) {
                "Python force-stop source started without a ScriptExecution"
            }
            val interruptedObservation = try {
                awaitRemotePluginStarted(interruptedExecution, interrupted.completed)
                forceStopUntilTerminal(interruptedExecution, interrupted.completed)
                interrupted.finish(interruptedExecution)
            } catch (error: Throwable) {
                throw error
            } finally {
                interrupted.dispose()
            }
            assertPythonInterruption(interruptedObservation)

            // R2 owns exact generation/death accounting. Here we only wait through the plugin's
            // bounded retirement window, then prove one explicit new dispatch succeeds; the
            // cancelled request itself is never replayed.
            settleRetiredPluginProcess()
            val recoveryToken = "R3P_RECOVERY_${UUID.randomUUID()}"
            val recoveryCheckpoint = consoleCheckpoint()
            val recovered = runToTerminal(
                writeSource(workDir, "recovery.py", "print(\"$recoveryToken\", flush=True)\n"),
            )
            assertPythonSuccess("post-forceStop recovery", recovered, recoveryToken)
            assertConsoleSince(recoveryCheckpoint, recoveryToken)

            assertEquals(
                "The vertical slice must create exactly one engine per explicit Scripts.run call",
                4,
                createdEngineClasses.size,
            )
            assertTrue(
                "A local .py execution escaped the Python engine: $createdEngineClasses",
                createdEngineClasses.all { it == PythonPluginScriptEngine::class.java },
            )
            assertFalse(
                "Python failure or cancellation fell back to a JavaScript engine: $createdEngineClasses",
                createdEngineClasses.any { JavaScriptEngine::class.java.isAssignableFrom(it) },
            )
            awaitNoRegisteredEngines("after R3-P3 vertical slice")
        } finally {
            service.unregisterEngineLifecycleCallback(lifecycle)
            workDir.deleteRecursively()
        }
    }

    private fun runToTerminal(file: File): ExecutionObservation {
        val pending = launchViaScripts(file)
        try {
            val execution = requireNotNull(pending.execution.get()) {
                "Scripts.run returned no execution for ${file.name}"
            }
            if (!pending.completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                execution.engine.forceStop()
                throw AssertionError("${file.name} did not reach a terminal ScriptEngineService callback")
            }
            return pending.finish(execution)
        } finally {
            pending.dispose()
        }
    }

    private fun launchViaScripts(file: File): PendingExecution {
        val expectedPath = file.canonicalPath
        val pending = PendingExecution(expectedPath)
        service.registerGlobalScriptExecutionListener(pending.listener)
        try {
            val execution = Scripts.run(context, ScriptFile(file))
            pending.execution.set(execution)
            if (execution == null) {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
            return pending
        } catch (error: Throwable) {
            service.unregisterGlobalScriptExecutionListener(pending.listener)
            throw AssertionError("Unable to launch ${file.name} through Scripts.run", error)
        }
    }

    private fun forceStopUntilTerminal(execution: ScriptExecution, completed: CountDownLatch) {
        val deadline = SystemClock.elapsedRealtime() + FORCE_STOP_TIMEOUT_MILLIS
        do {
            execution.engine.forceStop()
            if (completed.await(FORCE_STOP_POLL_MILLIS, TimeUnit.MILLISECONDS)) return
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Python forceStop() did not reach a terminal host callback")
    }

    private fun awaitRemotePluginStarted(execution: ScriptExecution, completed: CountDownLatch) {
        val engine = execution.engine as? PythonPluginScriptEngine
            ?: throw AssertionError("forceStop source did not create PythonPluginScriptEngine")
        val deadline = SystemClock.elapsedRealtime() + REMOTE_START_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (completed.count == 0L) {
                throw AssertionError("Python force-stop source terminated before the real plugin reported onStarted")
            }
            val clientExecution = privateAtomicReference(engine, "activeExecution").get()
            if (clientExecution != null) {
                val reservation = privateAtomicReference(clientExecution, "reservation").get()
                    as? PythonRuntimeDispatchRegistry.Reservation
                if (reservation?.plan?.admissionPlan?.sessionPolicy?.state ==
                    PythonExecutionSessionPolicy.State.STARTED
                ) {
                    return
                }
            }
            SystemClock.sleep(REMOTE_START_POLL_MILLIS)
        }
        throw AssertionError("Real Python Runtime did not report onStarted before forceStop")
    }

    private fun privateAtomicReference(owner: Any, fieldName: String): AtomicReference<*> {
        val field = owner.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(owner) as? AtomicReference<*>
            ?: throw AssertionError("$fieldName is not an AtomicReference on ${owner.javaClass.name}")
    }

    private fun settleRetiredPluginProcess() {
        // The real plugin's terminal session close and started cancellation both retire its
        // dedicated runtime process after a two-second bounded callback-drain window.
        SystemClock.sleep(PROCESS_RETIREMENT_SETTLE_MILLIS)
    }

    private fun assertPythonSuccess(label: String, observation: ExecutionObservation, token: String) {
        assertPythonEngine(label, observation)
        assertEquals("$label unexpectedly failed", null, observation.error)
        assertEquals("$label emitted more than one success callback", 1, observation.successCount)
        assertEquals("$label emitted an exception callback", 0, observation.exceptionCount)
        val result = observation.result as? PythonExecutionResult
        assertNotNull("$label did not return a typed PythonExecutionResult", result)
        requireNotNull(result)
        assertEquals("$label returned a non-zero exit code", 0, result.exitCode)
        assertEquals(
            "$label stdout byte accounting drifted",
            "$token\n".toByteArray(Charsets.UTF_8).size.toLong(),
            result.stdoutBytes,
        )
        assertEquals("$label unexpectedly emitted stderr", 0L, result.stderrBytes)
    }

    private fun assertPythonFailure(observation: ExecutionObservation) {
        assertPythonEngine("Python exception", observation)
        assertEquals("Python exception emitted a success callback", 0, observation.successCount)
        assertEquals("Python exception did not emit exactly one exception callback", 1, observation.exceptionCount)
        val error = requireNotNull(observation.error) { "Python exception returned no host error" }
        assertTrue(
            "Python exception lost its stable host code: $error",
            error.message.orEmpty().startsWith("PYTHON_RUNTIME_EXECUTION_FAILED:"),
        )
        assertTrue(
            "Python exception lost its protocol phase: $error",
            error.message.orEmpty().contains("PYTHON_EXCEPTION/EXECUTION"),
        )
    }

    private fun assertPythonInterruption(observation: ExecutionObservation) {
        assertPythonEngine("forceStop", observation)
        assertEquals("forceStop emitted a success callback", 0, observation.successCount)
        assertEquals("forceStop did not emit exactly one exception callback", 1, observation.exceptionCount)
        val error = requireNotNull(observation.error) { "forceStop returned no interruption" }
        assertTrue(
            "forceStop was not reported as a ScriptInterruptedException: $error",
            ScriptInterruptedException.causedByInterrupt(error),
        )
    }

    private fun assertPythonEngine(label: String, observation: ExecutionObservation) {
        assertEquals("$label did not start exactly once", 1, observation.startCount)
        assertEquals("$label did not use PythonPluginScriptEngine", PythonPluginScriptEngine::class.java, observation.engineClass)
        assertEquals("$label source routing drifted", PythonScriptSource.ENGINE, observation.sourceEngineName)
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

    private fun writeSource(workDir: File, name: String, source: String): File =
        File(workDir, name).apply { writeText(source, Charsets.UTF_8) }

    private fun freshWorkDir(): File = File(
        context.cacheDir,
        "python-r3p-smoke-${UUID.randomUUID()}",
    ).apply {
        check(mkdirs()) { "Unable to create R3-P3 work directory: $this" }
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

    private inner class PendingExecution(
        private val expectedCanonicalPath: String,
    ) {
        val execution = AtomicReference<ScriptExecution?>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        private val startCount = AtomicInteger(0)
        private val successCount = AtomicInteger(0)
        private val exceptionCount = AtomicInteger(0)
        private val engineClass = AtomicReference<Class<*>?>()
        private val sourceEngineName = AtomicReference<String?>()
        private val result = AtomicReference<Any?>()
        private val error = AtomicReference<Throwable?>()

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                if (!matches(execution)) return
                this@PendingExecution.execution.compareAndSet(null, execution)
                startCount.incrementAndGet()
                engineClass.compareAndSet(null, execution.engine.javaClass)
                sourceEngineName.compareAndSet(null, execution.source.engineName)
                started.countDown()
            }

            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                if (!matches(execution)) return
                this@PendingExecution.execution.compareAndSet(null, execution)
                successCount.incrementAndGet()
                result.set(value)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, throwable: Throwable) {
                if (!matches(execution)) return
                this@PendingExecution.execution.compareAndSet(null, execution)
                exceptionCount.incrementAndGet()
                error.set(throwable)
                completed.countDown()
            }
        }

        fun finish(completedExecution: ScriptExecution): ExecutionObservation {
            awaitEngineRemoved(completedExecution.id, expectedCanonicalPath)
            return ExecutionObservation(
                engineClass = engineClass.get(),
                sourceEngineName = sourceEngineName.get(),
                result = result.get(),
                error = error.get(),
                startCount = startCount.get(),
                successCount = successCount.get(),
                exceptionCount = exceptionCount.get(),
            )
        }

        fun dispose() {
            service.unregisterGlobalScriptExecutionListener(listener)
        }

        private fun matches(execution: ScriptExecution): Boolean {
            val source = execution.source as? PythonFileSource ?: return false
            return runCatching { source.file.canonicalPath }.getOrElse { source.file.absolutePath } == expectedCanonicalPath
        }
    }

    private data class ExecutionObservation(
        val engineClass: Class<*>?,
        val sourceEngineName: String?,
        val result: Any?,
        val error: Throwable?,
        val startCount: Int,
        val successCount: Int,
        val exceptionCount: Int,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.python.r3p.smoke.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"

        const val START_TIMEOUT_SECONDS = 20L
        const val EXECUTION_TIMEOUT_SECONDS = 75L
        const val REMOTE_START_TIMEOUT_MILLIS = 20_000L
        const val REMOTE_START_POLL_MILLIS = 50L
        const val FORCE_STOP_TIMEOUT_MILLIS = 20_000L
        const val FORCE_STOP_POLL_MILLIS = 100L
        const val PROCESS_RETIREMENT_SETTLE_MILLIS = 2_500L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
    }
}
