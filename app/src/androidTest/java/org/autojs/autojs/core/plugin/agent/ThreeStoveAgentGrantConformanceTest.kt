package org.autojs.autojs.core.plugin.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.ai.*
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerStub
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.engine.NodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.engine.NodeRuntimeErrorCodes
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelCallback
import org.autojs.plugin.three.stove.agent.fake.IBrokerConformanceDriver
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in, deterministic: real foreign-UID Binder calls, no real model or device actions. */
class ThreeStoveAgentGrantConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var driver: IBrokerConformanceDriver
    private var connection: ServiceConnection? = null
    private var fixtureUid = -1
    private val cleanup = mutableListOf<() -> Unit>()

    @Before fun bindDriver() {
        assumeTrue("Enable autojs.threeStoveAgent.grant.conformance.enabled", InstrumentationRegistry.getArguments()
            .getString("autojs.threeStoveAgent.grant.conformance.enabled") == "true")
        assertEquals(PackageManager.SIGNATURE_MATCH, context.packageManager.checkSignatures(context.packageName, FIXTURE))
        val connected = CountDownLatch(1)
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                assertNull("Must cross a process boundary", service.queryLocalInterface("org.autojs.plugin.three.stove.agent.fake.IBrokerConformanceDriver"))
                driver = IBrokerConformanceDriver.Stub.asInterface(service)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue("Install test-apps:three-stove-agent-conformance first", context.bindService(
            Intent().setComponent(ComponentName(FIXTURE, "$FIXTURE.BrokerConformanceService")), next, Context.BIND_AUTO_CREATE))
        connection = next
        assertTrue(connected.await(10, TimeUnit.SECONDS))
        fixtureUid = driver.identity.getInt("uid")
        assertEquals(context.packageManager.getApplicationInfo(FIXTURE, 0).uid, fixtureUid)
        assertNotEquals(Process.myUid(), fixtureUid)
        assertNotEquals(Process.myPid(), driver.identity.getInt("pid"))
    }

    @After fun release() {
        cleanup.asReversed().forEach { it() }
        connection?.let { context.unbindService(it) }
    }

    @Test fun methodPermissionAndUtf8SizeCeilingsRejectBeforeDispatch() {
        val calls = AtomicInteger()
        val grant = grant().copy(maxRequestBytes = 256)
        val broker = capability(grant, provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
                calls.incrementAndGet()
                callback(NodeBridgeResponse.success(request.id, "true"))
            }
        })
        assertTrue(reply(broker, request("good")).ok)
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, NodeBridgeErrorCategory.CAPABILITY_DENIED,
            reply(broker, request("method", "files", "delete")))
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, NodeBridgeErrorCategory.CAPABILITY_DENIED,
            reply(broker, request("permission", permissions = listOf("device", "shell.root"))))
        // Multibyte UTF-8 crosses the broker ceiling without exceeding Binder's UTF-16 parcel limit.
        for (text in listOf("x".repeat(257), "界".repeat(90), "界".repeat(H.MAX_BRIDGE_INLINE_JSON_BYTES / 3 + 1))) {
            assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, NodeBridgeErrorCategory.RESOURCE_LIMIT,
                reply(broker, request("oversize", args = JSONArray().put(text).toString())))
        }
        assertEquals(1, calls.get())
        assertTrue(reply(broker, request("recovered")).ok)
        assertEquals(2, calls.get())
    }

    @Test fun omittingOrUnderdeclaringTokensCannotBypassTheActualProvider() {
        val broker = capability(grant().copy(permissions = emptySet()))
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, NodeBridgeErrorCategory.CAPABILITY_DENIED,
            reply(broker, request("omitted", permissions = emptyList())))
        val allowed = capability(grant())
        assertTrue(reply(allowed, request("normal")).ok)
    }

    @Test fun actualAccessibilityRateLimitRejectsAndThenRecovers() {
        val broker = capability(HostCapabilityGrant(setOf("accessibility.isEnabled"), setOf("accessibility"),
            accessibilityQueriesPerSecond = 1))
        fun query(id: String) = reply(broker, request(id, "accessibility", "isEnabled", listOf("accessibility")))
        assertTrue(query("first").ok)
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_RATE_LIMITED, NodeBridgeErrorCategory.RATE_LIMITED, query("too-fast"))
        // A full window, not scheduler timing, separates denial from recovery.
        Thread.sleep(1_100)
        assertTrue(query("recovered").ok)
    }

    @Test fun concurrencyCeilingAndRevocationTerminatePendingWork() {
        val started = CountDownLatch(1)
        val broker = capability(grant().copy(maxConcurrentCalls = 1), provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) { started.countDown() }
        })
        val pending = Responses()
        driver.dispatch(broker, envelope(request("pending")), pending)
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, NodeBridgeErrorCategory.RESOURCE_LIMIT,
            reply(broker, request("excess")))
        broker.core.destroy(null)
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, NodeBridgeErrorCategory.PROCESS_DEAD, pending.bridge())
        assertBridgeFailure(NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, NodeBridgeErrorCategory.PROCESS_DEAD,
            reply(broker, request("revoked")))
    }

    @Test fun foreignUidCannotReadCallCancelOrDestroyEitherBroker() {
        val core = HostCapabilityBrokerCore(context, "foreign", grant())
        cleanup += { core.destroy(null) }
        val wrongCapability = HostCapabilityBrokerStub(core, Process.myUid())
        val model = model(grant(), expectedUid = Process.myUid())
        val results = driver.probeUnauthorized(wrongCapability, model.broker)
        assertEquals(9, results.size())
        results.keySet().forEach { assertTrue(it, results.getBoolean(it)) }
        assertFalse(core.isDestroyed)
        assertTrue(JSONObject(model.broker.brokerInfo.getString(C.KEY_MODEL_BROKER_INFO_JSON)!!).getBoolean("available"))
        assertEquals(0, model.calls.get())
        // Positive control: the identical process succeeds only on its own grant.
        assertEquals("completed", generate(model(grant()), "positive").getString("type"))
    }

    @Test fun inputByteLimitAndCallsPerMinuteAreEnforcedAcrossBinder() {
        val bounded = model(grant().copy(maxInputBytesPerRequest = 6, modelCallsPerMinute = 1))
        assertEquals(C.ERROR_QUOTA_EXCEEDED, generate(bounded, "too-large", "界界界").getString("code"))
        assertEquals(0, bounded.catalogs.get())
        assertEquals("completed", generate(bounded, "at-limit", "界界").getString("type"))
        assertEquals(C.ERROR_QUOTA_EXCEEDED, generate(bounded, "rate", "ok").getString("code"))
        assertEquals(1, bounded.calls.get())
    }

    @Test fun appendedToolResultTransactionResumesAnAuthorizedForeignUidGeneration() {
        val fixture = model(grant(), nativeTools = true)
        val events = CopyOnWriteArrayList<JSONObject>()
        val pending = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val callback = object : IThreeStoveAgentModelCallback.Stub() {
            override fun onEvent(event: Bundle) {
                assertEquals(fixtureUid, Binder.getCallingUid())
                val value = JSONObject(event.getString(C.KEY_MODEL_EVENT_JSON)!!)
                events += value
                if (value.getString("type") == C.MODEL_EVENT_TOOL_CALLS) pending.countDown()
                if (value.getString("type") in setOf("failed", "completed", "cancelled")) terminal.countDown()
            }
        }
        fun payload(json: String) = Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(C.KEY_MODEL_REQUEST_JSON, json)
        }
        driver.model(fixture.broker, "generate", payload("""{"requestId":"native-binder","targetId":"local:fixture","messages":[{"role":"user","content":"synthetic"}],"maximumOutputTokens":100,"maximumToolRounds":1,"tools":[{"name":"ui_read","description":"Read fixture","inputSchema":{"type":"object"}}]}"""), callback)
        assertTrue("Native call did not cross the callback Binder", pending.await(10, TimeUnit.SECONDS))
        val call = events.single { it.getString("type") == C.MODEL_EVENT_TOOL_CALLS }
        assertEquals("native-call", call.getJSONArray("calls").getJSONObject(0).getString("callId"))
        assertEquals(1, call.getInt("round"))
        driver.model(fixture.broker, "submitToolResults", payload("""{"requestId":"native-binder","round":1,"results":[{"callId":"native-call","output":"observed"}]}"""), callback)
        assertTrue("Native result did not resume the original callback", terminal.await(10, TimeUnit.SECONDS))
        assertEquals(listOf("started", "tool_calls", "usage", "completed"), events.map { it.getString("type") })
        assertEquals((1..events.size).toList(), events.map { it.getInt("sequence") })
        assertTrue(events.all { it.getString("requestId") == "native-binder" })
        assertEquals("native-result:observed", events.last().getString("text"))
        assertEquals(2, fixture.calls.get())
        assertEquals(1, fixture.catalogs.get())
    }

    @Test fun tokenReservationsAndSettlementCannotExceedTheLinkGrant() {
        val reserved = model(grant().copy(maxTotalTokens = 4))
        assertEquals(C.ERROR_QUOTA_EXCEEDED, generate(reserved, "reservation", "abc", maximumOutputTokens = 3).getString("code"))
        assertEquals(0, reserved.calls.get())
        val exhausted = model(grant().copy(maxTotalTokens = 4), usage = 4)
        assertEquals("completed", generate(exhausted, "exact", "abc", maximumOutputTokens = 2).getString("type"))
        assertEquals(C.ERROR_QUOTA_EXCEEDED, generate(exhausted, "exhausted", "a").getString("code"))
        assertEquals(1, exhausted.calls.get())
        val overreported = model(grant().copy(maxTotalTokens = 4), usage = 5)
        val terminal = generate(overreported, "settlement", "a")
        assertEquals(C.ERROR_QUOTA_EXCEEDED, terminal.getString("code"))
        assertFalse(terminal.has("text"))
    }

    @Test fun rejectedAndAllowedBodiesNeverEnterHostLogcat() {
        val marker = "p7-private-${UUID.randomUUID()}"
        val broker = capability(HostCapabilityGrant(setOf("toast.showToast"), setOf("toast")))
        assertTrue(reply(broker, request("toast", "toast", "showToast", listOf("toast"),
            JSONArray().put(marker).put(JSONObject().put("log", true)).toString())).ok)
        assertFalse(reply(broker, request("denied", "files", "delete", args = JSONArray().put(marker).toString())).ok)
        val fake = model(grant().copy(maxInputBytesPerRequest = 128))
        assertEquals("completed", generate(fake, "private-model", marker).getString("type"))
        assertEquals(C.ERROR_QUOTA_EXCEEDED, generate(fake, "private-rejected-model", marker.repeat(8)).getString("code"))
        val logs = hostLogs()
        assertTrue("Must observe real host rejection logs", logs.contains("denied category="))
        assertFalse("Request text must not be present in host logs", logs.contains(marker))
    }

    @Test fun sameSignerNonPluginAttachBroadcastCannotClaimTheAgentIdentity() {
        assertEquals(PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(ThreeStoveAgentActions.PLUGIN_PERMISSION, FIXTURE))
        val marker = "p7-attach-private-${UUID.randomUUID()}"
        val proof = driver.identity
        @Suppress("DEPRECATION")
        val token = proof.getParcelable<android.app.PendingIntent>(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY)!!
        assertEquals(FIXTURE, token.creatorPackage)
        assertEquals(fixtureUid, token.creatorUid)
        val intent = Intent(ThreeStoveAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName).putExtras(proof)
        assertFalse(ThreeStoveAgentAttachRequestReceiver.isAuthorizedRequest(context, intent))
        for (mode in listOf("missing", "wrong-type", "foreign")) {
            val before = hostLogs().lineSequence().count { it.contains("Attach request rejected: identity not authorized") }
            val response = Responses()
            driver.sendAttach(mode, marker, response)
            assertTrue(response.await().getBoolean("delivered"))
            val logs = hostLogs()
            assertTrue("Manifest receiver must reject the delivered $mode broadcast", logs.lineSequence()
                .count { it.contains("Attach request rejected: identity not authorized") } > before)
            assertFalse(logs.contains(marker))
        }
    }

    private fun grant() = HostCapabilityGrant(setOf("device.info"), setOf("device"))
    private fun assertBridgeFailure(code: String, category: String, response: NodeBridgeResponse) {
        assertFalse(response.ok)
        assertEquals(code, response.error?.code)
        assertEquals(category, response.error?.category)
    }
    private fun capability(grant: HostCapabilityGrant, provider: NodeBridgeCapabilityProvider? = null): HostCapabilityBrokerStub {
        val core = HostCapabilityBrokerCore(context, "p7-grant", grant,
            providerFactory = provider?.let { value -> { _, _ -> value } })
        cleanup += { core.destroy(null) }
        return HostCapabilityBrokerStub(core, fixtureUid)
    }
    private fun request(id: String, module: String = "device", method: String = "info",
                        permissions: List<String> = listOf("device"), args: String = "[]") =
        NodeBridgeRequest(id, module, method, args, 5_000, permissions = permissions).toJson()
    private fun envelope(json: String) = Bundle().apply { putString(H.KEY_BRIDGE_REQUEST_JSON, json) }
    private fun reply(broker: HostCapabilityBrokerStub, json: String): NodeBridgeResponse {
        val response = Responses()
        driver.dispatch(broker, envelope(json), response)
        return response.bridge()
    }
    private inner class Responses : IHostCapabilityCallback.Stub() {
        private val done = CountDownLatch(1)
        private val values = CopyOnWriteArrayList<Bundle>()
        override fun onResponse(response: Bundle) {
            assertEquals("Callback must also originate in the separate APK", fixtureUid, Binder.getCallingUid())
            values += response
            done.countDown()
        }
        fun await(): Bundle {
            assertTrue("Driver response timed out", done.await(10, TimeUnit.SECONDS))
            return values.single()
        }
        fun bridge(): NodeBridgeResponse = NodeBridgeResponse.fromJson(await().getString(H.KEY_BRIDGE_RESPONSE_JSON)!!)
    }
    private class ModelFixture(val broker: ThreeStoveAgentModelBroker, val calls: AtomicInteger, val catalogs: AtomicInteger)
    private fun model(grant: HostCapabilityGrant, usage: Long = 1, expectedUid: Int = fixtureUid,
                      nativeTools: Boolean = false): ModelFixture {
        val calls = AtomicInteger()
        val catalogs = AtomicInteger()
        val component = AiProviderComponent(FIXTURE, "$FIXTURE.NotARealProvider")
        val target = AiPluginTargetSummary("local:fixture", "fake.agent", null, "fixture", "Fixture",
            if (nativeTools) listOf(org.autojs.plugin.ai.provider.api.AiProviderCapabilityId.TOOLS) else emptyList(),
            0, 0, AiTargetAvailability.AVAILABLE, true, true, 1024 * 1024, 64 * 1024,
            emptyList(), emptyList(), emptyList())
        fun handle() = object : AiPluginAskHandle { override fun cancel() = true; override fun close() = Unit }
        val broker = ThreeStoveAgentModelBroker(context, expectedUid, grant, component, "fake.agent",
            askRunner = AiPluginAskRunner { input, callback ->
                calls.incrementAndGet()
                callback(AiPluginAskOutcome.Completed("fixture-output:${input.messages.last().content}", "",
                    AiPluginUsage(inputTokens = null, outputTokens = null, totalTokens = usage, durationMillis = null), 0, target))
                handle()
            }, streamRunner = AiPluginStreamRunner { _, _ -> error("Streaming is outside this fixture") },
            catalogRunner = AiPluginCatalogRunner { _, callback ->
                catalogs.incrementAndGet()
                callback(AiPluginCatalogOutcome.Completed(AiPluginCatalog(component, "fake.agent", "1", target.targetId, listOf(target))))
                handle()
            }, toolRunner = AiPluginToolRunner { _, _, callback ->
                check(nativeTools)
                calls.incrementAndGet()
                callback(AiPluginStreamEvent.ToolCalls(listOf(AiPluginToolCall("native-call", "ui_read", "{}"))))
                object : AiPluginAskHandle {
                    override fun cancel() = true
                    override fun close() = Unit
                    override fun submitToolResults(results: List<AiPluginToolResult>): Boolean {
                        calls.incrementAndGet()
                        callback(AiPluginStreamEvent.Completed("native-result:${results.single().output}", "",
                            AiPluginUsage(null, null, 2, null), 0, target))
                        return true
                    }
                }
            })
        cleanup += { broker.close() }
        return ModelFixture(broker, calls, catalogs)
    }
    private fun generate(fixture: ModelFixture, id: String, content: String = "a", maximumOutputTokens: Int = 1): JSONObject {
        val events = CopyOnWriteArrayList<JSONObject>()
        val done = CountDownLatch(1)
        val request = JSONObject().put("requestId", id).put("targetId", "local:fixture")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("maximumOutputTokens", maximumOutputTokens)
        driver.model(fixture.broker, "generate", Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(C.KEY_MODEL_REQUEST_JSON, request.toString())
        }, object : IThreeStoveAgentModelCallback.Stub() {
            override fun onEvent(event: Bundle) {
                assertEquals(fixtureUid, Binder.getCallingUid())
                val value = JSONObject(event.getString(C.KEY_MODEL_EVENT_JSON)!!)
                events += value
                if (value.getString("type") in setOf("failed", "completed", "cancelled")) done.countDown()
            }
        })
        assertTrue("Model broker response timed out", done.await(10, TimeUnit.SECONDS))
        assertEquals("started", events.first().getString("type"))
        assertEquals((1..events.size).toList(), events.map { it.getInt("sequence") })
        return events.single { it.getString("type") in setOf("failed", "completed", "cancelled") }
    }
    private fun hostLogs(): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
        .executeShellCommand("logcat -d --pid=${Process.myPid()} -v brief")).bufferedReader().use { it.readText() }

    companion object { private const val FIXTURE = "org.autojs.plugin.three.stove.agent.fake" }
}
