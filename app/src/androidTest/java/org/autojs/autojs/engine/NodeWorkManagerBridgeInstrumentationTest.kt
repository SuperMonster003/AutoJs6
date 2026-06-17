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

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
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
                    assertEquals(1_000L, payload.getLong("delayMs"))
                    assertTrue(payload.getJSONObject("constraints").getBoolean("network"))
                    NodeBridgeResponse.success(request.id, NodeBridgeJsonValue.string("fake-once"))
                }
                "schedulePeriodic" -> {
                    val payload = JSONArray(request.argsJson).getJSONObject(0)
                    assertEquals("projectEntry", payload.getString("entryKind"))
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
                argsJson = """[{"scriptPath":"main.cjs","backend":"embedded","delayMs":1000,"constraints":{"network":true}}]""",
                timeoutMs = 2_000L,
                permissions = listOf("work_manager"),
            ),
        ) { response -> responses += response }
        provider.dispatch(
            NodeBridgeRequest(
                id = "work-manager-periodic-1",
                module = "work_manager",
                method = "schedulePeriodic",
                argsJson = """[{"entry":"main.cjs","backend":"embedded","packaged":true,"intervalMs":900000,"constraints":{"charging":true}}]""",
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
              console.log("work.before=" + beforeCancel.map((task) => [task.id, task.type, task.scriptPath, task.entryKind, task.packaged, task.constraints.network, task.constraints.charging].join(":")).join("|"));
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
        assertTrue(result.stdout.contains("fake-work-1:once:main.cjs:scriptPath:false:true:false"))
        assertTrue(result.stdout.contains("fake-work-2:periodic:main.cjs:projectEntry:true:false:true"))
        assertTrue(result.stdout.contains("work.after=fake-work-2"))
        assertTrue(result.stdout.contains("work.permissions=true"))
        assertTrue(result.stdout.contains("work.timeout=777"))
        assertTrue(result.stdout.contains("work.invalid_no_request=true"))
        assertTrue(result.stdout.contains("work.tasks=MODULE_NOT_FOUND"))
        assertTrue(result.stdout.contains("work.codes=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("work.pending=0"))
    }

    @Test
    fun androidWorkManagerProviderStoresMetadataAndEnforcesPathPolicy() {
        val workDir = File(context.cacheDir, "node-bridge-work-manager-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work_manager provider directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "work-manager-provider-test",
                sourceName = "main.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val once = dispatch(provider, "scheduleOnce", """[{"scriptPath":"main.cjs","delayMs":1000,"constraints":{"network":true}}]""")
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
        assertTrue((0 until tasks.length()).map { tasks.getJSONObject(it).getString("scheduler") }.all { it == "metadata" })
        assertTrue((0 until tasks.length()).map { tasks.getJSONObject(it).getString("backend") }.all { it == "embedded" })

        val deniedPath = dispatch(provider, "scheduleOnce", """[{"scriptPath":"../escape.cjs"}]""")
        assertFalse(deniedPath.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedPath.error?.code)
        assertEquals("work_manager", deniedPath.error?.module)

        val deniedInterval = dispatch(provider, "schedulePeriodic", """[{"scriptPath":"main.cjs","intervalMs":1000}]""")
        assertFalse(deniedInterval.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedInterval.error?.code)

        val cancel = dispatch(provider, "cancel", JSONArray().put(onceId).toString())
        assertTrue(cancel.ok)
        val afterCancel = JSONArray(dispatch(provider, "list", "[]").resultJson.orEmpty())
        assertEquals(1, afterCancel.length())
        assertEquals(periodicId, afterCancel.getJSONObject(0).getString("id"))
    }

    private fun dispatch(provider: NodeBridgeCapabilityProvider, method: String, argsJson: String): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "work-manager-provider-${System.nanoTime()}",
                module = "work_manager",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("work_manager"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for work_manager provider response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing work_manager provider response" }
    }

    private fun jsonString(json: String): String {
        return JSONTokener(json).nextValue() as String
    }
}

private object NodeBridgeJsonValue {
    fun string(value: String): String = JSONArray().put(value).toString().let { wrapped ->
        wrapped.substring(1, wrapped.length - 1)
    }
}
