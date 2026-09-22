package org.autojs.autojs.engine

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** P1.4 execution-layer checks. The future public ai.agent.result/context augment is not used. */
class AgentRegisteredScriptExecutionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun realRegisteredScriptReceivesDefaultsAndReportsStructuredResultThroughHostSlot() = withFixture { root, provider ->
        val script = File(root, "report.js").apply { writeText(header("@param {integer} [days=7] Age") + """
            var engine = engines.myEngine();
            var bridge = Packages.org.autojs.autojs.execution.AgentScriptExecutionState;
            var context = JSON.parse(String(bridge.contextJson(engine)));
            console.log("agent-result-fixture");
            if (!bridge.report(engine, JSON.stringify({ days: engine.execArgv.days, runId: context.runId,
                  contextDays: context.parameters.days, preset: context.presetName }))) throw Error("result rejected");
        """.trimIndent()) }
        val manifest = invoke(provider, request("readManifest", JSONArray().put(script.path))).await()
        assertTrue("The registered fixture was rejected: ${manifest.error?.message}", manifest.ok)
        val catalog = invoke(provider, request("listScripts", JSONArray())).await()
        assertTrue(catalog.ok)
        assertEquals("The valid fixture must appear in the directory catalog", 1, JSONArray(catalog.resultJson).length())
        assertEquals(script.canonicalPath, JSONArray(catalog.resultJson).getJSONObject(0).getString("path"))
        val response = invoke(provider, exec(script, JSONObject(), JSONObject().put("agentRunId", "run-fixture").put("presetName", "test"))).await()
        assertTrue(response.error?.message, response.ok)
        val result = JSONObject(response.resultJson)
        assertTrue(result.getBoolean("finished"))
        assertEquals("success", result.getString("outcome"))
        val reported = result.getJSONObject("result")
        assertEquals(7, reported.getInt("days"))
        assertEquals(7, reported.getInt("contextDays"))
        assertEquals("run-fixture", reported.getString("runId"))
        assertEquals("test", reported.getString("preset"))
        assertTrue(result.getJSONArray("consoleTail").toString().contains("agent-result-fixture"))
        awaitEngineRemoved(result.getInt("executionId"))
    }

    @Test fun noReportPreservesNullResultAndConsoleFallback() = withFixture { root, provider ->
        val script = File(root, "fallback.js").apply { writeText(header() + "console.log('agent-console-fallback');") }
        val response = invoke(provider, exec(script)).await()
        assertTrue(response.error?.message, response.ok)
        val result = JSONObject(response.resultJson)
        assertEquals("success", result.getString("outcome"))
        assertTrue(result.isNull("result"))
        assertTrue(result.getJSONArray("consoleTail").toString().contains("agent-console-fallback"))
        assertEquals("global-window", result.getString("consoleCaptureMode"))
        awaitEngineRemoved(result.getInt("executionId"))
    }

    @Test fun registeredTimeoutStopsTheScriptThatActuallyStarted() = withFixture { root, provider ->
        val ready = File(root, "timeout-ready.txt")
        val script = stalledScript(root, "timeout.js")
        val pending = invoke(provider, exec(script, JSONObject().put("ready", ready.path), JSONObject().put("timeoutMs", 2500)))
        awaitFile(ready, pending)
        val response = pending.await()
        assertTrue(response.error?.message, response.ok)
        val result = JSONObject(response.resultJson)
        assertEquals("timeout", result.getString("outcome"))
        assertEquals("SCRIPT_TIMEOUT", result.getJSONObject("error").getString("code"))
        assertTrue("forceStop did not complete within the cleanup window", result.getBoolean("finished"))
        awaitEngineRemoved(result.getInt("executionId"))
    }

    @Test fun destroyingTheProviderCancelsItsRunningRegisteredScript() = withFixture { root, provider ->
        val ready = File(root, "cancel-ready.txt")
        val script = stalledScript(root, "cancel.js")
        val pending = invoke(provider, exec(script, JSONObject().put("ready", ready.path)))
        awaitFile(ready, pending)
        provider.destroy()
        val response = pending.await()
        assertTrue(response.error?.message, response.ok)
        val result = JSONObject(response.resultJson)
        assertEquals("stopped", result.getString("outcome"))
        assertEquals("CANCELLED", result.getJSONObject("error").getString("code"))
        awaitEngineRemoved(result.getInt("executionId"))
    }

    @Test fun schemaAndRegistrationFailuresCannotStartSource() = withFixture { root, provider ->
        val script = File(root, "guarded.js").apply { writeText(header("@param {integer} days Age") + "throw Error('must not start');") }
        val missing = invoke(provider, exec(script)).await()
        assertFalse(missing.ok)
        val invalidType = invoke(provider, exec(script, JSONObject().put("days", "seven"))).await()
        assertFalse(invalidType.ok)
        val plain = File(root, "plain.js").apply { writeText("throw Error('must not start');") }
        assertFalse(invoke(provider, exec(plain)).await().ok)
    }

    @Test fun synchronousConsumerFailureDoesNotCauseASecondBridgeResponse() = withFixture { root, provider ->
        File(root, "listed.js").writeText(header() + "1;")
        val responses = AtomicInteger()
        val received = CountDownLatch(1)
        provider.dispatch(request("listScripts", JSONArray())) {
            responses.incrementAndGet()
            received.countDown()
            throw IllegalStateException("fixture consumer failed after accepting the response")
        }
        assertTrue(received.await(5, TimeUnit.SECONDS))
        assertEquals("A consumer exception must not be reinterpreted as a second request failure", 1, responses.get())
    }

    @Test fun immediateCancellationIsRememberedBeforeMainProcessServiceStartsSource() = withFixture { root, _ ->
        val marker = File(root, "must-not-start.txt")
        val script = File(root, "early-cancel.js").apply {
            writeText("files.write(" + JSONObject.quote(marker.path) + ", 'bad'); while (true) sleep(20);")
        }
        val pending = Reply()
        var handle: Closeable? = null
        instrumentation.runOnMainSync {
            handle = NodeBridgeEngineDispatchService.dispatchRegistered(context, exec(script), script.path,
                root.path, "{}", "early-cancel", null, 10000, false, callback = pending::accept)
            handle?.close()
        }
        try {
            val response = pending.await()
            assertTrue(response.error?.message, response.ok)
            val result = JSONObject(response.resultJson)
            assertEquals("stopped", result.getString("outcome"))
            assertFalse("A cancellation queued before service dispatch must gate script evaluation", marker.exists())
            if (!result.isNull("executionId")) awaitEngineRemoved(result.getInt("executionId"))
        } finally { handle?.close() }
    }

    @Test fun projectLaunchCannotReplaceThePreviouslyAuthorizedEntry() = withFixture { root, _ ->
        val authorized = File(root, "main.js").apply { writeText("throw Error('must not run');") }
        File(root, "other.js").writeText("throw Error('must not run');")
        File(root, "project.json").writeText("""{"name":"entry-guard","type":"node","main":"other.js"}""")
        val pending = Reply()
        NodeBridgeEngineDispatchService.dispatchRegistered(context, exec(authorized), root.path, root.path,
            "{}", "entry-guard", null, 10000, false, expectedScriptPath = authorized.path, callback = pending::accept).use {
            val response = pending.await()
            assertTrue(response.error?.message, response.ok)
            val result = JSONObject(response.resultJson)
            assertEquals("exception", result.getString("outcome"))
            assertEquals("SCRIPT_FAILED", result.getJSONObject("error").getString("code"))
            assertTrue(result.isNull("executionId"))
            assertTrue(result.getJSONObject("error").getString("message").contains("authorized entry"))
        }
    }

    private fun stalledScript(root: File, name: String) = File(root, name).apply {
        writeText(header("@param {string} ready Ready marker") + "files.write(engines.myEngine().execArgv.ready, 'ready'); while (true) sleep(20);")
    }

    private fun header(parameter: String = "") = """/**
         * @agent
         * @description Registered script execution fixture
         ${if (parameter.isBlank()) "" else "* $parameter"}
         * @timeout 10000
         */
    """.trimIndent() + "\n"

    private fun request(method: String, args: JSONArray) = NodeBridgeRequest(UUID.randomUUID().toString(), "agent", method,
        args.toString(), 15000, listOf("agent", "agent.exec", "engines", "engines.exec"))
    private fun exec(script: File, parameters: JSONObject = JSONObject(), options: JSONObject = JSONObject()) =
        request("execRegistered", JSONArray().put(script.path).put(parameters).put(options))

    private fun invoke(provider: AndroidNodeBridgeCapabilityProvider, request: NodeBridgeRequest): Reply =
        Reply().also { provider.dispatch(request, it::accept) }

    private inline fun withFixture(block: (File, AndroidNodeBridgeCapabilityProvider) -> Unit) {
        val root = File(context.cacheDir, "agent-execution-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val provider = AndroidNodeBridgeCapabilityProvider(context,
            engineInfo = NodeBridgeEngineInfo.fromContext(context, "agent-execution-test", cwd = root.path))
        try { block(root, provider) } finally { provider.destroy(); root.deleteRecursively() }
    }

    private fun awaitFile(file: File, pending: Reply) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) {
            pending.peek()?.let { response ->
                val outcome = response.resultJson?.let { runCatching { JSONObject(it).optString("outcome") }.getOrNull() }
                fail("Registered script replied before startup acknowledgement: code=${response.error?.code}, outcome=$outcome")
            }
            Thread.sleep(20)
        }
        assertTrue("Registered script did not acknowledge startup", file.exists())
    }

    private fun awaitEngineRemoved(id: Int) {
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (AutoJs.instance.scriptEngineService.engines.any { it.id == id } && SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertFalse("Owned script engine survived completion/cancellation", AutoJs.instance.scriptEngineService.engines.any { it.id == id })
    }

    private class Reply {
        private val done = CountDownLatch(1)
        private val count = AtomicInteger()
        private val value = AtomicReference<NodeBridgeResponse>()
        fun accept(response: NodeBridgeResponse) { count.incrementAndGet(); value.set(response); done.countDown() }
        fun peek(): NodeBridgeResponse? = value.get()
        fun await(): NodeBridgeResponse {
            assertTrue("Registered script bridge did not reply", done.await(20, TimeUnit.SECONDS))
            assertEquals(1, count.get())
            return value.get()
        }
    }
}
