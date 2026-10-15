package org.autojs.autojs.engine

import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.joda.time.LocalTime
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

    private val context = NodePluginTestHarness.targetContext
    private val ownerKeys = mutableSetOf<String>()

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
        ownerKeys.clear()
    }

    @After
    fun settleAfter() {
        ownerKeys.forEach { ownerKey ->
            NodeWorkManagerBridgeScheduler.cancelAllForOwner(context, ownerKey)
        }
        ownerKeys.clear()
        NodePluginTestHarness.settlePluginRuntime()
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node work_manager bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-work-manager-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager bridge working directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true };"""
        File(workDir, "node_modules/work_manager").apply {
            check(mkdirs()) { "Failed to create work_manager shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("work_manager"))
        val result = NodePluginTestHarness.runPluginScript(
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
              const compat = require("autojs6:compat");
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
              const compatId = await compat.tasks.addDisposableTask({
                path: "./main.cjs",
                delayMs: 2345,
                taskTimeoutMs: 5432,
                timeoutMs: 778,
                constraints: { idle: true }
              });
              const compatBefore = await compat.tasks.queryTasks({ timeoutMs: 779 });
              const compatRemoved = await compat.tasks.removeTask(compatId, { timeoutMs: 780 });
              const compatAfter = await compat.tasks.queryTasks();
              const compatTimed = await compat.tasks.queryTimedTasks({ timeoutMs: 781 });
              const compatTimedOne = await compat.tasks.getTimedTask(compatId, { timeoutMs: 782 });
              const compatTimedRemoved = await compat.tasks.removeTimedTask("fake-work-missing", { timeoutMs: 783 });
              let compatRawCode = "";
              try {
                await compat.tasks.removeTask({ id: compatId });
              } catch (error) {
                compatRawCode = codeOf(error);
              }
              let compatUnknownCode = "";
              try {
                await compat.tasks.addDisposableTask({ path: "main.cjs", delayMs: 10, repeat: true });
              } catch (error) {
                compatUnknownCode = codeOf(error);
              }
              let compatPastCode = "";
              try {
                await compat.tasks.addDisposableTask({ path: "main.cjs", date: new Date(Date.now() - 1000) });
              } catch (error) {
                compatPastCode = codeOf(error);
              }
              const beforeRejected = requests.length;
              let compatDailyCode = "";
              try {
                await compat.tasks.addDailyTask({ path: "main.cjs", time: "08:30" });
              } catch (error) {
                compatDailyCode = codeOf(error);
              }
              let compatWeeklyCode = "";
              try {
                await compat.tasks.addWeeklyTask({ path: "main.cjs", time: "08:30", daysOfWeek: [1] });
              } catch (error) {
                compatWeeklyCode = codeOf(error);
              }
              let compatUpdateCode = "";
              try {
                await compat.tasks.updateTask({ id: compatId });
              } catch (error) {
                compatUpdateCode = codeOf(error);
              }
              let compatAddIntentCode = "";
              try {
                await compat.tasks.addIntentTask({ path: "main.cjs", action: "org.autojs6.TEST" });
              } catch (error) {
                compatAddIntentCode = codeOf(error);
              }
              let compatQueryIntentCode = "";
              try {
                await compat.tasks.queryIntentTasks({ action: "org.autojs6.TEST" });
              } catch (error) {
                compatQueryIntentCode = codeOf(error);
              }
              let compatGetIntentCode = "";
              try {
                await compat.tasks.getIntentTask("intent-1");
              } catch (error) {
                compatGetIntentCode = codeOf(error);
              }
              let compatRemoveIntentCode = "";
              try {
                await compat.tasks.removeIntentTask("intent-1");
              } catch (error) {
                compatRemoveIntentCode = codeOf(error);
              }

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
              console.log("compat.tasks.methods=" + [typeof compat.tasks.addDisposableTask, typeof compat.tasks.addDailyTask, typeof compat.tasks.addWeeklyTask, typeof compat.tasks.addIntentTask, typeof compat.tasks.removeTask, typeof compat.tasks.queryTasks, typeof compat.tasks.queryIntentTasks, typeof compat.tasks.queryTimedTasks, typeof compat.tasks.getIntentTask, typeof compat.tasks.getTimedTask, typeof compat.tasks.removeIntentTask, typeof compat.tasks.removeTimedTask, typeof compat.tasks.updateTask].join("|"));
              console.log("compat.tasks.id=" + compatId);
              console.log("compat.tasks.before=" + compatBefore.map((task) => [task.id, task.type, task.scriptPath || "", task.constraints.idle].join(":")).join("|"));
              console.log("compat.tasks.removed=" + [compatRemoved, compatAfter.length].join("|"));
              console.log("compat.tasks.rhino_aliases=" + [compatTimed.length, compatTimedOne === null, compatTimedRemoved].join("|"));
              console.log("compat.tasks.codes=" + [compatRawCode, compatUnknownCode, compatPastCode].join("|"));
              console.log("compat.tasks.rejected=" + [compatDailyCode, compatWeeklyCode, compatUpdateCode, compatAddIntentCode, compatQueryIntentCode, compatGetIntentCode, compatRemoveIntentCode, requests.length === beforeRejected].join("|"));
              console.log("compat.tasks.global=" + ("tasks" in globalThis));
              console.log("compat.tasks.status=" + [compat.status().implementedTasks.indexOf("P12-16") >= 0, compat.status().implementedTasks.indexOf("P12-17") >= 0, compat.status().implementedTasks.indexOf("P12-18") >= 0, compat.status().moduleNames.indexOf("tasks") >= 0, compat.status().availableGlobalNames.indexOf("tasks") >= 0].join("|"));
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
            moduleSources = mapOf("node_modules/work_manager/index.js" to shadowPackageSource),
            timeoutMs = 10_000L,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("work_manager fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.resolve=autojs6:work_manager")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.shadow=false")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.frozen=true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.methods=function|function|function|function")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.policy=64|900000|2592000000")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.ids=fake-work-1|fake-work-2")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "fake-work-1:once:main.cjs::scriptPath:false:true:false")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "fake-work-2:periodic::main.cjs:projectEntry:true:false:true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.request_entry=main.cjs|||main.cjs")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.after=fake-work-2")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.methods=function|function|function|function|function|function|function|function|function|function|function|function|function")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.id=fake-work-3")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.before=fake-work-3:once:main.cjs:true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.removed=true|0")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.rhino_aliases=0|true|true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.codes=ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN|ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION|ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.rejected=ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.global=false")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "compat.tasks.status=true|true|true|true|false")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.permissions=true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.timeout=777")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.invalid_no_request=true")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.tasks=MODULE_NOT_FOUND")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.codes=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("work_manager fake bridge", result, "work.pending=0")
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
        assertEquals(
            NodeWorkManagerBridgeScheduler.SCHEDULER_NAME,
            onceTask.getJSONObject("androidPolicy").getString("scheduler"),
        )
        assertTrue(onceTask.getJSONObject("androidPolicy").has("quotaState"))
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node work_manager live provider one-shot execution")

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
        val lastRun = task.getJSONObject("lastRun")
        assertEquals(NodeExecutionModePolicy.MODE_SCHEDULED, lastRun.getString("executionMode"))
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER, lastRun.getString("launchSurface"))
        assertEquals("work_manager", lastRun.getString("trigger"))
        assertEquals(1, lastRun.getInt("attempt"))
        assertTrue(lastRun.getString("executionId").startsWith("node-work-run-$taskId-"))
        assertTrue(lastRun.getString("stdout").contains("work-manager-live-packaged-ran"))
        assertEquals(
            NodeExecutionModePolicy.MODE_SCHEDULED,
            lastRun.getJSONObject("nativeValues").getString("embedded_script.execution_mode"),
        )
        assertEquals(
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            lastRun.getJSONObject("nativeValues").getString("embedded_script.execution_mode_launch_surface"),
        )
        assertEquals("true", lastRun.getJSONObject("nativeValues").getString("embedded_script.execution_mode_supported"))
        assertEquals(1, task.getJSONArray("runHistory").length())
        assertEquals(lastRun.getString("executionId"), task.getJSONArray("runHistory").getJSONObject(0).getString("executionId"))

        assertTrue(dispatch(provider, "cancel", JSONArray().put(taskId).toString()).ok)
    }

    @Test
    fun androidWorkManagerScheduledRunnerExecutesEsmEntryAndPersistsHistory() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node work_manager scheduled runner ESM execution")

        val workDir = File(context.cacheDir, "node-bridge-work-manager-scheduled-esm").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager scheduled ESM directory: $absolutePath" }
        }
        val resultFile = File(workDir, "wm-esm-result.json")
        File(workDir, "main.mjs").writeText(
            """
            import { writeFileSync } from "node:fs";
            await Promise.resolve();
            writeFileSync("./wm-esm-result.json", JSON.stringify({
              ok: true,
              taskId: process.env.AUTOJS6_NODE_SCHEDULED_TASK_ID,
              type: process.env.AUTOJS6_NODE_SCHEDULED_TASK_TYPE,
              attempt: process.env.AUTOJS6_NODE_SCHEDULED_TASK_ATTEMPT,
              cwd: process.cwd()
            }));
            console.log("work-manager-scheduled-esm-ran");
            """.trimIndent(),
        )
        val provider = androidProvider("work-manager-scheduled-esm-provider", workDir)

        val scheduled = dispatch(
            provider,
            "scheduleOnce",
            """[{"scriptPath":"main.mjs","delayMs":0,"timeoutMs":20000}]""",
            timeoutMs = 10_000L,
        )
        assertTrue(scheduled.toString(), scheduled.ok)
        val taskId = jsonString(scheduled.resultJson.orEmpty())

        waitUntil("scheduled ESM Node task should execute and persist history", timeoutMs = 60_000L) {
            val task = taskById(JSONArray(dispatch(provider, "list", "[]", timeoutMs = 10_000L).resultJson.orEmpty()), taskId)
            if (
                task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_FAILED ||
                task.optString("lastRunStatus") == NodeWorkManagerBridgeScheduler.STATUS_FAILED
            ) {
                throw AssertionError("scheduled ESM Node task failed: $task")
            }
            resultFile.isFile && task.optString("status") == NodeWorkManagerBridgeScheduler.STATUS_SUCCEEDED
        }

        val result = JSONObject(resultFile.readText())
        assertTrue(result.getBoolean("ok"))
        assertEquals(taskId, result.getString("taskId"))
        assertEquals("once", result.getString("type"))
        assertEquals("1", result.getString("attempt"))
        val task = taskById(JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty()), taskId)
        val lastRun = task.getJSONObject("lastRun")
        assertEquals("main.mjs", lastRun.getString("sourceName"))
        assertEquals(NodeExecutionModePolicy.MODE_SCHEDULED, lastRun.getString("executionMode"))
        assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER, lastRun.getString("launchSurface"))
        assertTrue(lastRun.getString("stdout").contains("work-manager-scheduled-esm-ran"))
        assertEquals(1, task.getJSONArray("runHistory").length())
        assertEquals(
            NodeExecutionModePolicy.MODE_SCHEDULED,
            task.getJSONArray("runHistory").getJSONObject(0).getString("executionMode"),
        )

        assertTrue(dispatch(provider, "cancel", JSONArray().put(taskId).toString()).ok)
    }

    @Test
    fun androidWorkManagerLiveProviderCancelPreventsDelayedExecution() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node work_manager live provider cancellation")

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

    @Test
    fun intentTaskSnapshotsStayJsonOnlyWithoutReceiverHandles() {
        val first = IntentTask().apply {
            id = 101L
            scriptPath = "jobs/package-added.cjs"
            action = "org.autojs6.TEST_PACKAGE"
            category = "android.intent.category.DEFAULT"
            dataType = "text/plain"
            isLocal = true
        }
        val second = IntentTask().apply {
            id = 102L
            scriptPath = "jobs/boot.cjs"
            action = "android.intent.action.BOOT_COMPLETED"
            isLocal = false
        }

        val snapshot = NodeIntentTaskSnapshots.snapshot(first)
        assertEquals(NodeIntentTaskSnapshots.SCHEMA, snapshot.getString("schema"))
        assertEquals(101L, snapshot.getLong("id"))
        assertEquals("jobs/package-added.cjs", snapshot.getString("scriptPath"))
        assertEquals("org.autojs6.TEST_PACKAGE", snapshot.getString("action"))
        assertEquals("android.intent.category.DEFAULT", snapshot.getString("category"))
        assertEquals("text/plain", snapshot.getString("dataType"))
        assertTrue(snapshot.getBoolean("local"))
        assertEquals("app-local", snapshot.getString("scope"))
        assertEquals("rhino_intent_task_database", snapshot.getString("legacyOwner"))
        assertFalse(snapshot.getBoolean("nodeProviderOwned"))

        val rawPolicy = snapshot.getJSONObject("rawObjectPolicy")
        assertEquals("denied", rawPolicy.getString("rawIntentTask"))
        assertEquals("denied", rawPolicy.getString("rawIntentFilter"))
        assertEquals("denied", rawPolicy.getString("rawIntent"))
        assertEquals("denied", rawPolicy.getString("rawBroadcastReceiver"))
        assertEquals("denied", rawPolicy.getString("rawReceiverHandle"))
        assertEquals("denied", rawPolicy.getString("mutableRowHandle"))

        assertFalse(snapshot.has("intentFilter"))
        assertFalse(snapshot.has("receiver"))
        assertFalse(snapshot.has("broadcastReceiver"))
        assertFalse(snapshot.has("rawIntentTask"))
        assertFalse(snapshot.has("rawReceiverHandle"))

        val tasks = listOf(second, first)
        val queried = NodeIntentTaskSnapshots.querySnapshots(tasks, action = "org.autojs6.TEST_PACKAGE")
        assertEquals(1, queried.length())
        assertEquals(101L, queried.getJSONObject(0).getLong("id"))
        assertEquals(
            102L,
            checkNotNull(NodeIntentTaskSnapshots.getSnapshot(tasks, 102L)).getLong("id"),
        )
        assertEquals(null, NodeIntentTaskSnapshots.getSnapshot(tasks, 999L))
    }

    @Test
    fun timedTaskSnapshotsStayJsonOnlyWithoutSchedulerHandles() {
        val daily = TimedTask.dailyTask(
            LocalTime(9, 30),
            "jobs/daily.cjs",
            ExecutionConfig(delay = 123L, interval = 456L, loopTimes = 3),
        ).apply {
            id = 201L
            isScheduled = true
            endMode = TimedTask.END_MODE_COUNT
            endValue = 5L
            finishedTimes = 2
        }
        val weekly = TimedTask.weeklyTask(
            LocalTime(10, 15),
            TimedTask.FLAG_MONDAY.toLong() + TimedTask.FLAG_FRIDAY.toLong(),
            "jobs/weekly.cjs",
            ExecutionConfig(),
        ).apply {
            id = 202L
        }

        val snapshot = NodeTimedTaskSnapshots.snapshot(daily)
        assertEquals(NodeTimedTaskSnapshots.SCHEMA, snapshot.getString("schema"))
        assertEquals(201L, snapshot.getLong("id"))
        assertEquals("jobs/daily.cjs", snapshot.getString("scriptPath"))
        assertEquals(TimedTask.REPEAT_MODE_DAILY, snapshot.getInt("repeatMode"))
        assertEquals("daily", snapshot.getString("repeatModeName"))
        assertEquals(TimedTask.END_MODE_COUNT, snapshot.getInt("endMode"))
        assertEquals("count", snapshot.getString("endModeName"))
        assertEquals(123L, snapshot.getLong("delay"))
        assertEquals(456L, snapshot.getLong("interval"))
        assertEquals(3, snapshot.getInt("loopTimes"))
        assertEquals(5L, snapshot.getLong("endValue"))
        assertEquals(2, snapshot.getInt("finishedTimes"))
        assertTrue(snapshot.getBoolean("scheduled"))
        assertTrue(snapshot.getBoolean("daily"))
        assertFalse(snapshot.getBoolean("weekly"))
        assertEquals("rhino_timed_task_database", snapshot.getString("legacyOwner"))
        assertFalse(snapshot.getBoolean("nodeProviderOwned"))

        val rawPolicy = snapshot.getJSONObject("rawObjectPolicy")
        assertEquals("denied", rawPolicy.getString("rawTimedTask"))
        assertEquals("denied", rawPolicy.getString("rawExecutionConfig"))
        assertEquals("denied", rawPolicy.getString("rawPendingIntent"))
        assertEquals("denied", rawPolicy.getString("rawAlarmManager"))
        assertEquals("denied", rawPolicy.getString("rawJobScheduler"))
        assertEquals("denied", rawPolicy.getString("rawWorkRequest"))
        assertEquals("denied", rawPolicy.getString("databaseCursor"))
        assertEquals("denied", rawPolicy.getString("mutableRowHandle"))

        assertFalse(snapshot.has("timedTask"))
        assertFalse(snapshot.has("executionConfig"))
        assertFalse(snapshot.has("pendingIntent"))
        assertFalse(snapshot.has("alarmManager"))
        assertFalse(snapshot.has("jobScheduler"))
        assertFalse(snapshot.has("workRequest"))
        assertFalse(snapshot.has("databaseCursor"))
        assertFalse(snapshot.has("rawTimedTask"))

        val tasks = listOf(weekly, daily)
        val queried = NodeTimedTaskSnapshots.querySnapshots(tasks, repeatMode = TimedTask.REPEAT_MODE_DAILY)
        assertEquals(1, queried.length())
        assertEquals(201L, queried.getJSONObject(0).getLong("id"))
        val queriedByPath = NodeTimedTaskSnapshots.querySnapshots(tasks, scriptPath = "jobs/weekly.cjs")
        assertEquals(1, queriedByPath.length())
        assertEquals("weekly", queriedByPath.getJSONObject(0).getString("repeatModeName"))
        assertEquals(
            202L,
            checkNotNull(NodeTimedTaskSnapshots.getSnapshot(tasks, 202L)).getLong("id"),
        )
        assertEquals(null, NodeTimedTaskSnapshots.getSnapshot(tasks, 999L))
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
