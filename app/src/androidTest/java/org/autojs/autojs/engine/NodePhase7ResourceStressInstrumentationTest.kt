package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodePhase7ResourceStressInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val ownerKeys = mutableSetOf<String>()

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 7 resource stress")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        NodeBridgeUiSessionStore.closeAll("phase7-resource-stress-before")
        ownerKeys.clear()
    }

    @After
    fun settleAfter() {
        ownerKeys.forEach { ownerKey ->
            NodeWorkManagerBridgeScheduler.cancelAllForOwner(context, ownerKey)
        }
        ownerKeys.clear()
        NodeBridgeUiSessionStore.closeAll("phase7-resource-stress-after")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun telemetryProbeRecordsHostBridgeAndLibuvMetrics() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "phase7-resource-telemetry"),
        )
        try {
            val before = NodeResourceStressSnapshot.capture(
                label = "phase7.telemetry.before",
                bridgeDiagnostics = provider.nativeDiagnostics(),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    function handleCount() {
                      try {
                        return typeof process._getActiveHandles === "function" ? process._getActiveHandles().length : -1;
                      } catch (error) {
                        return -2;
                      }
                    }
                    const before = handleCount();
                    const timer = setInterval(() => {}, 1000);
                    const during = handleCount();
                    clearInterval(timer);
                    setImmediate(() => {
                      console.log("phase7.resource.libuv_handles=" + [before, during, handleCount()].join("|"));
                    });
                """.trimIndent(),
                sourceName = "<phase7-resource-telemetry.js>",
                timeoutMs = 5_000L,
            )
            NodeEmbeddedTestHarness.assertSucceeded("Phase 7 resource telemetry libuv probe", result)
            val libuvCounts = parsePipeInts(result.stdout, "phase7.resource.libuv_handles", expectedCount = 3)
            val after = NodeResourceStressSnapshot.capture(
                label = "phase7.telemetry.after",
                bridgeDiagnostics = provider.nativeDiagnostics(),
                libuvHandleCount = libuvCounts.last(),
            )
            val report = JSONObject()
                .put("schema", "autojs6-node-phase7-resource-stress-v1")
                .put("samples", JSONArray().put(before.toJson()).put(after.toJson()))
            println("phase7.resource.telemetry=$report")

            assertSnapshotContainsRequiredMetrics(before)
            assertSnapshotContainsRequiredMetrics(after)
            assertTrue(after.toJson().getJSONObject("bridgeDiagnostics").has("embedded_script.bridge_resource.active_count"))
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead("phase7 resource telemetry cleanup"))
        }
    }

    @Test
    fun databaseAndWorkManagerStressReturnResourcesToZeroWithTelemetry() {
        val workDir = freshWorkDir("node-phase7-resource-provider")
        writeProjectPermissions(workDir, "database", "work_manager")
        val engineInfo = NodeBridgeEngineInfo.fromContext(
            context,
            id = "phase7-resource-provider",
            sourceName = "main.cjs",
            cwd = workDir.absolutePath,
        )
        ownerKeys += NodeWorkManagerBridgeScheduler.ownerKey(engineInfo)
        val provider = AndroidNodeBridgeCapabilityProvider(context, engineInfo)
        val samples = JSONArray()
        try {
            samples.put(NodeResourceStressSnapshot.capture("phase7.provider.before", provider.nativeDiagnostics()).toJson())
            repeat(providerStressIterations()) { index ->
                databaseRoundTrip(provider, index)
                samples.put(NodeResourceStressSnapshot.capture("phase7.provider.database.$index", provider.nativeDiagnostics()).toJson())
            }
            repeat(workManagerExecutionIterations()) { index ->
                workManagerExecutionRoundTrip(provider, workDir, index)
                samples.put(NodeResourceStressSnapshot.capture("phase7.provider.work_manager.$index", provider.nativeDiagnostics()).toJson())
            }

            val diagnostics = provider.nativeDiagnostics()
            val report = JSONObject()
                .put("schema", "autojs6-node-phase7-provider-resource-stress-v1")
                .put("databaseIterations", providerStressIterations())
                .put("workManagerExecutionIterations", workManagerExecutionIterations())
                .put("samples", samples)
                .put("finalDiagnostics", JSONObject(diagnostics))
            println("phase7.resource.provider.report=$report")

            assertNoActiveProviderResources(provider, "phase7 provider final")
            assertEquals("0", diagnostics["embedded_script.database_active_handle_count"])
            assertEquals("0", diagnostics["embedded_script.work_manager_registered_task_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead("phase7 provider resource stress cleanup"))
            ownerKeys.forEach { NodeWorkManagerBridgeScheduler.cancelAllForOwner(context, it) }
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pluginLoadUnloadStressClosesLifecycleResourcesAndRecordsLibuvHandles() {
        val workDir = freshWorkDir("node-phase7-plugin-stress")
        val pluginId = "org.example.phase7stress"
        writePlugin(
            workDir = workDir,
            id = pluginId,
            source = """
                let starts = 0;
                let stops = 0;
                exports.start = function(ctx) {
                  starts += 1;
                  ctx.resources.setInterval(function() {}, 5000);
                  ctx.resources.track("provider", { close: function() {} });
                  ctx.resources.track("ui", { close: function() {} });
                  ctx.resources.track("socket", { close: function() {} });
                  ctx.resources.track("image", { recycle: function() {} });
                  ctx.resources.addCleanup(function() {});
                };
                exports.stop = function() {
                  stops += 1;
                };
                exports.state = function() {
                  return { starts, stops };
                };
            """.trimIndent(),
        )
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("plugin.$pluginId"))
        try {
            val iterations = pluginStressIterations()
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    const id = "$pluginId";
                    function handleCount() {
                      try {
                        return typeof process._getActiveHandles === "function" ? process._getActiveHandles().length : -1;
                      } catch (error) {
                        return -2;
                      }
                    }
                    plugins.install(id, { source: "./plugins/" + id });
                    const beforeHandles = handleCount();
                    let ok = 0;
                    let maxResources = 0;
                    for (let index = 0; index < $iterations; index += 1) {
                      plugins.load(id);
                      const started = plugins.start(id);
                      const running = plugins.status(id);
                      maxResources = Math.max(maxResources, running.resources.total);
                      const stopped = plugins.stop(id);
                      const unloaded = plugins.unload(id);
                      if (started.started === true && stopped.resources.total === 0 && unloaded === true) {
                        ok += 1;
                      }
                    }
                    const finalStatus = plugins.status(id);
                    setImmediate(() => {
                      console.log("phase7.resource.plugin=" + [
                        $iterations,
                        ok,
                        maxResources,
                        finalStatus.resources.total,
                        finalStatus.loaded,
                        finalStatus.started,
                        beforeHandles,
                        handleCount()
                      ].join("|"));
                    });
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 20_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Phase 7 plugin load/unload resource stress", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("phase7.resource.plugin=$iterations|$iterations|6|0|false|false|"),
            )
            val values = parsePipeValues(result.stdout, "phase7.resource.plugin", expectedCount = 8)
            val beforeHandles = values[6].toIntOrNull()
            val afterHandles = values[7].toIntOrNull()
            assertTrue(
                "libuv handle count was not recorded: ${result.stdout}",
                beforeHandles != null && beforeHandles >= -2 && afterHandles != null && afterHandles >= -2,
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun databaseRoundTrip(provider: AndroidNodeBridgeCapabilityProvider, index: Int) {
        val open = dispatch(
            provider = provider,
            module = "database",
            method = "open",
            argsJson = JSONArray().put(JSONObject().put("name", "phase7_resource_$index")).toString(),
            permissions = listOf("database"),
        )
        assertTrue(open.toString(), open.ok)
        val handleId = JSONObject(open.resultJson.orEmpty()).getString("id")
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
        try {
            assertOk(databaseDispatch(provider, "exec", handleId, "DROP TABLE IF EXISTS items"))
            assertOk(databaseDispatch(provider, "exec", handleId, "CREATE TABLE items(name TEXT, value INTEGER)"))
            val transaction = dispatch(
                provider = provider,
                module = "database",
                method = "transaction",
                argsJson = JSONArray()
                    .put(handleId)
                    .put(
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "run")
                                    .put("sql", "INSERT INTO items(name,value) VALUES(?,?)")
                                    .put("params", JSONArray().put("alpha").put(index)),
                            )
                            .put(
                                JSONObject()
                                    .put("type", "run")
                                    .put("sql", "INSERT INTO items(name,value) VALUES(?,?)")
                                    .put("params", JSONArray().put("beta").put(index + 1)),
                            )
                            .put(JSONObject().put("type", "get").put("sql", "SELECT COUNT(*) AS count FROM items")),
                    )
                    .toString(),
                permissions = listOf("database"),
            )
            assertTrue(transaction.toString(), transaction.ok)
            val results = JSONArray(transaction.resultJson.orEmpty())
            assertEquals(3, results.length())
            assertEquals(2, results.getJSONObject(2).getInt("count"))
        } finally {
            assertOk(
                dispatch(
                    provider = provider,
                    module = "database",
                    method = "close",
                    argsJson = JSONArray().put(handleId).toString(),
                    permissions = listOf("database"),
                ),
            )
        }
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
        assertNoActiveProviderResources(provider, "database-$index")
    }

    private fun databaseDispatch(
        provider: AndroidNodeBridgeCapabilityProvider,
        method: String,
        handleId: String,
        sql: String,
    ): NodeBridgeResponse =
        dispatch(
            provider = provider,
            module = "database",
            method = method,
            argsJson = JSONArray().put(handleId).put(sql).put(JSONArray()).toString(),
            permissions = listOf("database"),
        )

    private fun workManagerExecutionRoundTrip(provider: AndroidNodeBridgeCapabilityProvider, workDir: File, index: Int) {
        val scriptName = "wm-phase7-$index.cjs"
        val resultName = "wm-phase7-$index.done"
        File(workDir, scriptName).writeText(
            """
            const fs = require("node:fs");
            fs.writeFileSync("./$resultName", "done-$index");
            console.log("phase7.resource.work_manager.run=$index");
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val scheduled = dispatch(
            provider = provider,
            module = "work_manager",
            method = "scheduleOnce",
            argsJson = """[{"scriptPath":"$scriptName","delayMs":0,"timeoutMs":20000}]""",
            permissions = listOf("work_manager"),
            timeoutMs = 10_000L,
        )
        assertTrue(scheduled.toString(), scheduled.ok)
        val taskId = jsonString(scheduled.resultJson.orEmpty())
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.work_manager_registered_task_count"])
        assertTrue(
            "scheduled task $taskId did not execute",
            waitUntil(timeoutMs = 60_000L) {
                val list = dispatch(
                    provider = provider,
                    module = "work_manager",
                    method = "list",
                    argsJson = "[]",
                    permissions = listOf("work_manager"),
                    timeoutMs = 10_000L,
                )
                if (!list.ok) {
                    return@waitUntil false
                }
                val task = taskById(JSONArray(list.resultJson.orEmpty()), taskId)
                if (
                    task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_FAILED ||
                    task.optString("lastRunStatus") == NodeWorkManagerBridgeScheduler.STATUS_FAILED
                ) {
                    throw AssertionError("scheduled task failed: $task")
                }
                File(workDir, resultName).isFile &&
                        task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_SUCCEEDED
            },
        )
        val cancel = dispatch(
            provider = provider,
            module = "work_manager",
            method = "cancel",
            argsJson = JSONArray().put(taskId).toString(),
            permissions = listOf("work_manager"),
        )
        assertTrue(cancel.toString(), cancel.ok)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.work_manager_registered_task_count"])
        assertNoActiveProviderResources(provider, "work-manager-$index")
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        timeoutMs: Long = 5_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method", latch.await(timeoutMs, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun assertOk(response: NodeBridgeResponse) {
        assertTrue(response.toString(), response.ok)
    }

    private fun assertNoActiveProviderResources(provider: AndroidNodeBridgeCapabilityProvider, label: String) {
        val diagnostics = provider.nativeDiagnostics()
        assertEquals("$label active_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("$label active_types diagnostics=$diagnostics", "{}", diagnostics["embedded_script.bridge_resource.active_types"])
        assertEquals("$label close_error_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.close_error_count"])
    }

    private fun assertSnapshotContainsRequiredMetrics(snapshot: NodeResourceStressSnapshot) {
        val json = snapshot.toJson()
        listOf("rssKb", "javaHeapKb", "nativeHeapKb", "threadCount", "fdCount", "libuvHandleCount", "bridgeDiagnostics").forEach { key ->
            assertTrue("snapshot missing $key: $json", json.has(key))
        }
    }

    private fun parsePipeInts(stdout: String, marker: String, expectedCount: Int): List<Int> {
        val values = parsePipeValues(stdout, marker, expectedCount)
        return values.map { value ->
            value.toIntOrNull() ?: throw AssertionError("Non-integer $marker value '$value' in stdout: $stdout")
        }
    }

    private fun parsePipeValues(stdout: String, marker: String, expectedCount: Int): List<String> {
        val line = stdout
            .lineSequence()
            .firstOrNull { it.contains("$marker=") }
            ?: throw AssertionError("Missing $marker in stdout: $stdout")
        val values = line.substringAfter("$marker=").trim().split("|")
        assertEquals("Unexpected $marker count in line: $line", expectedCount, values.size)
        return values
    }

    private fun taskById(tasks: JSONArray, taskId: String): JSONObject {
        for (index in 0 until tasks.length()) {
            val task = tasks.getJSONObject(index)
            if (task.getString("id") == taskId) {
                return task
            }
        }
        throw AssertionError("Task $taskId not found in $tasks")
    }

    private fun jsonString(json: String): String =
        JSONTokener(json).nextValue().toString()

    private fun writeProjectPermissions(workDir: File, vararg permissions: String) {
        val permissionJson = permissions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": $permissionJson
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
    }

    private fun writePlugin(workDir: File, id: String, source: String) {
        val pluginDir = File(workDir, "plugins/$id").apply {
            check(mkdirs()) { "Failed to create plugin directory: $absolutePath" }
        }
        File(pluginDir, "package.json").writeText(
            """
            {
              "name": "$id",
              "version": "1.0.0",
              "main": "index.cjs",
              "autojs6": {
                "plugin": {
                  "id": "$id",
                  "permissions": []
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(pluginDir, "index.cjs").writeText(source, StandardCharsets.UTF_8)
    }

    private fun freshWorkDir(prefix: String): File =
        File(context.cacheDir, "$prefix-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work directory: $absolutePath" }
        }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) {
                return true
            }
            android.os.SystemClock.sleep(250L)
        }
        return predicate()
    }

    private fun providerStressIterations(): Int =
        (arguments.getString(ARG_PROVIDER_ITERATIONS)?.toIntOrNull() ?: DEFAULT_PROVIDER_ITERATIONS).coerceIn(1, MAX_PROVIDER_ITERATIONS)

    private fun workManagerExecutionIterations(): Int =
        (arguments.getString(ARG_WORK_MANAGER_ITERATIONS)?.toIntOrNull() ?: DEFAULT_WORK_MANAGER_ITERATIONS).coerceIn(1, MAX_WORK_MANAGER_ITERATIONS)

    private fun pluginStressIterations(): Int =
        (arguments.getString(ARG_PLUGIN_ITERATIONS)?.toIntOrNull() ?: DEFAULT_PLUGIN_ITERATIONS).coerceIn(1, MAX_PLUGIN_ITERATIONS)

    private companion object {
        private const val ARG_PROVIDER_ITERATIONS = "autojs.nodejs.phase7Resource.providerIterations"
        private const val ARG_WORK_MANAGER_ITERATIONS = "autojs.nodejs.phase7Resource.workManagerIterations"
        private const val ARG_PLUGIN_ITERATIONS = "autojs.nodejs.phase7Resource.pluginIterations"
        private const val DEFAULT_PROVIDER_ITERATIONS = 3
        private const val DEFAULT_WORK_MANAGER_ITERATIONS = 1
        private const val DEFAULT_PLUGIN_ITERATIONS = 8
        private const val MAX_PROVIDER_ITERATIONS = 12
        private const val MAX_WORK_MANAGER_ITERATIONS = 3
        private const val MAX_PLUGIN_ITERATIONS = 50
    }
}
