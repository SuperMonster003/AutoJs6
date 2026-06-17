package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NodeAutoJsBridgeInstrumentationTest {

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
    fun kotlinBridgeProtocolRoundTripsRequestResponseAndError() {
        val request = NodeBridgeRequest(
            id = "bridge-1",
            module = "fake",
            method = "echo",
            argsJson = """["hello",{"count":2}]""",
            timeoutMs = 1234L,
            permissions = listOf("test.permission"),
        )

        val parsedRequest = NodeBridgeRequest.fromJson(request.toJson())
        assertEquals(request.id, parsedRequest.id)
        assertEquals(request.module, parsedRequest.module)
        assertEquals(request.method, parsedRequest.method)
        assertEquals(1234L, parsedRequest.timeoutMs)
        assertEquals(listOf("test.permission"), parsedRequest.permissions)
        assertEquals("hello", JSONArray(parsedRequest.argsJson).getString(0))
        assertEquals(2, JSONArray(parsedRequest.argsJson).getJSONObject(1).getInt("count"))

        val provider = FakeNodeBridgeCapabilityProvider()
        var echoResponse: NodeBridgeResponse? = null
        provider.dispatch(request) { response ->
            echoResponse = response
        }
        assertEquals(1, provider.requests.size)
        assertEquals(true, echoResponse?.ok)
        assertEquals("hello", JSONArray(echoResponse?.resultJson).getString(0))

        val success = NodeBridgeResponse.success("bridge-2", """{"ok":true}""")
        val parsedSuccess = NodeBridgeResponse.fromJson(success.toJson())
        assertEquals(true, parsedSuccess.ok)
        assertEquals(true, JSONObject(parsedSuccess.resultJson.orEmpty()).getBoolean("ok"))

        val failure = NodeBridgeResponse.failure(
            "bridge-3",
            NodeBridgeError.timeout(module = "fake", method = "slow"),
        )
        val parsedFailure = NodeBridgeResponse.fromJson(failure.toJson())
        assertFalse(parsedFailure.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_TIMEOUT, parsedFailure.error?.code)
        assertEquals("fake", parsedFailure.error?.module)
        assertEquals("slow", parsedFailure.error?.method)

        val malformed = """{"id":"bad-1","module":"app"}"""
        assertEquals("bad-1", NodeBridgeRequest.recoverId(malformed))
        assertEquals("app", NodeBridgeRequest.recoverModule(malformed))
        assertTrue(runCatching { NodeBridgeRequest.fromJson(malformed) }.isFailure)
        assertTrue(
            runCatching {
                NodeBridgeRequest.fromJson("""{"id":"bad-args","module":"app","method":"openAppSetting","args":{"packageName":"x"}}""")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                NodeBridgeRequest.fromJson("""{"id":"bad-permissions","module":"toast","method":"showToast","args":[],"permissions":"toast"}""")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                NodeBridgeRequest.fromJson("""{"id":"bad-timeout","module":"toast","method":"showToast","args":[],"timeoutMs":0}""")
            }.isFailure,
        )
        val mixedRequests = NodeBridgeRequest.fromJsonArray(
            """[{"id":"ok-1","module":"toast","method":"showToast","args":[]},{"id":"bad-2","module":"app"},{"id":"bad-3","module":"app","method":"openAppSetting","args":{"packageName":"x"}}]""",
        )
        assertEquals(1, mixedRequests.size)
        assertEquals("ok-1", mixedRequests.first().id)

        val invalidRequest = NodeBridgeError.invalidRequest(module = "app", method = "startActivity")
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, invalidRequest.code)
        assertEquals("app", invalidRequest.module)
        assertEquals("startActivity", invalidRequest.method)
    }

    @Test
    fun kotlinBridgeModuleRegistryAndUnavailableErrorsAreStable() {
        assertTrue(NodeBridgeModules.supports("toast", "showToast"))
        assertTrue(NodeBridgeModules.supports("images", "readImage"))
        assertTrue(NodeBridgeModules.supportedMethodsByModule.getValue("accessibility").contains("clickText"))
        assertFalse(NodeBridgeModules.supports("app", "execScript"))
        assertFalse(NodeBridgeModules.supports("missing", "method"))

        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val latch = CountDownLatch(2)
        val responses = mutableListOf<NodeBridgeResponse>()

        listOf(
            NodeBridgeRequest(
                id = "missing-module",
                module = "missing",
                method = "method",
                argsJson = "[]",
                timeoutMs = 1000L,
            ),
            NodeBridgeRequest(
                id = "missing-method",
                module = "app",
                method = "execScript",
                argsJson = "[]",
                timeoutMs = 1000L,
            ),
        ).forEach { request ->
            provider.dispatch(request) { response ->
                responses += response
                latch.countDown()
            }
        }

        assertTrue("Timed out waiting for unavailable bridge responses", latch.await(2, TimeUnit.SECONDS))
        val missingModule = responses.first { it.id == "missing-module" }
        assertFalse(missingModule.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, missingModule.error?.code)
        assertEquals("missing", missingModule.error?.module)
        assertEquals("method", missingModule.error?.method)
        assertEquals("AutoJs6 bridge module or method is not available: missing.method", missingModule.error?.message)

        val missingMethod = responses.first { it.id == "missing-method" }
        assertFalse(missingMethod.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, missingMethod.error?.code)
        assertEquals("app", missingMethod.error?.module)
        assertEquals("execScript", missingMethod.error?.method)
        assertEquals("AutoJs6 bridge module or method is not available: app.execScript", missingMethod.error?.message)
    }

    @Test
    fun liveBridgeFileTransportRespondsToMalformedRequests() {
        val provider = FakeNodeBridgeCapabilityProvider()
        val fixture = newLiveBridgeSession("node-bridge-malformed-session", provider)
        val config = fixture.config
        val requestDir = File(config.getString("requestDir"))
        val responseDir = File(config.getString("responseDir"))
        val requestId = "malformed-live-request"
        File(requestDir, "$requestId.json").writeText(
            """{"id":"$requestId","module":"app","method":"openAppSetting","args":{"packageName":"x"}}""",
        )

        try {
            fixture.start()
            val responseFile = File(responseDir, "$requestId.json")
            val deadline = SystemClock.uptimeMillis() + 2_000L
            while (!responseFile.isFile && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(25L)
            }
            assertTrue("Timed out waiting for malformed bridge response", responseFile.isFile)

            val response = NodeBridgeResponse.fromJson(responseFile.readText())
            assertEquals(requestId, response.id)
            assertFalse(response.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, response.error?.code)
            assertEquals("app", response.error?.module)
            assertEquals("openAppSetting", response.error?.method)
            assertTrue(response.error?.message.orEmpty().contains("AutoJs6 bridge request is malformed"))
            assertTrue(response.error?.message.orEmpty().contains("args must be a JSON array"))
            assertEquals(0, provider.requests.size)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun liveBridgeFileTransportIgnoresDuplicateProviderCallbacks() {
        val callbackAttempts = AtomicInteger(0)
        val provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
                callbackAttempts.incrementAndGet()
                callback(NodeBridgeResponse.success(request.id, "\"first\""))
                callback(NodeBridgeResponse.success(request.id, "\"second\""))
            }
        }
        val fixture = newLiveBridgeSession("node-bridge-duplicate-callback-session", provider)
        val requestDir = File(fixture.config.getString("requestDir"))
        val responseDir = File(fixture.config.getString("responseDir"))
        val requestId = "duplicate-callback"
        File(requestDir, "$requestId.json").writeText(
            """{"id":"$requestId","module":"toast","method":"showToast","args":["hello"],"timeoutMs":1000}""",
        )

        try {
            fixture.start()
            val responseFile = waitForBridgeResponse(responseDir, requestId)
            val responseJson = JSONObject(responseFile.readText())
            assertEquals(requestId, responseJson.getString("id"))
            assertTrue(responseJson.getBoolean("ok"))
            assertEquals("first", responseJson.getString("result"))
            assertEquals(1, callbackAttempts.get())
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun liveBridgeFileTransportConvertsProviderDispatchExceptionToFailure() {
        val provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
                throw IllegalStateException("provider boom")
            }
        }
        val fixture = newLiveBridgeSession("node-bridge-provider-exception-session", provider)
        val requestDir = File(fixture.config.getString("requestDir"))
        val responseDir = File(fixture.config.getString("responseDir"))
        val requestId = "provider-exception"
        File(requestDir, "$requestId.json").writeText(
            """{"id":"$requestId","module":"toast","method":"showToast","args":["hello"],"timeoutMs":1000}""",
        )

        try {
            fixture.start()
            val response = NodeBridgeResponse.fromJson(waitForBridgeResponse(responseDir, requestId).readText())
            assertEquals(requestId, response.id)
            assertFalse(response.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, response.error?.code)
            assertEquals("toast", response.error?.module)
            assertEquals("showToast", response.error?.method)
            assertEquals("provider boom", response.error?.message)
        } finally {
            fixture.stop()
        }
    }

    @Test
    fun jsBridgeFakeTransportEchoesArgs() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const resolved = require.resolve("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result: {
                        module: request.module,
                        method: request.method,
                        args: request.args,
                        permissions: request.permissions,
                        timeoutMs: request.timeoutMs
                      }
                    }));
                  });
                }
              });
              const response = await bridge.callAutoJs(
                "fake",
                "echo",
                ["hello", { count: 2 }],
                { timeoutMs: 1000, permissions: ["test.permission"] }
              );
              console.log("bridge.echo.resolve=" + resolved);
              console.log("bridge.echo.request=" + requests[0].module + "/" + requests[0].method + "/" + requests[0].args[0]);
              console.log("bridge.echo.response=" + response.module + "/" + response.method + "/" + response.args[1].count);
              console.log("bridge.echo.permissions=" + response.permissions.join("|"));
              console.log("bridge.echo.pending=" + bridge.__test.pendingCount());
              console.log("bridge.echo.frozen=" + Object.isFrozen(bridge) + "/" + Object.isFrozen(bridge.__test));
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-echo-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("bridge.echo.resolve=autojs6:bridge"))
        assertTrue(result.stdout.contains("bridge.echo.request=fake/echo/hello"))
        assertTrue(result.stdout.contains("bridge.echo.response=fake/echo/2"))
        assertTrue(result.stdout.contains("bridge.echo.permissions=test.permission"))
        assertTrue(result.stdout.contains("bridge.echo.pending=0"))
        assertTrue(result.stdout.contains("bridge.echo.frozen=true/true"))
    }

    @Test
    fun toastModuleDispatchesLiveHostBridgeRequestAndBlocksShadowing() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-toast-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge toast working directory: $absolutePath" }
        }
        File(workDir, "node_modules/toast").apply {
            check(mkdirs()) { "Failed to create toast shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const toast = require("toast");
              const resolved = require.resolve("toast");
              const response = await toast.showToast(
                "AutoJs6 Node bridge toast smoke",
                { duration: "short", log: true, timeoutMs: 1000 }
              );
              console.log("toast.resolve=" + resolved);
              console.log("toast.shadow=" + (toast.shadow === true));
              console.log("toast.result=" + response);
              console.log("toast.alias=" + (toast.toast === toast.showToast));
              console.log("toast.frozen=" + Object.isFrozen(toast));
            })();
            """.trimIndent(),
            "<embedded-autojs-toast-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("toast.resolve=toast"))
        assertTrue(result.stdout.contains("toast.shadow=false"))
        assertTrue(result.stdout.contains("toast.result=null"))
        assertTrue(result.stdout.contains("toast.alias=true"))
        assertTrue(result.stdout.contains("toast.frozen=true"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])

        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"].orEmpty())
        assertEquals(1, responses.length())
        assertTrue(responses.getJSONObject(0).getBoolean("ok"))
    }

    @Test
    fun appModuleExposesHostInfoQueuesLaunchPackageAndBlocksShadowing() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-app-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge app working directory: $absolutePath" }
        }
        File(workDir, "node_modules/app").apply {
            check(mkdirs()) { "Failed to create app shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const app = require("app");
              const resolved = require.resolve("app");
              const response = await app.launchPackage(app.packageName, { timeoutMs: 1000 });
              const missing = await app.launchApp("__autojs6_missing_app__", { timeoutMs: 1000 });
              console.log("app.resolve=" + resolved);
              console.log("app.packageName=" + app.packageName);
              console.log("app.versionName.type=" + typeof app.versionName);
              console.log("app.versionCode.type=" + typeof app.versionCode);
              console.log("app.launch.result=" + response);
              console.log("app.launchMissing.result=" + missing);
              console.log("app.shadow=" + (app.shadow === true));
              console.log("app.frozen=" + Object.isFrozen(app));
              console.log("app.methods=" + [
                typeof app.launchPackage,
                typeof app.launchApp,
                typeof app.openAppSetting,
                typeof app.startActivity
              ].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-app-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("app.resolve=app"))
        assertTrue(result.stdout.contains("app.packageName=${context.packageName}"))
        assertTrue(result.stdout.contains("app.versionName.type=string"))
        assertTrue(result.stdout.contains("app.versionCode.type=number"))
        assertTrue(result.stdout.contains("app.launch.result=true"))
        assertTrue(result.stdout.contains("app.launchMissing.result=false"))
        assertTrue(result.stdout.contains("app.shadow=false"))
        assertTrue(result.stdout.contains("app.frozen=true"))
        assertTrue(result.stdout.contains("app.methods=function|function|function|function"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("2", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])

        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"].orEmpty())
        assertEquals(2, responses.length())
        assertTrue(responses.getJSONObject(0).getBoolean("ok"))
        assertTrue(responses.getJSONObject(0).getBoolean("result"))
        assertTrue(responses.getJSONObject(1).getBoolean("ok"))
        assertFalse(responses.getJSONObject(1).getBoolean("result"))
    }

    @Test
    fun enginesModuleExposesCurrentEngineAndLiveBridgeMethods() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-engines-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge engines working directory: $absolutePath" }
        }
        File(workDir, "node_modules/engines").apply {
            check(mkdirs()) { "Failed to create engines shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const engines = require("engines");
              const resolved = require.resolve("engines");
              const mine = engines.myEngine();
              const all = await engines.all({ timeoutMs: 1000 });
              const stopAll = await engines.stopAll({ timeoutMs: 1000 });
              const stopSelf = await engines.stopSelf({ timeoutMs: 1000 });
              console.log("engines.resolve=" + resolved);
              console.log("engines.shadow=" + (engines.shadow === true));
              console.log("engines.frozen=" + Object.isFrozen(engines));
              console.log("engines.my.frozen=" + Object.isFrozen(mine));
              console.log("engines.all.frozen=" + Object.isFrozen(all) + "/" + Object.isFrozen(all[0]));
              console.log("engines.my.name=" + mine.engineName);
              console.log("engines.my.backend=" + mine.backend);
              console.log("engines.my.source=" + mine.sourceName);
              console.log("engines.my.cwd=" + mine.cwd);
              console.log("engines.my.packageName=" + mine.packageName);
              console.log("engines.my.nodeVersion=" + mine.nodeVersion);
              console.log("engines.all.length=" + all.length);
              console.log("engines.all.same=" + (all[0].id === mine.id));
              console.log("engines.stopAll.result=" + stopAll);
              console.log("engines.stopSelf.result=" + stopSelf);
              console.log("engines.execMethods=" + [
                typeof engines.execScript,
                typeof engines.execScriptFile,
                typeof engines.execAutoFile
              ].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-engines-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("engines.resolve=engines"))
        assertTrue(result.stdout.contains("engines.shadow=false"))
        assertTrue(result.stdout.contains("engines.frozen=true"))
        assertTrue(result.stdout.contains("engines.my.frozen=true"))
        assertTrue(result.stdout.contains("engines.all.frozen=true/true"))
        assertTrue(result.stdout.contains("engines.my.name=nodejs"))
        assertTrue(result.stdout.contains("engines.my.backend=embedded"))
        assertTrue(result.stdout.contains("engines.my.source=<embedded-autojs-engines-source.js>"))
        assertTrue(result.stdout.contains("engines.my.cwd=${workDir.absolutePath}"))
        assertTrue(result.stdout.contains("engines.my.packageName=${context.packageName}"))
        assertTrue(result.stdout.contains("engines.my.nodeVersion=v"))
        assertTrue(result.stdout.contains("engines.all.length=1"))
        assertTrue(result.stdout.contains("engines.all.same=true"))
        assertTrue(result.stdout.contains("engines.stopAll.result=0"))
        assertTrue(result.stdout.contains("engines.stopSelf.result=true"))
        assertTrue(result.stdout.contains("engines.execMethods=function|function|undefined"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("3", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])

        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"].orEmpty())
        assertEquals(3, responses.length())
        assertTrue(responses.getJSONObject(0).getBoolean("ok"))
        assertEquals(1, responses.getJSONObject(0).getJSONArray("result").length())
        assertTrue(responses.getJSONObject(1).getBoolean("ok"))
        assertEquals(0, responses.getJSONObject(1).getInt("result"))
        assertTrue(responses.getJSONObject(2).getBoolean("ok"))
        assertTrue(responses.getJSONObject(2).getBoolean("result"))
    }

    @Test
    fun dialogsModuleUsesFakeTransportAndBlocksShadowing() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-dialogs-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge dialogs working directory: $absolutePath" }
        }
        File(workDir, "node_modules/dialogs").apply {
            check(mkdirs()) { "Failed to create dialogs shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const results = {
                    alert: null,
                    confirm: true,
                    input: "typed-value",
                    select: 1
                  };
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result: results[request.method]
                    }));
                  });
                }
              });
              const dialogs = require("dialogs");
              const resolved = require.resolve("dialogs");
              const alertResult = await dialogs.alert("Title", "Message", { timeoutMs: 1000 });
              const confirmResult = await dialogs.confirm("Confirm", { timeoutMs: 1000 });
              const inputResult = await dialogs.input("Input", "prefill", { timeoutMs: 1000 });
              const promptResult = await dialogs.prompt("Prompt", { timeoutMs: 1000 });
              const selectResult = await dialogs.select("Select", ["a", 2], { timeoutMs: 1000 });
              console.log("dialogs.resolve=" + resolved);
              console.log("dialogs.shadow=" + (dialogs.shadow === true));
              console.log("dialogs.frozen=" + Object.isFrozen(dialogs));
              console.log("dialogs.prompt.alias=" + (dialogs.prompt === dialogs.input));
              console.log("dialogs.alert.result=" + String(alertResult));
              console.log("dialogs.confirm.result=" + confirmResult);
              console.log("dialogs.input.result=" + inputResult);
              console.log("dialogs.prompt.result=" + promptResult);
              console.log("dialogs.select.result=" + selectResult);
              console.log("dialogs.requests=" + requests.map(r => r.method + ":" + r.args.join("|")).join(","));
              console.log("dialogs.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-dialogs-fake-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("dialogs.resolve=dialogs"))
        assertTrue(result.stdout.contains("dialogs.shadow=false"))
        assertTrue(result.stdout.contains("dialogs.frozen=true"))
        assertTrue(result.stdout.contains("dialogs.prompt.alias=true"))
        assertTrue(result.stdout.contains("dialogs.alert.result=undefined"))
        assertTrue(result.stdout.contains("dialogs.confirm.result=true"))
        assertTrue(result.stdout.contains("dialogs.input.result=typed-value"))
        assertTrue(result.stdout.contains("dialogs.prompt.result=typed-value"))
        assertTrue(result.stdout.contains("dialogs.select.result=1"))
        assertTrue(result.stdout.contains("alert:Title|Message"))
        assertTrue(result.stdout.contains("confirm:Confirm|"))
        assertTrue(result.stdout.contains("input:Input|prefill"))
        assertTrue(result.stdout.contains("input:Prompt|"))
        assertTrue(result.stdout.contains("select:Select|a,2"))
        assertTrue(result.stdout.contains("dialogs.pending=0"))
    }

    @Test
    fun dialogsModuleRejectsWithStableCodeUntilUiProviderIsConnected() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const dialogs = require("dialogs");
              try {
                await dialogs.alert("Needs UI provider", "not available in this smoke", { timeoutMs: 1000 });
                console.log("dialogs.android.unexpected=true");
              } catch (error) {
                console.log("dialogs.android.code=" + error.code + "/" + error.autojs6Code);
                console.log("dialogs.android.target=" + error.module + "/" + error.method);
                console.log("dialogs.android.message=" + error.message);
              }
            })();
            """.trimIndent(),
            "<embedded-autojs-dialogs-android-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("dialogs.android.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("dialogs.android.target=dialogs/alert"))
        assertTrue(result.stdout.contains("dialogs.android.message=AutoJs6 dialogs bridge requires a UI capability provider."))
        assertFalse(result.stdout.contains("dialogs.android.unexpected=true"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])

        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"].orEmpty())
        assertEquals(1, responses.length())
        val response = responses.getJSONObject(0)
        assertFalse(response.getBoolean("ok"))
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, response.getJSONObject("error").getString("code"))
    }

    @Test
    fun accessibilityModuleUsesFakeTransportAndBlocksShadowing() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-accessibility-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge accessibility working directory: $absolutePath" }
        }
        File(workDir, "node_modules/accessibility").apply {
            check(mkdirs()) { "Failed to create accessibility shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const results = {
                    isEnabled: true,
                    ensureEnabled: true,
                    click: true,
                    back: true,
                    home: true,
                    recentApps: true,
                    findByText: {
                      text: "OK",
                      desc: "Confirm",
                      id: "android:id/button1",
                      className: "android.widget.Button",
                      bounds: { left: 1, top: 2, right: 3, bottom: 4 },
                      clickable: true,
                      enabled: true
                    },
                    clickText: true
                  };
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result: results[request.method]
                    }));
                  });
                }
              });
              const accessibility = require("accessibility");
              const resolved = require.resolve("accessibility");
              const isEnabled = await accessibility.isEnabled({ timeoutMs: 1000 });
              const ensured = await accessibility.ensureEnabled({ prompt: true, timeoutMs: 1000 });
              const clicked = await accessibility.click(12, 34, { timeoutMs: 1000 });
              const backed = await accessibility.back({ timeoutMs: 1000 });
              const homed = await accessibility.home({ timeoutMs: 1000 });
              const recent = await accessibility.recentApps({ timeoutMs: 1000 });
              const node = await accessibility.findByText("OK", { timeoutMs: 1000 });
              const clickText = await accessibility.clickText("OK", { timeoutMs: 1000 });
              try {
                await accessibility.click("x", 1);
                console.log("accessibility.invalid.unexpected=true");
              } catch (error) {
                console.log("accessibility.invalid=" + error.code + "/" + error.module + "/" + error.method);
              }
              const clickRequest = requests.find(r => r.method === "click");
              const findRequest = requests.find(r => r.method === "findByText");
              console.log("accessibility.resolve=" + resolved);
              console.log("accessibility.shadow=" + (accessibility.shadow === true));
              console.log("accessibility.frozen=" + Object.isFrozen(accessibility));
              console.log("accessibility.booleans=" + [isEnabled, ensured, clicked, backed, homed, recent, clickText].join("|"));
              console.log("accessibility.node=" + node.text + "/" + node.desc + "/" + node.id + "/" + node.bounds.left + "/" + Object.isFrozen(node) + "/" + Object.isFrozen(node.bounds));
              console.log("accessibility.methods=" + requests.map(r => r.method).join("|"));
              console.log("accessibility.click.args=" + clickRequest.args.join("|"));
              console.log("accessibility.find.args=" + findRequest.args[0] + "/" + findRequest.args[1].timeoutMs);
              console.log("accessibility.permissions=" + requests.every(r => r.permissions.indexOf("accessibility") >= 0));
              console.log("accessibility.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-fake-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("accessibility.resolve=accessibility"))
        assertTrue(result.stdout.contains("accessibility.shadow=false"))
        assertTrue(result.stdout.contains("accessibility.frozen=true"))
        assertTrue(result.stdout.contains("accessibility.booleans=true|true|true|true|true|true|true"))
        assertTrue(result.stdout.contains("accessibility.node=OK/Confirm/android:id/button1/1/true/true"))
        assertTrue(result.stdout.contains("accessibility.methods=isEnabled|ensureEnabled|click|back|home|recentApps|findByText|clickText"))
        assertTrue(result.stdout.contains("accessibility.click.args=12|34"))
        assertTrue(result.stdout.contains("accessibility.find.args=OK/1000"))
        assertTrue(result.stdout.contains("accessibility.permissions=true"))
        assertTrue(result.stdout.contains("accessibility.invalid=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/click"))
        assertFalse(result.stdout.contains("accessibility.invalid.unexpected=true"))
        assertTrue(result.stdout.contains("accessibility.pending=0"))
    }

    @Test
    fun accessibilityModuleReportsDisabledAndRejectsActionsUntilProviderIsConnected() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const accessibility = require("accessibility");
              const enabled = await accessibility.isEnabled({ timeoutMs: 1000 });
              const ensured = await accessibility.ensureEnabled({ prompt: false, timeoutMs: 1000 });
              console.log("accessibility.android.enabled=" + enabled);
              console.log("accessibility.android.ensured=" + ensured);
              try {
                await accessibility.click(1, 2, { timeoutMs: 1000 });
                console.log("accessibility.android.unexpected=true");
              } catch (error) {
                console.log("accessibility.android.code=" + error.code + "/" + error.autojs6Code);
                console.log("accessibility.android.target=" + error.module + "/" + error.method);
                console.log("accessibility.android.message=" + error.message);
              }
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-android-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("accessibility.android.enabled=false"))
        assertTrue(result.stdout.contains("accessibility.android.ensured=false"))
        assertTrue(result.stdout.contains("accessibility.android.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("accessibility.android.target=accessibility/click"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "accessibility.android.message=AutoJs6 accessibility bridge requires an enabled accessibility capability provider in the current process.",
            ),
        )
        assertFalse(result.stdout.contains("accessibility.android.unexpected=true"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("3", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])

        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"].orEmpty())
        assertEquals(3, responses.length())
        assertTrue(responses.getJSONObject(0).getBoolean("ok"))
        assertFalse(responses.getJSONObject(0).getBoolean("result"))
        assertTrue(responses.getJSONObject(1).getBoolean("ok"))
        assertFalse(responses.getJSONObject(1).getBoolean("result"))
        assertFalse(responses.getJSONObject(2).getBoolean("ok"))
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, responses.getJSONObject(2).getJSONObject("error").getString("code"))
    }

    @Test
    fun androidAccessibilityBridgeReportsDisabledAndRejectsActionsUntilProviderIsConnected() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val latch = CountDownLatch(2)
        val responses = mutableListOf<NodeBridgeResponse>()

        listOf(
            NodeBridgeRequest(
                id = "accessibility-enabled",
                module = "accessibility",
                method = "isEnabled",
                argsJson = "[]",
                timeoutMs = 1000L,
                permissions = listOf("accessibility"),
            ),
            NodeBridgeRequest(
                id = "accessibility-click",
                module = "accessibility",
                method = "click",
                argsJson = "[1,2]",
                timeoutMs = 1000L,
                permissions = listOf("accessibility"),
            ),
        ).forEach { request ->
            provider.dispatch(request) { response ->
                responses += response
                latch.countDown()
            }
        }

        assertTrue("Timed out waiting for Android accessibility bridge response", latch.await(2, TimeUnit.SECONDS))
        val status = responses.first { it.id == "accessibility-enabled" }
        assertTrue(status.ok)
        assertEquals("false", status.resultJson)

        val action = responses.first { it.id == "accessibility-click" }
        assertFalse(action.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, action.error?.code)
        assertEquals("accessibility", action.error?.module)
        assertEquals("click", action.error?.method)
    }

    @Test
    fun mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing() {
        assumeEmbeddedScriptExecutionEnabled()

        val workDir = File(context.cacheDir, "node-bridge-media-image-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge media/image working directory: $absolutePath" }
        }
        listOf("media_projection", "image", "images").forEach { moduleName ->
            File(workDir, "node_modules/$moduleName").apply {
                check(mkdirs()) { "Failed to create $moduleName shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText("""module.exports = { shadow: true };""")
        }

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "media_projection" && request.method === "requestScreenCapture") {
                    result = { id: "capturer-1", width: 1080, height: 2400 };
                  } else if (request.module === "media_projection" && request.method === "nextImage") {
                    result = { id: "capture-1", width: 1080, height: 2400 };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "readImage") {
                    result = { id: request.module + ":" + request.args[0], width: 10, height: 20 };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "findImage") {
                    result = { x: request.module === "images" ? 9 : 7, y: 8 };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "recycle") {
                    result = true;
                  } else if (request.module === "media_projection" && request.method === "stop") {
                    result = true;
                  }
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result
                    }));
                  });
                }
              });
              const media = require("media_projection");
              const image = require("image");
              const images = require("images");
              const capturer = await media.requestScreenCapture({ orientation: "portrait", timeoutMs: 1000 });
              const capture = await capturer.nextImage({ timeoutMs: 1000 });
              const template = await image.readImage("./template.png", { timeoutMs: 1000 });
              const point = await image.findImage(capture, template, { threshold: 0.8, timeoutMs: 1000 });
              const aliasTemplate = await images.readImage("./alias.png", { timeoutMs: 1000 });
              const aliasPoint = await images.findImage(aliasTemplate, template, { timeoutMs: 1000 });
              const recycled = await image.recycle(template, { timeoutMs: 1000 });
              const stopped = await capturer.stop({ timeoutMs: 1000 });
              try {
                await image.findImage({}, template);
                console.log("media.image.invalid.unexpected=true");
              } catch (error) {
                console.log("media.image.invalid=" + error.code + "/" + error.module + "/" + error.method);
              }
              const mediaRequest = requests.find(r => r.method === "requestScreenCapture");
              const nextRequest = requests.find(r => r.method === "nextImage");
              console.log("media.resolve=" + require.resolve("media_projection"));
              console.log("image.resolve=" + require.resolve("image"));
              console.log("images.resolve=" + require.resolve("images"));
              console.log("media.shadow=" + (media.shadow === true));
              console.log("image.shadow=" + (image.shadow === true));
              console.log("images.shadow=" + (images.shadow === true));
              console.log("media.frozen=" + Object.isFrozen(media) + "/" + Object.isFrozen(capturer));
              console.log("image.frozen=" + Object.isFrozen(image) + "/" + Object.isFrozen(images) + "/" + Object.isFrozen(capture) + "/" + Object.isFrozen(point));
              console.log("media.handles=" + capturer.__autojs6ScreenCapturerHandle + "/" + capture.__autojs6ImageHandle + "/" + template.__autojs6ImageHandle + "/" + aliasTemplate.__autojs6ImageHandle);
              console.log("media.sizes=" + capturer.width + "x" + capturer.height + "/" + capture.width + "x" + capture.height);
              console.log("image.points=" + point.x + "," + point.y + "/" + aliasPoint.x + "," + aliasPoint.y);
              console.log("image.voids=" + String(recycled) + "/" + String(stopped));
              console.log("media.requests=" + requests.map(r => r.module + ":" + r.method).join("|"));
              console.log("media.orientation=" + mediaRequest.args[0].orientation);
              console.log("media.next.args=" + nextRequest.args[0] + "/" + nextRequest.args[1].timeoutMs);
              console.log("media.permissions=" + requests.filter(r => r.module === "media_projection").every(r => r.permissions.indexOf("media_projection") >= 0));
              console.log("image.permissions=" + requests.filter(r => r.module === "image" || r.module === "images").every(r => r.permissions.indexOf("image") >= 0));
              console.log("media.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-media-image-fake-source.js>",
            workingDirectory = workDir.absolutePath,
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("media.resolve=media_projection"))
        assertTrue(result.stdout.contains("image.resolve=image"))
        assertTrue(result.stdout.contains("images.resolve=images"))
        assertTrue(result.stdout.contains("media.shadow=false"))
        assertTrue(result.stdout.contains("image.shadow=false"))
        assertTrue(result.stdout.contains("images.shadow=false"))
        assertTrue(result.stdout.contains("media.frozen=true/true"))
        assertTrue(result.stdout.contains("image.frozen=true/true/true/true"))
        assertTrue(result.stdout.contains("media.handles=capturer-1/capture-1/image:./template.png/images:./alias.png"))
        assertTrue(result.stdout.contains("media.sizes=1080x2400/1080x2400"))
        assertTrue(result.stdout.contains("image.points=7,8/9,8"))
        assertTrue(result.stdout.contains("image.voids=undefined/undefined"))
        assertTrue(result.stdout.contains("media.requests=media_projection:requestScreenCapture|media_projection:nextImage|image:readImage|image:findImage|images:readImage|images:findImage|image:recycle|media_projection:stop"))
        assertTrue(result.stdout.contains("media.orientation=portrait"))
        assertTrue(result.stdout.contains("media.next.args=capturer-1/1000"))
        assertTrue(result.stdout.contains("media.permissions=true"))
        assertTrue(result.stdout.contains("image.permissions=true"))
        assertTrue(result.stdout.contains("media.image.invalid=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/image/findImage"))
        assertFalse(result.stdout.contains("media.image.invalid.unexpected=true"))
        assertTrue(result.stdout.contains("media.pending=0"))
    }

    @Test
    fun mediaProjectionAndImageModulesRejectWithStableCodeUntilProvidersAreConnected() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const media = require("media_projection");
              const image = require("image");
              try {
                await media.requestScreenCapture({ timeoutMs: 1000 });
                console.log("media.android.unexpected=true");
              } catch (error) {
                console.log("media.android.code=" + error.code + "/" + error.autojs6Code);
                console.log("media.android.target=" + error.module + "/" + error.method);
                console.log("media.android.message=" + error.message);
              }
              try {
                await image.readImage("./template.png", { timeoutMs: 1000 });
                console.log("image.android.unexpected=true");
              } catch (error) {
                console.log("image.android.code=" + error.code + "/" + error.autojs6Code);
                console.log("image.android.target=" + error.module + "/" + error.method);
                console.log("image.android.message=" + error.message);
              }
            })();
            """.trimIndent(),
            "<embedded-autojs-media-image-android-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("media.android.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("media.android.target=media_projection/requestScreenCapture"))
        assertTrue(result.stdout.contains("media.android.message=AutoJs6 media_projection bridge requires a MediaProjection capability provider."))
        assertTrue(result.stdout.contains("image.android.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("image.android.target=image/readImage"))
        assertTrue(result.stdout.contains("image.android.message=AutoJs6 image bridge requires an image capability provider."))
        assertFalse(result.stdout.contains("media.android.unexpected=true"))
        assertFalse(result.stdout.contains("image.android.unexpected=true"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("2", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("2", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
    }

    @Test
    fun androidMediaProjectionAndImageBridgeRejectUntilProvidersAreConnected() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val latch = CountDownLatch(2)
        val responses = mutableListOf<NodeBridgeResponse>()

        listOf(
            NodeBridgeRequest(
                id = "media-unavailable",
                module = "media_projection",
                method = "requestScreenCapture",
                argsJson = """[{"orientation":"auto"}]""",
                timeoutMs = 1000L,
                permissions = listOf("media_projection"),
            ),
            NodeBridgeRequest(
                id = "image-unavailable",
                module = "image",
                method = "readImage",
                argsJson = """["./template.png"]""",
                timeoutMs = 1000L,
                permissions = listOf("image"),
            ),
        ).forEach { request ->
            provider.dispatch(request) { response ->
                responses += response
                latch.countDown()
            }
        }

        assertTrue("Timed out waiting for Android media/image bridge responses", latch.await(2, TimeUnit.SECONDS))
        val media = responses.first { it.id == "media-unavailable" }
        assertFalse(media.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, media.error?.code)
        assertEquals("media_projection", media.error?.module)
        assertEquals("requestScreenCapture", media.error?.method)

        val image = responses.first { it.id == "image-unavailable" }
        assertFalse(image.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, image.error?.code)
        assertEquals("image", image.error?.module)
        assertEquals("readImage", image.error?.method)
    }

    @Test
    fun appStartActivityRejectsInvalidDescriptorWithStableCode() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const app = require("app");
              try {
                await app.startActivity({});
                console.log("app.invalid.unexpected=true");
              } catch (error) {
                console.log("app.invalid.code=" + error.code + "/" + error.autojs6Code);
                console.log("app.invalid.target=" + error.module + "/" + error.method);
                console.log("app.invalid.message=" + error.message);
              }
            })();
            """.trimIndent(),
            "<embedded-autojs-app-invalid-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("app.invalid.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("app.invalid.target=app/startActivity"))
        assertTrue(result.stdout.contains("app.invalid.message=app.startActivity requires a JSON intent descriptor"))
        assertFalse(result.stdout.contains("app.invalid.unexpected=true"))
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])
    }

    @Test
    fun androidAppBridgeRejectsInvalidStartActivityDescriptor() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "app-invalid",
                module = "app",
                method = "startActivity",
                argsJson = "[{}]",
                timeoutMs = 1000L,
                permissions = listOf("app.activity"),
            ),
        ) {
            response = it
            latch.countDown()
        }

        assertTrue("Timed out waiting for Android app bridge response", latch.await(2, TimeUnit.SECONDS))
        val actual = checkNotNull(response)
        assertFalse(actual.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, actual.error?.code)
        assertEquals("app", actual.error?.module)
        assertEquals("startActivity", actual.error?.method)
    }

    @Test
    fun androidDialogsBridgeRejectsUntilUiProviderIsConnected() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "dialogs-unavailable",
                module = "dialogs",
                method = "confirm",
                argsJson = """["Title","Message"]""",
                timeoutMs = 1000L,
                permissions = listOf("dialogs"),
            ),
        ) {
            response = it
            latch.countDown()
        }

        assertTrue("Timed out waiting for Android dialogs bridge response", latch.await(2, TimeUnit.SECONDS))
        val actual = checkNotNull(response)
        assertFalse(actual.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, actual.error?.code)
        assertEquals("dialogs", actual.error?.module)
        assertEquals("confirm", actual.error?.method)
    }

    @Test
    fun jsBridgeTimeoutRejectsAndClearsPendingCall() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            return (async function main() {
              const bridge = require("autojs6:bridge");
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  console.log("bridge.timeout.request=" + request.module + "/" + request.method + "/" + request.timeoutMs);
                }
              });
              try {
                await bridge.callAutoJs("fake", "never", [], { timeoutMs: 100 });
                console.log("bridge.timeout.unexpected=true");
              } catch (error) {
                console.log("bridge.timeout.code=" + error.code + "/" + error.autojs6Code);
                console.log("bridge.timeout.target=" + error.module + "/" + error.method);
                console.log("bridge.timeout.pending=" + bridge.__test.pendingCount());
              }
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-timeout-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("bridge.timeout.request=fake/never/100"))
        assertTrue(result.stdout.contains("bridge.timeout.code=ERR_AUTOJS6_BRIDGE_TIMEOUT/ERR_AUTOJS6_BRIDGE_TIMEOUT"))
        assertTrue(result.stdout.contains("bridge.timeout.target=fake/never"))
        assertTrue(result.stdout.contains("bridge.timeout.pending=0"))
        assertFalse(result.stdout.contains("bridge.timeout.unexpected=true"))
    }

    @Test
    fun jsBridgeAbortSignalRejectsAndClearsPendingCalls() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  console.log("bridge.abort.request=" + request.module + "/" + request.method);
                }
              });

              const preController = new AbortController();
              preController.abort();
              const preError = await bridge.callAutoJs("dialogs", "alert", [], {
                timeoutMs: 1000,
                signal: preController.signal
              }).then(
                () => "unexpected",
                (error) => error
              );
              console.log("bridge.abort.pre.code=" + preError.code + "/" + preError.autojs6Code);
              console.log("bridge.abort.pre.requests=" + requests.length);
              console.log("bridge.abort.pre.pending=" + bridge.__test.pendingCount());

              const controller = new AbortController();
              const pending = bridge.callAutoJs("accessibility", "findOne", [], {
                timeoutMs: 1000,
                signal: controller.signal
              }).then(
                () => "unexpected",
                (error) => error
              );
              setImmediate(() => {
                controller.abort();
              });
              const duringError = await pending;
              const request = requests[0];
              console.log("bridge.abort.during.code=" + duringError.code + "/" + duringError.autojs6Code);
              console.log("bridge.abort.during.target=" + duringError.module + "/" + duringError.method);
              console.log("bridge.abort.during.pending=" + bridge.__test.pendingCount());
              console.log("bridge.abort.during.late=" + bridge.__test.receiveMessage(JSON.stringify({
                id: request.id,
                ok: true,
                result: "late"
              })));
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-abort-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("bridge.abort.pre.code=ERR_AUTOJS6_BRIDGE_CANCELLED/ERR_AUTOJS6_BRIDGE_CANCELLED"))
        assertTrue(result.stdout.contains("bridge.abort.pre.requests=0"))
        assertTrue(result.stdout.contains("bridge.abort.pre.pending=0"))
        assertTrue(result.stdout.contains("bridge.abort.request=accessibility/findOne"))
        assertTrue(result.stdout.contains("bridge.abort.during.code=ERR_AUTOJS6_BRIDGE_CANCELLED/ERR_AUTOJS6_BRIDGE_CANCELLED"))
        assertTrue(result.stdout.contains("bridge.abort.during.target=accessibility/findOne"))
        assertTrue(result.stdout.contains("bridge.abort.during.pending=0"))
        assertTrue(result.stdout.contains("bridge.abort.during.late=false"))
    }

    @Test
    fun jsBridgeModuleAbortSignalsAreForwarded() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const dialogs = require("dialogs");
              const accessibility = require("accessibility");
              const image = require("image");
              const shell = require("shell");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                }
              });

              function capture(promise) {
                return promise.then(
                  () => "unexpected",
                  (error) => error
                );
              }
              function imageHandle(id) {
                return {
                  id,
                  __autojs6ImageHandle: id,
                  width: 1,
                  height: 1
                };
              }

              const controller = new AbortController();
              const options = { timeoutMs: 5000, signal: controller.signal };
              const errorsPromise = Promise.all([
                capture(dialogs.alert("Title", "Message", options)),
                capture(accessibility.findByText("OK", options)),
                capture(image.findImage(imageHandle("source"), imageHandle("template"), options)),
                capture(shell.exec("echo ok", options))
              ]);
              await new Promise((resolve) => setImmediate(resolve));
              console.log("bridge.abort.wrapper.requests=" + requests.map((request) => request.module + "/" + request.method).join(","));
              controller.abort();
              const errors = await errorsPromise;
              console.log("bridge.abort.wrapper.codes=" + errors.map((error) => error.code).join(","));
              console.log("bridge.abort.wrapper.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-wrapper-abort-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("bridge.abort.wrapper.requests=dialogs/alert,accessibility/findByText,image/findImage,shell/exec"))
        assertTrue(result.stdout.contains("bridge.abort.wrapper.codes=ERR_AUTOJS6_BRIDGE_CANCELLED,ERR_AUTOJS6_BRIDGE_CANCELLED,ERR_AUTOJS6_BRIDGE_CANCELLED,ERR_AUTOJS6_BRIDGE_CANCELLED"))
        assertTrue(result.stdout.contains("bridge.abort.wrapper.pending=0"))
    }

    @Test
    fun jsBridgeDestroyRejectsPendingCalls() {
        assumeEmbeddedScriptExecutionEnabled()

        val result = runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              bridge.__test.setTransport({
                postMessage() {}
              });
              const pending = bridge.callAutoJs("fake", "destroyed", [], { timeoutMs: 5000 }).then(
                () => "unexpected",
                (error) => error
              );
              setImmediate(() => {
                bridge.__test.destroy("service destroyed");
              });
              const error = await pending;
              console.log("bridge.destroy.code=" + error.code + "/" + error.autojs6Code);
              console.log("bridge.destroy.target=" + error.module + "/" + error.method);
              console.log("bridge.destroy.message=" + error.message);
              console.log("bridge.destroy.pending=" + bridge.__test.pendingCount());
              const afterDestroy = await bridge.callAutoJs("fake", "after", [], { timeoutMs: 10 }).then(
                () => "unexpected",
                (afterError) => afterError.code
              );
              console.log("bridge.destroy.after=" + afterDestroy);
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-destroy-source.js>",
        )

        assertTrue(result.errorMessage.orEmpty() + "\n" + result.stdout, result.succeeded)
        assertTrue(result.stdout.contains("bridge.destroy.code=ERR_AUTOJS6_BRIDGE_PROCESS_DEAD/ERR_AUTOJS6_BRIDGE_PROCESS_DEAD"))
        assertTrue(result.stdout.contains("bridge.destroy.target=fake/destroyed"))
        assertTrue(result.stdout.contains("bridge.destroy.message=service destroyed"))
        assertTrue(result.stdout.contains("bridge.destroy.pending=0"))
        assertTrue(result.stdout.contains("bridge.destroy.after=ERR_AUTOJS6_BRIDGE_PROCESS_DEAD"))
    }

    private fun runEmbeddedScript(
        source: String,
        sourceName: String,
        timeoutMs: Long = 5000L,
        workingDirectory: String = context.cacheDir.absolutePath,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
            ),
        )
    }

    private fun assumeEmbeddedScriptExecutionEnabled() {
        assumeTrue(
            "Build with -Pautojs.nodejs.embedded.script.enabled=true to run AutoJs bridge tests.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
    }

    private fun settleEmbeddedScriptServiceProcess() {
        context.stopService(Intent(context, NativeNodeEmbeddedScriptService::class.java))
        SystemClock.sleep(600L)
    }

    private fun newLiveBridgeSession(name: String, provider: NodeBridgeCapabilityProvider): LiveBridgeSessionFixture {
        val root = File(context.cacheDir, name).apply {
            deleteRecursively()
        }
        val sessionClass = Class.forName(
            "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService\$NodeBridgeFileTransportSession",
        )
        val constructor = sessionClass.getDeclaredConstructor(
            File::class.java,
            NodeBridgeLimits.Policy::class.java,
            NodeBridgeCapabilityProvider::class.java,
        ).apply {
            isAccessible = true
        }
        val session = constructor.newInstance(root, NodeBridgeLimits.defaults, provider)
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

    private fun waitForBridgeResponse(responseDir: File, requestId: String): File {
        val responseFile = File(responseDir, "$requestId.json")
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (!responseFile.isFile && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(25L)
        }
        assertTrue("Timed out waiting for bridge response $requestId", responseFile.isFile)
        return responseFile
    }

    private data class LiveBridgeSessionFixture(
        val root: File,
        val session: Any,
        val config: JSONObject,
        val start: () -> Unit,
        val stop: () -> Unit,
    )
}
