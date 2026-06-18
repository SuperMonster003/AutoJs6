package org.autojs.autojs.engine

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeWorkManagerBridgeInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private val ownerKeys = mutableSetOf<String>()

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        ownerKeys.clear()
    }

    @After
    fun settleAfter() {
        ownerKeys.forEach { ownerKey ->
            NodeWorkManagerBridgeScheduler.cancelAllForOwner(context, ownerKey)
        }
        ownerKeys.clear()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinWorkManagerBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("work_manager", "scheduleOnce"))
        assertTrue(NodeBridgeModules.supports("work_manager", "schedulePeriodic"))
        assertTrue(NodeBridgeModules.supports("work_manager", "cancel"))
        assertTrue(NodeBridgeModules.supports("work_manager", "list"))
        assertFalse(NodeBridgeModules.supports("work_manager", "registerReceiver"))
        assertFalse(NodeBridgeModules.supports("tasks", "createTimedTask"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("work_manager", request.module)
            assertTrue("work_manager" in request.permissions)
            when (request.method) {
                "scheduleOnce" -> {
                    val payload = JSONArray(request.argsJson).getJSONObject(0)
                    assertEquals("embedded", payload.getString("backend"))
                    assertEquals("main.cjs", payload.getString("scriptPath"))
                    assertEquals("scriptPath", payload.getString("entryKind"))
                    assertEquals(1_000L, payload.getLong("delayMs"))
                    assertEquals(4_321L, payload.getLong("timeoutMs"))
                    assertTrue(payload.getJSONObject("constraints").getBoolean("network"))
                    NodeBridgeResponse.success(request.id, NodeBridgeJsonValue.string("fake-once"))
                }
                "schedulePeriodic" -> {
                    val payload = JSONArray(request.argsJson).getJSONObject(0)
                    assertEquals("projectEntry", payload.getString("entryKind"))
                    assertEquals("main.cjs", payload.getString("entry"))
                    assertFalse(payload.has("scriptPath"))
                    assertEquals(900_000L, payload.getLong("intervalMs"))
                    assertTrue(payload.getBoolean("packaged"))
                    NodeBridgeResponse.success(request.id, NodeBridgeJsonValue.string("fake-periodic"))
                }
                "cancel" -> NodeBridgeResponse.success(request.id, "null")
                "list" -> NodeBridgeResponse.success(request.id, JSONArray().toString())
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "work-manager-once-1",
                module = "work_manager",
                method = "scheduleOnce",
                argsJson = """[{"scriptPath":"main.cjs","entryKind":"scriptPath","backend":"embedded","delayMs":1000,"timeoutMs":4321,"constraints":{"network":true}}]""",
                timeoutMs = 2_000L,
                permissions = listOf("work_manager"),
            ),
        ) { response -> responses += response }
        provider.dispatch(
            NodeBridgeRequest(
                id = "work-manager-periodic-1",
                module = "work_manager",
                method = "schedulePeriodic",
                argsJson = """[{"entry":"main.cjs","entryKind":"projectEntry","backend":"embedded","packaged":true,"intervalMs":900000,"constraints":{"charging":true}}]""",
                timeoutMs = 2_000L,
                permissions = listOf("work_manager"),
            ),
        ) { response -> responses += response }

        assertEquals(2, provider.requests.size)
        assertTrue(responses.all { it.ok })
        assertEquals("fake-once", jsonString(responses[0].resultJson.orEmpty()))
        assertEquals("fake-periodic", jsonString(responses[1].resultJson.orEmpty()))
    }

    @Test
    fun jsWorkManagerModuleUsesFakeTransportAndSerializesPackagedMetadata() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node work_manager bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-work-manager-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager bridge working directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        File(workDir, "node_modules/work_manager").apply {
            check(mkdirs()) { "Failed to create work_manager shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              const tasks = new Map();
              let sequence = 0;
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              function respond(request, ok, result, error) {
                setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok,
                  result,
                  error
                })));
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  if (request.module !== "work_manager") {
                    respond(request, false, null, {
                      code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                      message: "unexpected module"
                    });
                    return;
                  }
                  if (request.method === "scheduleOnce" || request.method === "schedulePeriodic") {
                    sequence += 1;
                    const descriptor = request.args[0];
                    const id = "fake-work-" + sequence;
                    tasks.set(id, Object.assign({
                      id,
                      type: request.method === "scheduleOnce" ? "once" : "periodic",
                      status: "registered",
                      scheduler: "metadata",
                      packageName: descriptor.packaged ? "org.autojs.autojs6.packaged" : "org.autojs.autojs6",
                      createdAtEpochMs: sequence
                    }, descriptor));
                    respond(request, true, id);
                    return;
                  }
                  if (request.method === "cancel") {
                    tasks.delete(request.args[0]);
                    respond(request, true, null);
                    return;
                  }
                  if (request.method === "list") {
                    respond(request, true, Array.from(tasks.values()));
                    return;
                  }
                  respond(request, false, null, {
                    code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                    message: "unexpected method"
                  });
                }
              });

              const work = require("work_manager");
              const resolved = require.resolve("work_manager");
              const onceId = await work.scheduleOnce({
                scriptPath: "./main.cjs",
                delayMs: 1234,
                taskTimeoutMs: 4321,
                timeoutMs: 777,
                constraints: { network: true }
              });
              const periodicId = await work.schedulePeriodic({
                entry: "main.cjs",
                packaged: true,
                intervalMs: work.policy.minPeriodicIntervalMs,
                constraints: { charging: true }
              });
              const beforeCancel = await work.list();
              await work.cancel(onceId);
              const afterCancel = await work.list();

              const beforeInvalid = requests.length;
              let pathEscapeCode = "";
              try {
                await work.scheduleOnce({ scriptPath: "../escape.cjs" });
              } catch (error) {
                pathEscapeCode = codeOf(error);
              }
              let intervalCode = "";
              try {
                await work.schedulePeriodic({ scriptPath: "main.cjs", intervalMs: 1000 });
              } catch (error) {
                intervalCode = codeOf(error);
              }
              let backendCode = "";
              try {
                await work.scheduleOnce({ scriptPath: "main.cjs", backend: "native" });
              } catch (error) {
                backendCode = codeOf(error);
              }
              let tasksCode = "";
              try {
                require("tasks");
              } catch (error) {
                tasksCode = codeOf(error);
              }

              console.log("work.resolve=" + resolved);
              console.log("work.shadow=" + (work.shadow === true));
              console.log("work.frozen=" + Object.isFrozen(work));
              console.log("work.methods=" + [typeof work.scheduleOnce, typeof work.schedulePeriodic, typeof work.cancel, typeof work.list].join("|"));
              console.log("work.policy=" + [work.policy.maxTasksPerProvider, work.policy.minPeriodicIntervalMs, work.policy.maxPeriodicIntervalMs].join("|"));
              console.log("work.ids=" + onceId + "|" + periodicId);
              console.log("work.before=" + beforeCancel.map((task) => [task.id, task.type, task.scriptPath || "", task.entry || "", task.entryKind, task.packaged, task.constraints.network, task.constraints.charging].join(":")).join("|"));
              console.log("work.request_entry=" + [requests[0].args[0].scriptPath || "", requests[0].args[0].entry || "", requests[1].args[0].scriptPath || "", requests[1].args[0].entry || ""].join("|"));
              console.log("work.after=" + afterCancel.map((task) => task.id).join("|"));
              console.log("work.requests=" + requests.map((request) => request.method).join("|"));
              console.log("work.permissions=" + requests.slice(0, 3).every((request) => request.permissions.indexOf("work_manager") >= 0));
              console.log("work.timeout=" + requests[0].timeoutMs);
              console.log("work.invalid_no_request=" + (requests.length === beforeInvalid));
              console.log("work.tasks=" + tasksCode);
              console.log("work.codes=" + [pathEscapeCode, intervalCode, backendCode].join("|"));
              console.log("work.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-work-manager-fake-source.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("work_manager fake bridge", result)
        assertTrue(result.stdout.contains("work.resolve=work_manager"))
        assertTrue(result.stdout.contains("work.shadow=false"))
        assertTrue(result.stdout.contains("work.frozen=true"))
        assertTrue(result.stdout.contains("work.methods=function|function|function|function"))
        assertTrue(result.stdout.contains("work.policy=64|900000|2592000000"))
        assertTrue(result.stdout.contains("work.ids=fake-work-1|fake-work-2"))
        assertTrue(result.stdout.contains("fake-work-1:once:main.cjs::scriptPath:false:true:false"))
        assertTrue(result.stdout.contains("fake-work-2:periodic::main.cjs:projectEntry:true:false:true"))
        assertTrue(result.stdout.contains("work.request_entry=main.cjs|||main.cjs"))
        assertTrue(result.stdout.contains("work.after=fake-work-2"))
        assertTrue(result.stdout.contains("work.permissions=true"))
        assertTrue(result.stdout.contains("work.timeout=777"))
        assertTrue(result.stdout.contains("work.invalid_no_request=true"))
        assertTrue(result.stdout.contains("work.tasks=MODULE_NOT_FOUND"))
        assertTrue(result.stdout.contains("work.codes=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("work.pending=0"))
    }

    @Test
    fun androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy() {
        val workDir = File(context.cacheDir, "node-bridge-work-manager-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager provider directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        val provider = androidProvider("work-manager-provider-test", workDir)

        val once = dispatch(provider, "scheduleOnce", """[{"scriptPath":"main.cjs","delayMs":60000,"constraints":{"network":true}}]""")
        assertTrue(once.ok)
        val onceId = jsonString(once.resultJson.orEmpty())
        assertTrue(onceId.startsWith("node-work-"))

        val periodic = dispatch(provider, "schedulePeriodic", """[{"entry":"main.cjs","packaged":true,"intervalMs":900000,"constraints":{"charging":true}}]""")
        assertTrue(periodic.ok)
        val periodicId = jsonString(periodic.resultJson.orEmpty())
        assertTrue(periodicId.startsWith("node-work-"))

        val list = dispatch(provider, "list", "[]")
        assertTrue(list.ok)
        val tasks = JSONArray(list.resultJson.orEmpty())
        assertEquals(2, tasks.length())
        assertEquals(setOf(onceId, periodicId), (0 until tasks.length()).map { tasks.getJSONObject(it).getString("id") }.toSet())
        assertTrue((0 until tasks.length()).map { tasks.getJSONObject(it).getString("scheduler") }.all { it == NodeWorkManagerBridgeScheduler.SCHEDULER_NAME })
        assertTrue((0 until tasks.length()).map { tasks.getJSONObject(it).getString("status") }.all { it == NodeWorkManagerBridgeScheduler.STATUS_ENQUEUED })
        assertTrue((0 until tasks.length()).map { tasks.getJSONObject(it).getString("backend") }.all { it == "embedded" })
        val onceTask = taskById(tasks, onceId)
        assertEquals("scriptPath", onceTask.getString("entryKind"))
        assertEquals("main.cjs", onceTask.getString("scriptPath"))
        assertFalse(onceTask.has("entry"))
        val periodicTask = taskById(tasks, periodicId)
        assertEquals("projectEntry", periodicTask.getString("entryKind"))
        assertEquals("main.cjs", periodicTask.getString("entry"))
        assertFalse(periodicTask.has("scriptPath"))
        assertTrue(periodicTask.getBoolean("packaged"))

        val deniedPath = dispatch(provider, "scheduleOnce", """[{"scriptPath":"../escape.cjs"}]""")
        assertFalse(deniedPath.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedPath.error?.code)
        assertEquals("work_manager", deniedPath.error?.module)

        val deniedAbsolute = dispatch(provider, "scheduleOnce", """[{"scriptPath":"/sdcard/escape.cjs"}]""")
        assertFalse(deniedAbsolute.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedAbsolute.error?.code)

        val deniedMissing = dispatch(provider, "scheduleOnce", """[{"entry":"missing.cjs"}]""")
        assertFalse(deniedMissing.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedMissing.error?.code)

        val packagedMissing = dispatch(provider, "scheduleOnce", """[{"entry":"packaged/main.cjs","packaged":true,"delayMs":60000}]""")
        assertTrue(packagedMissing.ok)
        val packagedMissingId = jsonString(packagedMissing.resultJson.orEmpty())
        val withPackagedMissing = JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty())
        val packagedMissingTask = taskById(withPackagedMissing, packagedMissingId)
        assertEquals("projectEntry", packagedMissingTask.getString("entryKind"))
        assertEquals("packaged/main.cjs", packagedMissingTask.getString("entry"))
        assertTrue(packagedMissingTask.getBoolean("packaged"))
        assertTrue(dispatch(provider, "cancel", JSONArray().put(packagedMissingId).toString()).ok)

        val deniedInterval = dispatch(provider, "schedulePeriodic", """[{"scriptPath":"main.cjs","intervalMs":1000}]""")
        assertFalse(deniedInterval.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedInterval.error?.code)

        val cancel = dispatch(provider, "cancel", JSONArray().put(onceId).toString())
        assertTrue(cancel.ok)
        val afterCancel = JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty())
        assertEquals(1, afterCancel.length())
        assertEquals(periodicId, afterCancel.getJSONObject(0).getString("id"))

        assertTrue(dispatch(provider, "cancel", JSONArray().put(periodicId).toString()).ok)
    }

    @Test
    fun androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node work_manager live provider one-shot execution")

        val workDir = File(context.cacheDir, "node-bridge-work-manager-live-packaged").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager live directory: $absolutePath" }
        }
        val resultFile = File(workDir, "wm-result.json")
        File(workDir, "main.cjs").writeText(
            """
            const fs = require("node:fs");
            fs.writeFileSync("./wm-result.json", JSON.stringify({
              ok: true,
              mode: "packaged-entry",
              cwd: process.cwd()
            }));
            console.log("work-manager-live-packaged-ran");
            """.trimIndent(),
        )
        val provider = androidProvider("work-manager-live-packaged-provider", workDir)

        val scheduled = dispatch(
            provider,
            "scheduleOnce",
            """[{"entry":"main.cjs","packaged":true,"delayMs":0,"timeoutMs":20000}]""",
            timeoutMs = 10_000L,
        )
        assertTrue(scheduled.toString(), scheduled.ok)
        val taskId = jsonString(scheduled.resultJson.orEmpty())

        waitUntil("scheduled packaged Node task should execute and persist result", timeoutMs = 60_000L) {
            val task = taskById(JSONArray(dispatch(provider, "list", "[]", timeoutMs = 10_000L).resultJson.orEmpty()), taskId)
            if (
                task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_FAILED ||
                task.optString("lastRunStatus") == NodeWorkManagerBridgeScheduler.STATUS_FAILED
            ) {
                throw AssertionError("scheduled packaged Node task failed: $task")
            }
            resultFile.isFile && task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_SUCCEEDED
        }

        val result = JSONObject(resultFile.readText())
        assertTrue(result.getBoolean("ok"))
        assertEquals("packaged-entry", result.getString("mode"))
        val task = taskById(JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty()), taskId)
        assertEquals("projectEntry", task.getString("entryKind"))
        assertTrue(task.getBoolean("packaged"))
        assertEquals(NodeWorkManagerBridgeScheduler.STATUS_SUCCEEDED, task.getString("lastRunStatus"))
        assertTrue(task.getJSONObject("lastRun").getString("stdout").contains("work-manager-live-packaged-ran"))

        assertTrue(dispatch(provider, "cancel", JSONArray().put(taskId).toString()).ok)
    }

    @Test
    fun androidWorkManagerLiveProviderCancelPreventsDelayedExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node work_manager live provider cancellation")

        val workDir = File(context.cacheDir, "node-bridge-work-manager-live-cancel").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager cancel directory: $absolutePath" }
        }
        val resultFile = File(workDir, "wm-cancel-result.txt")
        File(workDir, "main.cjs").writeText(
            """
            const fs = require("node:fs");
            fs.writeFileSync("./wm-cancel-result.txt", "should-not-run");
            """.trimIndent(),
        )
        val provider = androidProvider("work-manager-live-cancel-provider", workDir)

        val scheduled = dispatch(provider, "scheduleOnce", """[{"scriptPath":"main.cjs","delayMs":2000,"timeoutMs":20000}]""")
        assertTrue(scheduled.toString(), scheduled.ok)
        val taskId = jsonString(scheduled.resultJson.orEmpty())
        assertTrue(dispatch(provider, "cancel", JSONArray().put(taskId).toString()).ok)
        assertEquals(0, JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty()).length())

        TimeUnit.MILLISECONDS.sleep(3500)
        assertFalse("Cancelled WorkManager task should not write its output file.", resultFile.exists())
    }

    @Test
    fun androidWorkManagerProviderRequiresExplicitCapability() {
        val workDir = File(context.cacheDir, "node-bridge-work-manager-capability").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager capability directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        val provider = androidProvider("work-manager-capability-provider", workDir)

        val response = dispatch(
            provider,
            "scheduleOnce",
            """[{"scriptPath":"main.cjs","delayMs":60000}]""",
            permissions = emptyList(),
        )
        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, response.error?.code)
    }

    private fun androidProvider(id: String, workDir: File): AndroidNodeBridgeCapabilityProvider {
        val engineInfo = NodeBridgeEngineInfo.fromContext(
            context,
            id = id,
            sourceName = "main.cjs",
            cwd = workDir.absolutePath,
        )
        ownerKeys += NodeWorkManagerBridgeScheduler.ownerKey(engineInfo)
        return AndroidNodeBridgeCapabilityProvider(context, engineInfo)
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String,
        permissions: List<String> = listOf("work_manager"),
        timeoutMs: Long = 5_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "work-manager-provider-${System.nanoTime()}",
                module = "work_manager",
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for work_manager provider response", latch.await(timeoutMs, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing work_manager provider response" }
    }

    private fun jsonString(json: String): String {
        return JSONTokener(json).nextValue() as String
    }

    private fun taskById(tasks: JSONArray, id: String): JSONObject {
        for (index in 0 until tasks.length()) {
            val task = tasks.getJSONObject(index)
            if (task.optString("id") == id) {
                return task
            }
        }
        throw AssertionError("Missing work_manager task id: $id")
    }

    private fun waitUntil(label: String, timeoutMs: Long, intervalMs: Long = 500L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                if (condition()) {
                    return
                }
            } catch (error: Throwable) {
                lastError = error
            }
            TimeUnit.MILLISECONDS.sleep(intervalMs)
        }
        throw AssertionError(label, lastError)
    }
}

private object NodeBridgeJsonValue {
    fun string(value: String): String = JSONArray().put(value).toString().let { wrapped ->
        wrapped.substring(1, wrapped.length - 1)
    }
}
