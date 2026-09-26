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

/** Exercises real in-memory Rhino execution behind the Agent-owned engines.execScript extension. */
class AgentDynamicScriptExecutionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun inlineSourceReportsStructuredResultContextAndConsoleWithoutCreatingAScriptFile() = withFixture { root, provider ->
        val response = invoke(provider, exec("""
            console.log('dynamic-console-fixture');
            var context = ai.agent.context();
            if (!ai.agent.result({ value: 6 * 7, runId: context.runId, preset: context.presetName,
                cwd: files.cwd(), arguments: context.parameters })) throw Error('result rejected');
        """.trimIndent(), options = options().put("agentRunId", "dynamic-fixture").put("presetName", "fixture"))).await()
        val result = successfulResponse(response)
        assertEquals("success", result.getString("outcome"))
        assertTrue(result.getBoolean("finished"))
        assertTrue(result.getBoolean("resultReported"))
        val reported = result.getJSONObject("result")
        assertEquals(42, reported.getInt("value"))
        assertEquals("dynamic-fixture", reported.getString("runId"))
        assertEquals("fixture", reported.getString("preset"))
        assertEquals(root.canonicalPath, reported.getString("cwd"))
        assertEquals(0, reported.getJSONObject("arguments").length())
        assertTrue(result.getJSONArray("consoleTail").toString().contains("dynamic-console-fixture"))
        assertTrue(root.listFiles().orEmpty().none { it.extension == "js" })
        awaitRemoved(result)
    }

    @Test fun exceptionsAndNoResultAreReportedWithoutInventingStructuredOutput() = withFixture { _, provider ->
        val failure = successfulResponse(invoke(provider, exec("throw Error('dynamic-exception-fixture');")).await())
        assertEquals("exception", failure.getString("outcome"))
        assertEquals("SCRIPT_FAILED", failure.getJSONObject("error").getString("code"))
        assertTrue(failure.getJSONObject("error").getString("message").contains("dynamic-exception-fixture"))
        assertFalse(failure.getBoolean("resultReported"))
        awaitRemoved(failure)
        val success = successfulResponse(invoke(provider, exec("console.log('dynamic-no-result');")).await())
        assertEquals("success", success.getString("outcome"))
        assertFalse(success.getBoolean("resultReported")); assertTrue(success.isNull("result"))
        awaitRemoved(success)
    }

    @Test fun deadlineStopsTheActualInlineEngine() = withFixture { root, provider ->
        val marker = File(root, "timeout-ready")
        val pending = invoke(provider, exec(stalled(marker), options = options().put("timeoutMs", 2500)))
        awaitFile(marker, pending)
        val result = successfulResponse(pending.await())
        assertEquals("timeout", result.getString("outcome"))
        assertEquals("SCRIPT_TIMEOUT", result.getJSONObject("error").getString("code"))
        assertTrue(result.getBoolean("finished"))
        awaitRemoved(result)
    }

    @Test fun explicitStopOnlyCancelsTheMatchingInvocation() = withFixture { root, provider ->
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val firstMarker = File(root, "first-ready")
        val secondMarker = File(root, "second-ready")
        val first = invoke(provider, exec(stalled(firstMarker), options(firstId)))
        val second = invoke(provider, exec(stalled(secondMarker), options(secondId)))
        awaitFile(firstMarker, first); awaitFile(secondMarker, second)
        assertTrue(invoke(provider, stop(firstId)).await().ok)
        val stopped = successfulResponse(first.await())
        assertEquals("stopped", stopped.getString("outcome")); awaitRemoved(stopped)
        assertNull("Stopping one invocation stopped an unrelated owned engine", second.peek())
        assertTrue(invoke(provider, stop(secondId)).await().ok)
        val other = successfulResponse(second.await())
        assertEquals("stopped", other.getString("outcome")); awaitRemoved(other)
    }

    @Test fun providerDestructionCancelsItsInlineEngine() = withFixture { root, provider ->
        val marker = File(root, "destroy-ready")
        val pending = invoke(provider, exec(stalled(marker)))
        awaitFile(marker, pending)
        provider.destroy()
        val result = successfulResponse(pending.await())
        assertEquals("stopped", result.getString("outcome")); awaitRemoved(result)
    }

    @Test fun stopBeforeDispatchPreventsSourceEvaluationAndTokenReuse() = withFixture { root, provider ->
        val id = UUID.randomUUID().toString()
        val marker = File(root, "must-not-start")
        assertTrue(invoke(provider, stop(id)).await().ok)
        repeat(2) {
            val response = invoke(provider, exec(stalled(marker), options(id))).await()
            assertFalse(response.ok)
            assertTrue(response.error?.message?.contains("CANCELLED") == true)
        }
        assertFalse(marker.exists())
    }

    @Test fun earlyServiceCancellationIsVisibleBeforeSourceStarts() = withFixture { root, _ ->
        val marker = File(root, "early-must-not-start")
        val source = stalled(marker)
        val pending = Reply()
        var handle: Closeable? = null
        instrumentation.runOnMainSync {
            handle = NodeBridgeEngineDispatchService.dispatchDynamic(context, exec(source), "agent-generated.js", source,
                root.path, "early-dynamic", null, 10000, false, pending::accept)
            handle?.close()
        }
        try {
            val result = successfulResponse(pending.await())
            assertEquals("stopped", result.getString("outcome"))
            assertFalse(marker.exists()); awaitRemoved(result)
        } finally { handle?.close() }
    }

    @Test fun sourceLimitsOptionsAndUnsupportedRuntimeDirectivesCannotStartSource() = withFixture { root, provider ->
        val marker = File(root, "invalid-must-not-start")
        for (prefix in listOf("\"nodejs\";", "\"bun\";", "\"quickjs\";", "\"ui\";", "\"ui-thread\";")) {
            assertFalse(prefix, invoke(provider, exec(prefix + stalled(marker))).await().ok)
        }
        for (source in listOf(" ", "a".repeat(8193), "\uD800", "\u0000")) {
            assertFalse(invoke(provider, exec(source)).await().ok)
        }
        for (key in listOf("cwd", "loopTimes", "delay", "arguments", "waitMs", "unknown")) {
            assertFalse(key, invoke(provider, exec(stalled(marker), options().put(key, 1))).await().ok)
        }
        assertFalse(marker.exists())
    }

    private fun options(id: String = UUID.randomUUID().toString()) = JSONObject()
        .put("agentInvocationId", id).put("timeoutMs", 10000).put("captureConsole", true)
    private fun exec(source: String, options: JSONObject = options()) = NodeBridgeRequest(UUID.randomUUID().toString(),
        "engines", "execScript", JSONArray().put("agent-generated.js").put(source).put(options).toString(),
        15000, listOf("engines", "engines.exec"))
    private fun stop(id: String) = NodeBridgeRequest(UUID.randomUUID().toString(), "engines", "stop",
        JSONArray().put(JSONObject().put("agentInvocationId", id)).toString(), 5000, listOf("engines"))
    private fun stalled(marker: File) = "files.write(${JSONObject.quote(marker.path)}, 'ready'); while (true) sleep(20);"
    private fun successfulResponse(response: NodeBridgeResponse): JSONObject {
        assertTrue(response.error?.message, response.ok)
        return JSONObject(response.resultJson)
    }
    private fun invoke(provider: AndroidNodeBridgeCapabilityProvider, request: NodeBridgeRequest) =
        Reply().also { provider.dispatch(request, it::accept) }
    private inline fun withFixture(block: (File, AndroidNodeBridgeCapabilityProvider) -> Unit) {
        val root = File(context.cacheDir, "agent-dynamic-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val provider = AndroidNodeBridgeCapabilityProvider(context,
            engineInfo = NodeBridgeEngineInfo.fromContext(context, "agent-dynamic-test", cwd = root.path))
        try { block(root, provider) } finally { provider.destroy(); root.deleteRecursively() }
    }
    private fun awaitFile(file: File, pending: Reply) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) {
            pending.peek()?.let { fail("Dynamic script replied before startup: ${it.error?.code}, ${it.resultJson}") }
            Thread.sleep(20)
        }
        assertTrue("Dynamic script did not acknowledge startup", file.exists())
    }
    private fun awaitRemoved(result: JSONObject) {
        if (result.isNull("executionId")) return
        val id = result.getInt("executionId")
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (AutoJs.instance.scriptEngineService.engines.any { it.id == id } && SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertFalse("Owned inline engine survived completion", AutoJs.instance.scriptEngineService.engines.any { it.id == id })
    }
    private class Reply {
        private val done = CountDownLatch(1)
        private val count = AtomicInteger()
        private val value = AtomicReference<NodeBridgeResponse>()
        fun accept(response: NodeBridgeResponse) { count.incrementAndGet(); value.set(response); done.countDown() }
        fun peek(): NodeBridgeResponse? = value.get()
        fun await(): NodeBridgeResponse {
            assertTrue("Dynamic script bridge did not reply", done.await(20, TimeUnit.SECONDS))
            assertEquals(1, count.get())
            return value.get()
        }
    }
}
