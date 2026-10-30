package org.autojs.autojs.engine

import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.mozilla.javascript.Undefined
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * R6 release-device coverage for alternating source-routed Rhino and external Node.js engines.
 *
 * This deliberately executes through [AutoJs.scriptEngineService]. It therefore verifies
 * source routing, engine factory selection, execution, engine destruction, plugin session
 * reuse, and process-runtime reuse together instead of testing those contracts in isolation.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NodeR6EngineSwitchInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun settleBefore() {
        NodeJsRuntimePluginHost.invalidateForTest(context)
        service.stopAll()
        awaitNoRegisteredEngines("before R6 engine-switch test")
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        service.stopAll()
        awaitNoRegisteredEngines("after R6 engine-switch test")
        NodeJsRuntimePluginHost.invalidateForTest(context)
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds() {
        assertFixedPluginTopology()
        val workDir = freshWorkDir("alternating")
        val pluginRuns = mutableListOf<PluginRun>()
        try {
            repeat(ALTERNATING_ROUNDS) { round ->
                val firstRhinoToken = "rhino-$round-a"
                val firstRhino = runViaScriptEngineService(
                    workDir = workDir,
                    sourceName = "<r6-rhino-$round-a.js>",
                    source = legacyRhinoSource(firstRhinoToken, "$firstRhinoToken.marker"),
                    markerName = "$firstRhinoToken.marker",
                    markerValue = firstRhinoToken,
                )
                assertLegacyRhinoExecution("round $round first Rhino", firstRhino)

                val firstNodeToken = "node-$round-a"
                pluginRuns += assertRequiredPluginExecution(
                    label = "round $round first Node",
                    observation = runViaScriptEngineService(
                        workDir = workDir,
                        sourceName = "<r6-node-$round-a.js>",
                        source = nativeNodeSource(firstNodeToken, "$firstNodeToken.marker"),
                        markerName = "$firstNodeToken.marker",
                        markerValue = firstNodeToken,
                    ),
                )

                val secondRhinoToken = "rhino-$round-b"
                val secondRhino = runViaScriptEngineService(
                    workDir = workDir,
                    sourceName = "<r6-rhino-$round-b.js>",
                    source = legacyRhinoSource(secondRhinoToken, "$secondRhinoToken.marker"),
                    markerName = "$secondRhinoToken.marker",
                    markerValue = secondRhinoToken,
                )
                assertLegacyRhinoExecution("round $round second Rhino", secondRhino)

                val secondNodeToken = "node-$round-b"
                pluginRuns += assertRequiredPluginExecution(
                    label = "round $round second Node",
                    observation = runViaScriptEngineService(
                        workDir = workDir,
                        sourceName = "<r6-node-$round-b.js>",
                        source = nativeNodeSource(secondNodeToken, "$secondNodeToken.marker"),
                        markerName = "$secondNodeToken.marker",
                        markerValue = secondNodeToken,
                    ),
                )
            }

            assertStablePluginRuntime("alternating Rhino/Node executions", pluginRuns)
            assertNoRegisteredEngines("alternating Rhino/Node executions")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun assertFixedPluginTopology() {
        assertTrue("external Node.js runtime plugin was not requested", NodeJsRuntimePluginBridge.isRequested())
        assertTrue("external Node.js runtime plugin was not required", NodeJsRuntimePluginBridge.isRequired())
    }

    private fun assertLegacyRhinoExecution(label: String, observation: EngineObservation) {
        assertEquals("$label did not use the production Rhino engine", LoopBasedJavaScriptEngine::class.java, observation.engineClass)
        assertEquals("$label source routing drifted", JavaScriptSource.ENGINE, observation.sourceEngineName)
        // The completion value of the source's last expression statement, files.write(...): null before files moved to
        // the new script module layer (augment P3.4), undefined since. Either way not a Node exit code.
        assertTrue(
            "$label unexpectedly returned a loop-engine result: ${observation.result}",
            observation.result == null || observation.result === Undefined.instance,
        )
        assertTrue("$label unexpectedly exposed native Node diagnostics", observation.nativeValues.isEmpty())
    }

    private fun assertRequiredPluginExecution(label: String, observation: EngineObservation): PluginRun {
        assertEquals("$label did not use the Node.js runtime plugin engine", NodePluginJavaScriptEngine::class.java, observation.engineClass)
        assertEquals("$label source routing drifted", JavaScriptSource.ENGINE_NODEJS, observation.sourceEngineName)
        assertEquals("$label did not return Node exit code 0", 0, observation.result)
        assertTrue("$label plugin PID is not remote: ${observation.pid}", observation.pid > 0 && observation.pid != Process.myPid())

        val native = observation.nativeValues
        assertEquals("$label did not dispatch to the required plugin", "true", native[PLUGIN_ENABLED_KEY])
        assertEquals("$label plugin diagnostics attribution drifted", "plugin", native[PLUGIN_DIAGNOSTICS_SOURCE_KEY])
        assertEquals("$label unexpectedly became fallback-safe after dispatch", "false", native[PLUGIN_FALLBACK_SAFE_KEY])
        assertEquals("$label process runtime initialized more than once", "1", native[PROCESS_RUNTIME_INIT_COUNT_KEY])
        assertEquals("$label process runtime is unhealthy", "true", native[PROCESS_RUNTIME_HEALTHY_KEY])
        assertEquals("$label process runtime teardown was not clean", "true", native[PROCESS_RUNTIME_TEARDOWN_KEY])
        assertEquals("$label execution teardown was not clean", "true", native[EXECUTION_TEARDOWN_KEY])

        return PluginRun(
            pid = observation.pid,
            sessionGeneration = requireLongNativeValue(label, native, PLUGIN_SESSION_GENERATION_KEY),
            runtimeGeneration = requireLongNativeValue(label, native, PROCESS_RUNTIME_GENERATION_KEY),
            executionSequence = requireLongNativeValue(label, native, PROCESS_RUNTIME_SEQUENCE_KEY),
            runtimeReused = native[PROCESS_RUNTIME_REUSED_KEY] == "true",
        )
    }

    private fun assertStablePluginRuntime(label: String, runs: List<PluginRun>) {
        assertTrue("$label did not include multiple plugin executions: $runs", runs.size >= 2)
        val first = runs.first()
        runs.forEachIndexed { index, run ->
            assertEquals("$label plugin PID changed at run $index", first.pid, run.pid)
            assertEquals("$label plugin Binder session generation changed at run $index", first.sessionGeneration, run.sessionGeneration)
            assertEquals("$label process runtime generation changed at run $index", first.runtimeGeneration, run.runtimeGeneration)
        }
        runs.zipWithNext().forEachIndexed { index, (previous, current) ->
            assertTrue(
                "$label process runtime sequence was not strictly monotonic at run ${index + 1}: previous=$previous current=$current",
                current.executionSequence > previous.executionSequence,
            )
        }
        runs.drop(1).forEachIndexed { index, run ->
            assertTrue("$label warm plugin execution ${index + 2} did not report runtime reuse: $run", run.runtimeReused)
        }
    }

    private fun requireLongNativeValue(label: String, nativeValues: Map<String, String>, key: String): Long {
        return checkNotNull(nativeValues[key]?.toLongOrNull()) {
            "$label omitted numeric native diagnostic $key: $nativeValues"
        }
    }

    private fun runViaScriptEngineService(
        workDir: File,
        sourceName: String,
        source: String,
        markerName: String,
        markerValue: String,
    ): EngineObservation {
        val marker = File(workDir, markerName).apply { delete() }
        val completed = CountDownLatch(1)
        val startedEngineClass = AtomicReference<Class<*>>()
        val observation = AtomicReference<EngineObservation>()
        val failure = AtomicReference<Throwable>()
        val sourceFile = File(workDir, sourceName.removeSurrounding("<", ">")).apply {
            writeText(source)
        }
        val sourceObject = JavaScriptFileSource(sourceFile)
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                startedEngineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                val engine = execution.engine
                val pluginEngine = engine as? NodePluginJavaScriptEngine
                val pluginResult = pluginEngine?.lastPluginScriptResult
                observation.set(
                    EngineObservation(
                        executionId = execution.id,
                        engineClass = engine.javaClass,
                        sourceEngineName = execution.source.engineName.orEmpty(),
                        result = result,
                        pid = pluginResult?.pid ?: -1,
                        nativeValues = pluginResult?.nativeValues.orEmpty(),
                    ),
                )
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }

        val execution = try {
            service.execute(
                sourceObject,
                listener,
                ExecutionConfig(workingDirectory = workDir.absolutePath),
            )
        } catch (error: Throwable) {
            service.stopAll()
            awaitNoRegisteredEngines("after synchronous failure for $sourceName")
            throw AssertionError("Failed to start $sourceName through ScriptEngineService", error)
        }

        if (!completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            service.stopAll()
            awaitNoRegisteredEngines("after timeout for $sourceName")
            throw AssertionError("Timed out waiting for ScriptEngineService execution $sourceName")
        }
        awaitEngineRemoved(execution.id, sourceName)
        failure.get()?.let { error ->
            throw AssertionError("ScriptEngineService execution failed for $sourceName", error)
        }
        val completedObservation = checkNotNull(observation.get()) {
            "ScriptEngineService execution $sourceName completed without an observation"
        }
        assertEquals("$sourceName changed engine instances between start and success", startedEngineClass.get(), completedObservation.engineClass)
        assertEquals("$sourceName did not write its execution marker", markerValue, marker.takeIf(File::isFile)?.readText())
        assertNoRegisteredEngines("after $sourceName")
        return completedObservation
    }

    private fun awaitEngineRemoved(executionId: Int, label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) {
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MS)
        }
        val engines = service.engines.joinToString { engine -> "${engine.id}:${engine.javaClass.name}" }
        throw AssertionError("Engine $executionId remained registered after $label: [$engines]")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty()) {
                assertTrue("$label left tracked script executions: ${service.scriptExecutions}", service.scriptExecutions.isEmpty())
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MS)
        }
        assertNoRegisteredEngines(label)
    }

    private fun assertNoRegisteredEngines(label: String) {
        val engines = service.engines
        assertTrue(
            "$label left registered engines: ${engines.joinToString { engine -> "${engine.id}:${engine.javaClass.name}" }}",
            engines.isEmpty(),
        )
        assertTrue("$label left tracked script executions: ${service.scriptExecutions}", service.scriptExecutions.isEmpty())
    }

    private fun freshWorkDir(label: String): File =
        File(context.cacheDir, "node-r6-engine-switch-$label-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create R6 engine-switch working directory: $absolutePath" }
        }

    private fun legacyRhinoSource(token: String, markerName: String): String =
        """
        "rhino";
        if (typeof __autojs6R6RhinoState !== "undefined") {
          throw new Error("Rhino global state leaked across engine instances");
        }
        this.__autojs6R6RhinoState = "$token";
        files.write("./$markerName", "$token");
        if (files.read("./$markerName") !== "$token") {
          throw new Error("Rhino marker verification failed");
        }
        """.trimIndent()

    private fun nativeNodeSource(token: String, markerName: String): String =
        """
        "nodejs";
        const fs = require("node:fs");
        if (!process || !process.versions || typeof process.versions.node !== "string") {
          throw new Error("Native Node process contract is unavailable");
        }
        if (Object.prototype.hasOwnProperty.call(globalThis, "__autojs6R6NodeState")) {
          throw new Error("Node global state leaked across persistent-runtime isolates");
        }
        globalThis.__autojs6R6NodeState = "$token";
        fs.writeFileSync("./$markerName", "$token", "utf8");
        if (fs.readFileSync("./$markerName", "utf8") !== "$token") {
          throw new Error("Node marker verification failed");
        }
        """.trimIndent()

    private data class EngineObservation(
        val executionId: Int,
        val engineClass: Class<*>,
        val sourceEngineName: String,
        val result: Any?,
        val pid: Int,
        val nativeValues: Map<String, String>,
    )

    private data class PluginRun(
        val pid: Int,
        val sessionGeneration: Long,
        val runtimeGeneration: Long,
        val executionSequence: Long,
        val runtimeReused: Boolean,
    )

    companion object {
        private const val ALTERNATING_ROUNDS = 3
        private const val EXECUTION_TIMEOUT_SECONDS = 30L
        private const val ENGINE_REMOVAL_TIMEOUT_MS = 5_000L
        private const val ENGINE_REMOVAL_POLL_MS = 25L

        private const val PLUGIN_ENABLED_KEY = "embedded_script.runtime_plugin.enabled"
        private const val PLUGIN_DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        private const val PLUGIN_FALLBACK_SAFE_KEY = "embedded_script.runtime_plugin.fallback_safe"
        private const val PLUGIN_SESSION_GENERATION_KEY = "embedded_script.runtime_plugin.session_generation"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_HEALTHY_KEY = "process_runtime.healthy"
        private const val PROCESS_RUNTIME_TEARDOWN_KEY = "process_runtime.teardown_clean"
        private const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"
    }
}
