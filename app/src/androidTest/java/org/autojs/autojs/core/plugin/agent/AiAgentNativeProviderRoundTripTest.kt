package org.autojs.autojs.core.plugin.agent

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.hostbroker.*
import org.autojs.plugin.ai.agent.api.*
import org.autojs.plugin.ai.agent.api.AiAgentContract as C
import org.autojs.plugin.host.capability.api.*
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Real host + installed Agent + separate fake Provider UIDs. No network or accepted device writes. */
class AiAgentNativeProviderRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val agentPackage = "io.github.supermonster003.autojs6.plugin.ai.agent"
    private val providerPackage = "org.autojs.plugin.ai.provider.fake"

    @Test fun nativeBatchReturnsRealDeviceObservationsToTheSameProviderSession() = runScenario("batch")
    @Test fun invalidWholeBatchReturnsErrorsWithoutDispatchingItsValidFirstCall() = runScenario("invalid")
    @Test fun deniedConfirmationReturnsAnErrorWithoutWritingTheClipboard() = runScenario("deny")
    @Test fun cancellationAtConfirmationClosesThePausedProviderWithoutContinuing() = runScenario("cancel")

    private fun runScenario(scenario: String) {
        assumeTrue("Opt in with -e autojs.agent.nativeProvider true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.nativeProvider").toBoolean())
        assertTrue("Use a disposable AVD", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        for (pkg in listOf(agentPackage, providerPackage)) {
            assertEquals(PackageManager.SIGNATURE_MATCH, context.packageManager.checkSignatures(context.packageName, pkg))
        }
        val uid = context.packageManager.getApplicationInfo(agentPackage, 0).uid
        assertNotEquals(Process.myUid(), uid)
        assertNotEquals(uid, context.packageManager.getApplicationInfo(providerPackage, 0).uid)
        val ready = CountDownLatch(1)
        var plugin: IAiAgentPlugin? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                plugin = IAiAgentPlugin.Stub.asInterface(binder); ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(agentPackage, "$agentPackage.AiAgentPluginService")),
            connection, Context.BIND_AUTO_CREATE))
        val grant = AiAgentGrant.default()
        val core = HostCapabilityBrokerCore(context, "native-provider-${UUID.randomUUID()}", grant,
            sourceName = "ai-agent", engineName = "ai-agent")
        val capabilities = HostCapabilityBrokerStub(core, uid)
        val model = AiAgentModelBroker(context, uid, grant,
            AiProviderComponent(providerPackage, "$providerPackage.service.FakeAiProviderService"), "fake.local")
        val modelEvents = CopyOnWriteArrayList<JSONObject>()
        val submitted = CopyOnWriteArrayList<JSONObject>()
        val methods = CopyOnWriteArrayList<String>()
        val generations = AtomicInteger()
        val cancelled = AtomicInteger()
        val events = LinkedBlockingQueue<JSONObject>()
        var link: IAiAgentLink? = null
        try {
            assertTrue(ready.await(15, TimeUnit.SECONDS))
            val tracedModel = object : IAiAgentModelBroker.Stub() {
                override fun getBrokerInfo() = model.brokerInfo
                override fun listTargets(request: Bundle?, callback: IAiAgentModelCallback?) = model.listTargets(request, callback)
                override fun generate(request: Bundle?, callback: IAiAgentModelCallback?) {
                    generations.incrementAndGet()
                    model.generate(request, object : IAiAgentModelCallback.Stub() {
                        override fun onEvent(event: Bundle) {
                            event.getString(C.KEY_MODEL_EVENT_JSON)?.let { modelEvents += JSONObject(it) }
                            callback?.onEvent(event)
                        }
                    })
                }
                override fun submitToolResults(request: Bundle?) {
                    submitted += JSONObject(requireNotNull(request).getString(C.KEY_MODEL_REQUEST_JSON)!!)
                    model.submitToolResults(request)
                }
                override fun cancel(reference: Bundle?) { cancelled.incrementAndGet(); model.cancel(reference) }
                override fun destroy(reason: Bundle?) = model.destroy(reason)
            }
            val tracedCapabilities = object : IHostCapabilityBroker.Stub() {
                override fun getBrokerInfo() = capabilities.brokerInfo
                override fun dispatch(request: Bundle?, callback: IHostCapabilityCallback?) {
                    val call = JSONObject(requireNotNull(request).getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                    methods += "${call.getString("module")}.${call.getString("method")}"
                    capabilities.dispatch(request, callback)
                }
                override fun destroy(reason: Bundle?) = capabilities.destroy(reason)
            }
            link = checkNotNull(plugin).attach(envelope(C.KEY_LINK_CONFIG_JSON, JSONObject()), tracedModel, tracedCapabilities,
                object : IAiAgentLinkCallback.Stub() {
                    override fun onStatus(status: Bundle?) = Unit
                    override fun onEvent(event: Bundle?) = Unit
                })
            val options = JSONObject().put("target", "profile:agent-tools").put("interaction", "script")
                .put("confirm", "cautious").put("memory", false).put("tools", JSONArray(listOf("observe", "act", "user")))
            val id = decode(link.startRun(envelope(C.KEY_RUN_REQUEST_JSON,
                JSONObject().put("goal", "agent-conformance:native-$scenario").put("options", options)),
                object : IAiAgentRunCallback.Stub() {
                    override fun onRunEvent(event: Bundle) { events.offer(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) }
                })).getString("runId")
            var confirmations = 0
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var terminal = false
            while (!terminal && SystemClock.elapsedRealtime() < deadline) {
                val event = events.poll(250, TimeUnit.MILLISECONDS) ?: continue
                if (event.optString("type") == "confirmation") {
                    confirmations++
                    assertEquals("clipboard_set", event.getString("tool"))
                    assertTrue(scenario in setOf("deny", "cancel"))
                    if (scenario == "cancel") link.cancelRun(envelope(C.KEY_RUN_REF_JSON, JSONObject().put("runId", id)))
                    else assertTrue(decode(link.respond(envelope(C.KEY_RUN_RESPONSE_JSON,
                        JSONObject().put("runId", id).put("requestId", event.getString("requestId"))
                            .put("allowed", false).put("scope", "once")))).getBoolean("accepted"))
                }
                terminal = event.optString("type") == "done"
            }
            assertTrue("Task did not finish", terminal)
            val row = decode(link.getRun(envelope(C.KEY_RUN_REF_JSON, JSONObject().put("runId", id))))
            assertEquals(row.toString(), if (scenario == "cancel") "cancelled" else "completed", row.getString("state"))
            assertEquals(1, generations.get())
            val calls = modelEvents.single { it.optString("type") == "tool_calls" }
            assertEquals(1, calls.getInt("round"))
            assertEquals(if (scenario in setOf("deny", "cancel")) 1 else 0, confirmations)
            assertFalse(methods.contains("clipboard.setText"))
            if (scenario == "cancel") {
                assertTrue(cancelled.get() > 0)
                assertTrue(submitted.isEmpty())
                val cancelDeadline = SystemClock.elapsedRealtime() + 5000
                while (modelEvents.none { it.optString("type") == "cancelled" } && SystemClock.elapsedRealtime() < cancelDeadline) SystemClock.sleep(20)
                assertEquals(1, modelEvents.count { it.optString("type") == "cancelled" })
            } else {
                val resume = submitted.single()
                assertEquals(calls.getString("requestId"), resume.getString("requestId"))
                assertEquals(1, resume.getInt("round"))
                val results = resume.getJSONArray("results")
                assertEquals(calls.getJSONArray("calls").length(), results.length())
                for (index in 0 until results.length()) {
                    val result = results.getJSONObject(index)
                    assertEquals(calls.getJSONArray("calls").getJSONObject(index).getString("callId"), result.getString("callId"))
                    assertEquals(scenario != "batch", result.getBoolean("isError"))
                    assertTrue(JSONObject(result.getString("output")).has("remaining_budget"))
                }
                assertEquals(2, row.getJSONObject("result").getJSONObject("usage").getInt("modelCalls"))
                val completed = modelEvents.single { it.optString("type") == "completed" }
                assertEquals(calls.getString("requestId"), completed.getString("requestId"))
                assertTrue(completed.getString("text").contains("Provider received Agent tool results"))
                if (scenario == "batch") {
                    assertEquals(listOf("device.info", "device.isScreenOn"), methods.toList())
                    assertTrue(results.getJSONObject(1).getString("output").contains("screenOn"))
                    val steps = row.getJSONArray("steps")
                    assertEquals(3, steps.length())
                    for (index in 0..1) assertEquals("NATIVE_TOOL", steps.getJSONObject(index).getJSONObject("decision").getString("parseMode"))
                } else {
                    assertTrue(methods.isEmpty())
                    assertTrue(results.toString().contains(if (scenario == "invalid") "TOOL_ARGUMENTS_INVALID" else "USER_DENIED"))
                }
            }
        } finally {
            val reason = envelope(H.KEY_REASON_JSON, JSONObject().put("reason", "native-provider-test-finished"))
            runCatching { link?.detach(reason) }
            model.close(); core.destroy(reason); context.unbindService(connection)
        }
    }

    private fun envelope(key: String, value: JSONObject) = Bundle().apply {
        putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, value.toString())
    }
    private fun decode(value: Bundle): JSONObject {
        assertNull(value.getString(C.KEY_ERROR_CODE))
        return JSONObject(value.getString(C.KEY_RUN_RESPONSE_JSON)!!)
    }
}
