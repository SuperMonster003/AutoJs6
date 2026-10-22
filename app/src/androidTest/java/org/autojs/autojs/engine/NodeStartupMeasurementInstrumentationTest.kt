package org.autojs.autojs.engine

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.timing.TimedTask
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Manual M23.8 measurements through the same launch routes used by users and schedulers. */
class NodeStartupMeasurementInstrumentationTest {
    private val context get() = NodePluginTestHarness.targetContext
    private var enabledBefore = true
    @org.junit.Before fun enableInstalledPluginForMeasurement() {
        enabledBefore = org.autojs.autojs.core.plugin.center.PluginEnableStore.isEnabled(context,
            "io.github.supermonster003.autojs6.plugin.nodejs", true)
        org.autojs.autojs.core.plugin.center.PluginEnableStore.setEnabled(context,
            "io.github.supermonster003.autojs6.plugin.nodejs", true)
    }
    @org.junit.After fun restorePluginSetting() {
        org.autojs.autojs.core.plugin.center.PluginEnableStore.setEnabled(context,
            "io.github.supermonster003.autojs6.plugin.nodejs", enabledBefore)
    }

    @Test fun recordsUiJavaScriptArchiveTypeScriptAndTimedTaskFirstOutput() {
        kotlinx.coroutines.runBlocking { NodeJsRuntimePluginHost.probe(context) }
        val runs = JSONArray()
        for ((mode, count) in listOf("javascript" to 5, "archive" to 5, "typescript" to 3, "timed_task" to 3)) {
            val root = File(context.cacheDir, "m23-startup-$mode-${UUID.randomUUID()}").apply { check(mkdirs()) }
            try {
                val extension = if (mode == "typescript") "cts" else "cjs"
                val entry = File(root, "main.$extension")
                File(root, "project.json").writeText("""{"name":"m23-startup","main":"main.$extension","type":"node","node":{"permissions":[]}}""")
                File(root, "package.json").writeText("""{"name":"m23-startup","private":true,"type":"commonjs"}""")
                entry.writeText("\"nodejs\";\n" + (if (mode == "typescript") "const message: string = 'm23-startup-$mode';\n" else "const message = 'm23-startup-$mode';\n") + "console.log(message);\n")
                if (mode == "archive") repeat(200) { File(root, "asset-$it.txt").writeText("data".repeat(1024)) }
                repeat(count) { index ->
                    val result = awaitExecution("m23-startup-$mode") {
                        if (mode == "timed_task") {
                            val task = TimedTask.countdownTask(1L, entry.path, ExecutionConfig(workingDirectory = root.path))
                            val snapshot = NodeTimedTaskSnapshots.snapshot(task)
                            assertEquals(entry.path, snapshot.getString("scriptPath"))
                            ScriptIntents.handleIntent(context, task.createIntent())
                        } else {
                            assertNotNull(Scripts.runInteractive(context, ScriptFile(entry.path)))
                        }
                    }
                    val timings = result.nativeValues.filterKeys { it.startsWith("timing.") }
                    assertTrue("first stdout timing missing: $timings", (timings["timing.host.launch_to_first_stdout.ms"]?.toLong() ?: -1L) >= 0)
                    runs.put(JSONObject().put("mode", mode).put("index", index).put("timings", JSONObject(timings)))
                }
            } finally { root.deleteRecursively() }
        }
        report("M23_HOST_STARTUP", runs)
    }

    @Test fun recordsRealWorkManagerFirstOutput() {
        kotlinx.coroutines.runBlocking { NodeJsRuntimePluginHost.probe(context) }
        val root = File(context.cacheDir, "m23-startup-work-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val owner = "${context.packageName}:${root.canonicalPath}"
        val runs = JSONArray()
        try {
            File(root, "main.cjs").writeText("console.log('m23-work-first-output');\n")
            repeat(3) { index ->
                val id = "m23-startup-${UUID.randomUUID()}"
                val task = JSONObject().put("id", id).put("type", "once")
                    .put("packageName", context.packageName).put("workingDirectory", root.path)
                    .put("entryKind", "projectEntry").put("entry", "main.cjs")
                    .put("delayMs", 0).put("timeoutMs", 20000).put("createdAtEpochMs", System.currentTimeMillis())
                val accepted = NodeWorkManagerBridgeScheduler.schedule(context, task)
                assertNull(accepted.errorMessage)
                val deadline = SystemClock.elapsedRealtime() + 60000
                var lastRun: JSONObject? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    val tasks = SettingsJson.toOrgJson(NodeWorkManagerBridgeScheduler.list(context, owner)) as JSONArray
                    val row = (0 until tasks.length()).map { tasks.getJSONObject(it) }.firstOrNull { it.optString("id") == id }
                    if (row?.optString("status") == "failed") fail(row.toString())
                    if (row?.optString("status") == "succeeded") { lastRun = row.getJSONObject("lastRun"); break }
                    Thread.sleep(50)
                }
                assertNotNull("WorkManager did not finish", lastRun)
                val values = requireNotNull(lastRun).getJSONObject("nativeValues")
                assertTrue(values.toString(), values.getString("timing.host.launch_to_first_stdout.ms").toLong() >= 0)
                runs.put(JSONObject().put("mode", "work_manager").put("index", index).put("timings", values))
            }
        } finally {
            NodeWorkManagerBridgeScheduler.cancelAllForOwner(context, owner)
            root.deleteRecursively()
        }
        report("M23_WORK_STARTUP", runs)
    }

    private fun awaitExecution(marker: String, launch: () -> Unit): NodePluginScriptResult {
        val done = CountDownLatch(1)
        val result = AtomicReference<NodePluginScriptResult>()
        val error = AtomicReference<Throwable>()
        val service = AutoJs.instance.scriptEngineService
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                val scriptResult = (execution.engine as? NodePluginJavaScriptEngine)?.lastPluginScriptResult ?: return
                if (!scriptResult.stdout.contains(marker)) return
                result.set(scriptResult)
                done.countDown()
            }
            override fun onException(execution: ScriptExecution, failure: Throwable) {
                error.set(failure)
                done.countDown()
            }
        }
        service.registerGlobalScriptExecutionListener(listener)
        try {
            launch()
            assertTrue("Node launch timed out", done.await(120, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("Node launch failed", it) }
            return requireNotNull(result.get()).also { NodePluginTestHarness.assertSucceeded(marker, it) }
        } finally { service.unregisterGlobalScriptExecutionListener(listener) }
    }

    private fun report(label: String, runs: JSONArray) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "\n$label=$runs\n") })
    }
}
