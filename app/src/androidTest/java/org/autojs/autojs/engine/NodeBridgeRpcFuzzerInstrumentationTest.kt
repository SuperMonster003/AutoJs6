package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBridgeRpcFuzzerInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun liveBridgeMalformedRequestFuzzerReturnsStableErrorsAndCleansFiles() {
        val budget = NodeFuzzerBudget.current()
        val baseSeed = NodeFuzzerSupport.baseSeed(FUZZER_NAME, DEFAULT_BASE_SEED)
        val dispatched = mutableListOf<NodeBridgeRequest>()
        val provider = FakeNodeBridgeCapabilityProvider { request ->
            dispatched += request
            NodeBridgeResponse.success(request.id, "\"ok\"")
        }
        val fixture = newLiveBridgeSession("node-bridge-rpc-fuzzer-session", provider)
        val requestDir = File(fixture.config.getString("requestDir"))
        val responseDir = File(fixture.config.getString("responseDir"))
        val malformedCases = budget.selectCases(liveMalformedCases(budget), quick = 8, normal = NORMAL_LIVE_MALFORMED_CASES)
        val validCases = budget.selectCases(liveValidCases(budget), quick = 1, normal = NORMAL_LIVE_VALID_CASES)
        val responseTimeoutMs = budget.timeoutMs(2_000L, 3_000L, 5_000L)
        try {
            (malformedCases + validCases).forEach { request ->
                File(requestDir, "${request.fileId}.json").writeText(request.json)
            }
            fixture.start()
            malformedCases.forEach { request ->
                val response = NodeBridgeResponse.fromJson(waitForBridgeResponse(responseDir, request.fileId, responseTimeoutMs).readText())
                assertEquals(request.fileId, response.id)
                assertFalse(response.ok)
                assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, response.error?.code)
                assertTrue(response.error?.message.orEmpty().contains("AutoJs6 bridge request is malformed"))
                request.expectedModule?.let { assertEquals(it, response.error?.module) }
                request.expectedMethod?.let { assertEquals(it, response.error?.method) }
            }
            validCases.forEach { request ->
                val response = NodeBridgeResponse.fromJson(waitForBridgeResponse(responseDir, request.fileId, responseTimeoutMs).readText())
                assertEquals(request.fileId, response.id)
                assertTrue(response.toJson(), response.ok)
                assertEquals("ok", response.resultJson?.trim('"'))
            }

            assertEquals(validCases.size, dispatched.size)
            assertEquals(validCases.size, provider.requests.size)
            assertTrue(
                "Live bridge request directory should be drained",
                requestDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty().isEmpty(),
            )
        } catch (throwable: Throwable) {
            NodeFuzzerSupport.reportFailure(
                fuzzer = FUZZER_NAME,
                budget = budget,
                baseSeed = baseSeed,
                caseCount = malformedCases.size + validCases.size,
                replayClass = TEST_CLASS,
                replayMethod = "liveBridgeMalformedRequestFuzzerReturnsStableErrorsAndCleansFiles",
                throwable = throwable,
            )
            throw throwable
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun kotlinBridgeDispatchFuzzerReturnsStableProviderErrors() {
        val budget = NodeFuzzerBudget.current()
        val baseSeed = NodeFuzzerSupport.baseSeed(FUZZER_NAME, DEFAULT_BASE_SEED)
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val requests = budget.selectCases(listOf(
            NodeBridgeRequest("unknown-module", "missing", "method", "[]", 1000L),
            NodeBridgeRequest("unknown-method", "app", "execScript", "[]", 1000L),
            NodeBridgeRequest("wrong-arg-type", "app", "startActivity", "[42]", 1000L),
            NodeBridgeRequest("too-many-args", "app", "startActivity", "[{},1,2,3]", 1000L),
            NodeBridgeRequest("extended-unknown-module", "missingExtended", "method", "[]", 1000L),
            NodeBridgeRequest("extended-unknown-method", "dialogs", "missingExtended", "[]", 1000L),
        ), quick = 2, normal = 4)
        val latch = CountDownLatch(requests.size)
        val responses = mutableListOf<NodeBridgeResponse>()

        requests.forEach { request ->
            provider.dispatch(request) { response ->
                synchronized(responses) {
                    responses += response
                }
                latch.countDown()
            }
        }

        try {
            assertTrue(
                "Timed out waiting for bridge dispatch fuzzer responses",
                latch.await(budget.timeoutMs(1_500L, 2_000L, 4_000L), TimeUnit.MILLISECONDS),
            )
            val byId = synchronized(responses) { responses.associateBy { it.id } }
            requests.forEach { request ->
                assertStableProviderError(byId.getValue(request.id), request.module, request.method)
            }
            assertTrue(provider.nativeDiagnostics()["embedded_script.bridge_resource_limit_rejection_count"] == "0")
        } catch (throwable: Throwable) {
            NodeFuzzerSupport.reportFailure(
                fuzzer = FUZZER_NAME,
                budget = budget,
                baseSeed = baseSeed,
                caseCount = requests.size,
                replayClass = TEST_CLASS,
                replayMethod = "kotlinBridgeDispatchFuzzerReturnsStableProviderErrors",
                throwable = throwable,
            )
            throw throwable
        } finally {
            provider.destroy()
        }
    }

    @Test
    fun jsBridgeRpcFuzzerIgnoresMalformedResponsesAndCleansPendingMap() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge RPC fuzzer")

        val budget = NodeFuzzerBudget.current()
        val baseSeed = NodeFuzzerSupport.baseSeed(FUZZER_NAME, DEFAULT_BASE_SEED)
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                """
                "nodejs";
                (async function main() {
              const bridge = require("autojs6:bridge");
              function fail(message) {
                throw new Error(message);
              }
              function sleep(ms) {
                return new Promise((resolve) => setTimeout(resolve, ms));
              }
              async function waitFor(predicate, label) {
                for (let i = 0; i < 60; i += 1) {
                  if (predicate()) return;
                  await sleep(5);
                }
                fail("timeout waiting for " + label);
              }
              function codeOf(error) {
                return error && (error.autojs6Code || error.code || error.name) || "";
              }
              async function collect(promise, codes) {
                try {
                  await promise;
                  codes.push("OK");
                } catch (error) {
                  codes.push(codeOf(error));
                }
              }

              let ignored = 0;
              let thrown = 0;
              const invalidMessages = [
                "",
                "{",
                "[]",
                "{}",
                JSON.stringify({ id: "unknown", ok: true, result: null }),
                JSON.stringify({ id: null, ok: false, error: { code: "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST" } }),
                { id: "", ok: true, result: null },
                42,
                null
              ];
              for (const message of invalidMessages) {
                try {
                  if (bridge.__test.receiveMessage(message) === false) {
                    ignored += 1;
                  }
                } catch (_) {
                  thrown += 1;
                }
              }
              if (thrown !== 0) fail("malformed response threw");
              if (bridge.__test.pendingCount() !== 0) fail("unexpected pending after invalid responses");

              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  if (request.module === "hold") {
                    return;
                  }
                  const code = request.module === "missing" || request.method === "missing"
                    ? "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"
                    : "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST";
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: false,
                    error: {
                      name: "Error",
                      message: "fuzz rejected",
                      code,
                      module: request.module,
                      method: request.method
                    }
                  })));
                }
              });

              const codes = [];
              await collect(bridge.callAutoJs("missing", "method", [], { timeoutMs: 1000 }), codes);
              await collect(bridge.callAutoJs("toast", "showToast", ["x".repeat(8192), { data: String.fromCharCode(0, 1, 255) }], { timeoutMs: 1000 }), codes);
              await collect(bridge.callAutoJs("app", "startActivity", [{}, 1, 2, 3, { extra: true }], { timeoutMs: 1000 }), codes);
              await collect(bridge.callAutoJs(null, "method", [], { timeoutMs: 1000 }), codes);
              await collect(bridge.callAutoJs("module", "", [], { timeoutMs: 1000 }), codes);

              const malformedPromise = bridge.callAutoJs("hold", "malformed", [], { timeoutMs: 1000 }).catch(codeOf);
              await waitFor(() => requests.some((request) => request.method === "malformed"), "held malformed response request");
              const malformedRequest = requests.find((request) => request.method === "malformed");
              const falseBeforeCleanup = [
                bridge.__test.receiveMessage("{"),
                bridge.__test.receiveMessage(JSON.stringify({ id: "unknown", ok: true, result: null })),
                bridge.__test.receiveMessage(JSON.stringify({ ok: false, error: { code: "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST" } }))
              ].filter((value) => value === false).length;
              if (bridge.__test.pendingCount() !== 1) fail("held malformed response was not pending");
              bridge.__test.receiveMessage(JSON.stringify({ id: malformedRequest.id, ok: false, error: "bad" }));
              codes.push(await malformedPromise);
              if (bridge.__test.pendingCount() !== 0) fail("malformed response did not clean pending");

              const destroyPromise = bridge.callAutoJs("hold", "destroy", [], { timeoutMs: 1000 }).catch(codeOf);
              await waitFor(() => requests.some((request) => request.method === "destroy"), "held destroy request");
              bridge.__test.destroy("fuzz destroy");
              codes.push(await destroyPromise);
              if (bridge.__test.pendingCount() !== 0) fail("destroy did not clean pending");

              console.log("bridge.rpc.fuzzer.seed=$baseSeed");
              console.log("bridge.rpc.fuzzer.budget=${budget.id}");
              console.log("bridge.rpc.fuzzer.invalid=" + ignored + "/" + thrown + "/" + falseBeforeCleanup);
              console.log("bridge.rpc.fuzzer.codes=" + codes.join("|"));
              console.log("bridge.rpc.fuzzer.requests=" + requests.length);
              console.log("bridge.rpc.fuzzer.pending=" + bridge.__test.pendingCount());
            })();
                """.trimIndent(),
                "<embedded-autojs-bridge-rpc-fuzzer-source.js>",
                timeoutMs = budget.timeoutMs(7_000L, 10_000L, 15_000L),
            )

            if (!result.succeeded || result.timedOut) {
                NodeFuzzerSupport.reportFailure(
                    fuzzer = FUZZER_NAME,
                    budget = budget,
                    baseSeed = baseSeed,
                    caseCount = JS_BRIDGE_CASE_COUNT,
                    replayClass = TEST_CLASS,
                    replayMethod = "jsBridgeRpcFuzzerIgnoresMalformedResponsesAndCleansPendingMap",
                    failure = JSONObject()
                        .put("phase", "script-result")
                        .put("errorCode", result.errorCode ?: JSONObject.NULL)
                        .put("errorMessage", result.errorMessage ?: JSONObject.NULL)
                        .put("timedOut", result.timedOut),
                )
            }
            NodeEmbeddedTestHarness.assertSucceeded("bridge RPC fuzzer", result)
            assertTrue(result.stdout.contains("bridge.rpc.fuzzer.seed=$baseSeed"))
            assertTrue(result.stdout.contains("bridge.rpc.fuzzer.budget=${budget.id}"))
            assertTrue(result.stdout.contains("bridge.rpc.fuzzer.invalid=9/0/3"))
            assertTrue(
                result.stdout.contains(
                    "bridge.rpc.fuzzer.codes=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_INVALID_REQUEST|ERR_AUTOJS6_BRIDGE_INVALID_REQUEST|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD",
                ),
            )
            assertTrue(result.stdout.contains("bridge.rpc.fuzzer.requests=5"))
            assertTrue(result.stdout.contains("bridge.rpc.fuzzer.pending=0"))
        } catch (throwable: Throwable) {
            NodeFuzzerSupport.reportFailure(
                fuzzer = FUZZER_NAME,
                budget = budget,
                baseSeed = baseSeed,
                caseCount = JS_BRIDGE_CASE_COUNT,
                replayClass = TEST_CLASS,
                replayMethod = "jsBridgeRpcFuzzerIgnoresMalformedResponsesAndCleansPendingMap",
                throwable = throwable,
            )
            throw throwable
        }
    }

    private fun assertStableProviderError(
        response: NodeBridgeResponse,
        module: String,
        method: String,
    ) {
        assertFalse(response.toJson(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, response.error?.code)
        assertEquals(module, response.error?.module)
        assertEquals(method, response.error?.method)
    }

    private fun liveMalformedCases(budget: NodeFuzzerBudget): List<LiveRequestCase> {
        val baseCases = listOf(
            LiveRequestCase("invalid-json", "{"),
            LiveRequestCase("top-level-array", "[]"),
            LiveRequestCase("missing-id", """{"module":"toast","method":"showToast","args":[]}"""),
            LiveRequestCase("blank-id", """{"id":"","module":"toast","method":"showToast","args":[]}"""),
            LiveRequestCase("numeric-id", """{"id":42,"module":"toast","method":"showToast","args":[]}"""),
            LiveRequestCase("missing-module", """{"id":"missing-module","method":"showToast","args":[]}"""),
            LiveRequestCase("numeric-module", """{"id":"numeric-module","module":7,"method":"showToast","args":[]}"""),
            LiveRequestCase("missing-method", """{"id":"missing-method","module":"toast","args":[]}"""),
            LiveRequestCase("boolean-method", """{"id":"boolean-method","module":"toast","method":true,"args":[]}"""),
            LiveRequestCase(
                "wrong-args-object",
                """{"id":"wrong-args-object","module":"app","method":"startActivity","args":{"action":"android.intent.action.VIEW"}}""",
                expectedModule = "app",
                expectedMethod = "startActivity",
            ),
            LiveRequestCase(
                "wrong-args-string",
                """{"id":"wrong-args-string","module":"toast","method":"showToast","args":"hello"}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "permissions-string",
                """{"id":"permissions-string","module":"toast","method":"showToast","args":[],"permissions":"toast"}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "permissions-blank",
                """{"id":"permissions-blank","module":"toast","method":"showToast","args":[],"permissions":[""]}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "permissions-number",
                """{"id":"permissions-number","module":"toast","method":"showToast","args":[],"permissions":[1]}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "timeout-zero",
                """{"id":"timeout-zero","module":"toast","method":"showToast","args":[],"timeoutMs":0}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "timeout-negative",
                """{"id":"timeout-negative","module":"toast","method":"showToast","args":[],"timeoutMs":-1}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "timeout-string",
                """{"id":"timeout-string","module":"toast","method":"showToast","args":[],"timeoutMs":"fast"}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
        )
        if (budget != NodeFuzzerBudget.EXTENDED) {
            return baseCases
        }
        return baseCases + listOf(
            LiveRequestCase("empty-object", "{}"),
            LiveRequestCase("null-id", """{"id":null,"module":"toast","method":"showToast","args":[]}"""),
            LiveRequestCase(
                "args-number",
                """{"id":"args-number","module":"toast","method":"showToast","args":7}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "permissions-object",
                """{"id":"permissions-object","module":"toast","method":"showToast","args":[],"permissions":{}}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
            LiveRequestCase(
                "timeout-object",
                """{"id":"timeout-object","module":"toast","method":"showToast","args":[],"timeoutMs":{}}""",
                expectedModule = "toast",
                expectedMethod = "showToast",
            ),
        )
    }

    private fun liveValidCases(budget: NodeFuzzerBudget): List<LiveRequestCase> {
        val baseCases = listOf(
            LiveRequestCase(
                "huge-args",
                JSONObject()
                    .put("id", "huge-args")
                    .put("module", "fake")
                    .put("method", "echo")
                    .put("args", hugeArgs())
                    .put("timeoutMs", 1000)
                    .toString(),
            ),
            LiveRequestCase(
                "too-many-args",
                JSONObject()
                    .put("id", "too-many-args")
                    .put("module", "fake")
                    .put("method", "many")
                    .put("args", JSONArray().apply { repeat(160) { put(it) } })
                    .put("timeoutMs", 1000)
                    .toString(),
            ),
            LiveRequestCase(
                "binary-like-args",
                JSONObject()
                    .put("id", "binary-like-args")
                    .put("module", "fake")
                    .put("method", "binary")
                    .put("args", JSONArray().put(JSONObject().put("dataBase64", "AAH/").put("text", "line\\u0000value")))
                    .put("timeoutMs", 1000)
                    .toString(),
            ),
        )
        if (budget != NodeFuzzerBudget.EXTENDED) {
            return baseCases
        }
        return baseCases + LiveRequestCase(
            "nested-valid-args",
            JSONObject()
                .put("id", "nested-valid-args")
                .put("module", "fake")
                .put("method", "nested")
                .put("args", JSONArray().put(JSONObject().put("nested", JSONArray().put(true).put(JSONObject().put("n", 1)))))
                .put("timeoutMs", 1000)
                .toString(),
        )
    }

    private fun hugeArgs(): JSONArray {
        return JSONArray().apply {
            repeat(96) { index ->
                put(JSONObject().put("index", index).put("value", "x".repeat(128)))
            }
        }
    }

    private fun newLiveBridgeSession(
        name: String,
        provider: NodeBridgeCapabilityProvider,
        bridgeLimits: NodeBridgeLimits.Policy = NodeBridgeLimits.defaults,
    ): LiveBridgeSessionFixture {
        val root = File(context.cacheDir, name).apply {
            deleteRecursively()
        }
        val sessionClass = Class.forName(
            "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService${'$'}NodeBridgeFileTransportSession",
        )
        val constructor = sessionClass.getDeclaredConstructor(
            File::class.java,
            NodeBridgeLimits.Policy::class.java,
            NodeBridgeCapabilityProvider::class.java,
        ).apply {
            isAccessible = true
        }
        val session = constructor.newInstance(root, bridgeLimits, provider)
        val configJson = sessionClass.getDeclaredMethod("configJson").apply {
            isAccessible = true
        }.invoke(session) as String
        val start = sessionClass.getDeclaredMethod("start").apply {
            isAccessible = true
        }
        val stop = sessionClass.getDeclaredMethod("stop").apply {
            isAccessible = true
        }
        return LiveBridgeSessionFixture(
            root = root,
            session = session,
            config = JSONObject(configJson),
            start = { start.invoke(session) },
            stop = {
                stop.invoke(session)
                root.deleteRecursively()
            },
        )
    }

    private fun waitForBridgeResponse(responseDir: File, requestId: String, timeoutMs: Long): File {
        val responseFile = File(responseDir, "$requestId.json")
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!responseFile.isFile && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(25L)
        }
        assertTrue("Timed out waiting for bridge response $requestId", responseFile.isFile)
        return responseFile
    }

    private fun settleEmbeddedScriptServiceProcess() {
        context.stopService(Intent(context, NativeNodeEmbeddedScriptService::class.java))
        SystemClock.sleep(600L)
    }

    private data class LiveRequestCase(
        val fileId: String,
        val json: String,
        val expectedModule: String? = null,
        val expectedMethod: String? = null,
    )

    private data class LiveBridgeSessionFixture(
        val root: File,
        val session: Any,
        val config: JSONObject,
        val start: () -> Unit,
        val stop: () -> Unit,
    )

    companion object {
        private const val TEST_CLASS = "org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest"
        private const val FUZZER_NAME = "bridge-rpc"
        private const val DEFAULT_BASE_SEED = 44040103L
        private const val NORMAL_LIVE_MALFORMED_CASES = 17
        private const val NORMAL_LIVE_VALID_CASES = 3
        private const val JS_BRIDGE_CASE_COUNT = 17
    }
}
