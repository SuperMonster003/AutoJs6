package org.autojs.autojs.core.plugin.agent

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLinkCallback
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentRunCallback
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Android Bundle/Binder lifecycle with an injected local link, not a plugin APK fixture.
 * Exercises UID checks, generation fencing, lease ownership and revocation without starting tasks.
 */
class ThreeStoveAgentLinkControllerTest {
    @Test
    fun attachPinsBothBrokersAndDetachRevokesThemBeforeReleasingLease() = withFixture { fixture ->
        fixture.controller.attach()
        assertEquals("attached", fixture.controller.status.value.state)
        assertEquals(1, fixture.calls.size)
        val call = fixture.calls.single()
        assertEquals(C.CONTRACT_VERSION, call.config.getInt(C.KEY_CONTRACT_VERSION))
        assertTrue(modelAvailable(call.brokers))

        fixture.controller.detach()
        assertEquals("detached", fixture.controller.status.value.state)
        assertEquals(1, call.link.detachCount)
        assertEquals(1, call.closed.get())
        assertFalse(modelAvailable(call.brokers))
        assertCapabilityRevoked(call.brokers)
        assertEquals(listOf(C.ERROR_LINK_DETACHED), fixture.lost)
        fixture.controller.detach()
        assertEquals("closing twice never releases the same lease twice", 1, call.closed.get())
    }

    @Test
    fun authorizedCallbacksPublishValidatedSnapshotsAndRejectMalformedInput() = withFixture { fixture ->
        fixture.controller.attach()
        val callback = fixture.calls.single().callback
        callback.onStatus(status("attached", queued = 3))
        withTimeout(2_000) { fixture.controller.status.first { it.queuedCount == 3 } }
        callback.onStatus(status("attached", queued = C.MAX_RUN_QUEUE + 1))
        callback.onStatus(status("attached").apply { putInt(C.KEY_CONTRACT_VERSION, 99) })
        callback.onStatus(status("made-up"))
        callback.onStatus(status("attached").apply { putString(C.KEY_STATUS_JSON, "[".repeat(C.MAX_EVENT_JSON_BYTES + 1)) })
        assertEquals(3, fixture.controller.status.value.queuedCount)

        callback.onEvent(event("queue_changed"))
        withTimeout(2_000) { while (fixture.events.isEmpty()) delay(1) }
        callback.onEvent(event("unexpected"))
        callback.onEvent(event("preset_changed").apply { putInt(C.KEY_CONTRACT_VERSION, 99) })
        assertEquals(1, fixture.events.size)
    }

    @Test
    fun callbacksFromADifferentUidCannotAlterLinkOrEmitEvents() =
        withFixture(expectedUid = Process.myUid() + 1) { fixture ->
            fixture.controller.attach()
            val callback = fixture.calls.single().callback
            callback.onStatus(status("failed"))
            callback.onEvent(event("queue_changed"))
            assertEquals("attached", fixture.controller.status.value.state)
            assertTrue(fixture.events.isEmpty())
            assertTrue(fixture.lost.isEmpty())
        }

    @Test
    fun rejectedCallbackDescriptorsAreClosedEvenForAnUnauthorizedUid() =
        withFixture(expectedUid = Process.myUid() + 1) { fixture ->
            fixture.controller.attach()
            val callback = fixture.calls.single().callback
            for (key in listOf(C.KEY_PAYLOAD_FD, "unexpected", C.KEY_STATUS_JSON, C.KEY_RUN_EVENT_JSON)) {
                for (statusCallback in listOf(true, false)) {
                    val pipe = ParcelFileDescriptor.createPipe()
                    try {
                        val descriptor = pipe[0].fileDescriptor
                        if (statusCallback) callback.onStatus(status("failed").apply { putParcelable(key, pipe[0]) })
                        else callback.onEvent(event("queue_changed").apply { putParcelable(key, pipe[0]) })
                        assertFalse("a rejected callback must release its transferred descriptor", descriptor.valid())
                        assertEquals("attached", fixture.controller.status.value.state)
                    } finally {
                        pipe.forEach { runCatching { it.close() } }
                    }
                }
            }
        }

    @Test
    fun callbacksFromAnOldGenerationCannotDetachTheReplacementLink() = withFixture { fixture ->
        fixture.controller.attach()
        val first = fixture.calls.single()
        fixture.controller.detach()
        fixture.controller.attach()
        val replacement = fixture.calls.last()
        assertNotSame(first.link, replacement.link)
        val lostBefore = fixture.lost.size

        first.callback.onStatus(status("failed"))
        first.callback.onEvent(event("queue_changed"))
        first.link.binder.deliverDeath()
        assertEquals("attached", fixture.controller.status.value.state)
        assertTrue(modelAvailable(replacement.brokers))
        assertEquals(lostBefore, fixture.lost.size)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun attachFailureRevokesCreatedBrokersAndDoesNotRetry() = withFixture(failAttach = true) { fixture ->
        val failure = runCatching { fixture.controller.attach() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("failed", fixture.controller.status.value.state)
        assertEquals(1, fixture.calls.size)
        assertFalse(modelAvailable(fixture.calls.single().brokers))
        assertCapabilityRevoked(fixture.calls.single().brokers)
        delay(1_100)
        assertEquals("an ambiguous attach must never retry", 1, fixture.calls.size)
    }

    @Test
    fun hostUnavailableRevokesTheLinkAndNotifiesObservers() = withFixture { fixture ->
        fixture.controller.attach()
        val call = fixture.calls.single()
        call.callback.onStatus(status("host_unavailable"))
        withTimeout(2_000) { while (call.closed.get() == 0) delay(1) }
        assertEquals("host_unavailable", fixture.controller.status.value.state)
        assertFalse(modelAvailable(call.brokers))
        assertCapabilityRevoked(call.brokers)
        assertEquals(listOf(C.ERROR_HOST_UNAVAILABLE), fixture.lost)
        assertTrue(runCatching { fixture.controller.withLink { it.getStatus() } }.isFailure)
    }

    @Test
    fun processDeathRevokesOldBrokersAndRebindsWithoutReplayingTasks() = withFixture { fixture ->
        fixture.controller.attach()
        val first = fixture.calls.single()
        first.link.binder.deliverDeath()
        assertEquals("failed", fixture.controller.status.value.state)
        assertEquals(1, first.closed.get())
        assertFalse(modelAvailable(first.brokers))
        assertCapabilityRevoked(first.brokers)
        assertEquals(listOf(C.ERROR_PLUGIN_UNAVAILABLE), fixture.lost)
        withTimeout(4_000) {
            while (fixture.calls.size < 2) delay(10)
            fixture.controller.status.first { it.state == "attached" }
        }
        assertEquals(2, fixture.calls.size)
        assertEquals(0, fixture.calls.sumOf { it.link.startRunCount })
        assertTrue(modelAvailable(fixture.calls.last().brokers))
    }

    @Test
    fun rootSettingsPreserveHostRestrictionsAndRejectedPluginUpdatesAreNotCommitted() = withFixture { fixture ->
        val root = java.io.File(android.os.Environment.getExternalStorageDirectory(), "AgentP31Controller-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        try {
            fixture.controller.attach("""{"locale":"zh","grantSummary":{"toolGroups":["script"],"methods":["agent.listScripts"]}}""")
            val link = fixture.calls.single().link
            val config = org.json.JSONObject().put("scriptRoots", org.json.JSONArray().put(root.path)).toString()
            fixture.controller.updateScriptRoots(config)
            val accepted = JsonParser.parseString(link.configurations.single()).asJsonObject
            assertEquals("zh", accepted.get("locale").asString)
            assertEquals("[\"script\"]", accepted.getAsJsonObject("grantSummary").get("toolGroups").toString())
            assertEquals("[\"agent.listScripts\"]", accepted.getAsJsonObject("grantSummary").get("methods").toString())
            link.failUpdate = true
            assertTrue(runCatching { fixture.controller.updateScriptRoots("{\"scriptRoots\":[]}") }.isFailure)
            link.failUpdate = false
            fixture.controller.updateScriptRoots(config)
            assertEquals("The unchanged accepted roots remain the controller's source of truth", 1, link.configurations.size)
            fixture.controller.updateScriptRoots("{\"scriptRoots\":[]}")
            assertEquals(2, link.configurations.size)
        } finally { root.deleteRecursively() }
    }

    private fun withFixture(
        expectedUid: Int = Process.myUid(),
        failAttach: Boolean = false,
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val fixture = Fixture(expectedUid, failAttach)
        try {
            block(fixture)
        } finally {
            fixture.controller.detach()
            fixture.scope.cancel()
        }
    }

    private class Fixture(expectedUid: Int, failAttach: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = CopyOnWriteArrayList<Call>()
        val lost = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<Bundle>()
        val controller = ThreeStoveAgentLinkController(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            scope = scope,
            listener = object : ThreeStoveAgentLinkController.Listener {
                override fun onLinkLost(errorCode: String) { lost += errorCode }
                override fun onEvent(event: Bundle) { events += event }
            },
            available = { true },
            uid = { expectedUid },
            attachLink = { config, brokers, callback ->
                val call = Call(config, brokers, callback)
                calls += call
                check(!failAttach) { "fixture attach failure" }
                AidlPluginHost.LeasedCall(call.link, org.autojs.autojs.core.plugin.ServiceBindingLease { call.closed.incrementAndGet() }.apply { markRegistered() })
            },
        )
    }

    private class Call(val config: Bundle, val brokers: ThreeStoveAgentLinkBrokers, val callback: IThreeStoveAgentLinkCallback) {
        val link = FakeLink()
        val closed = AtomicInteger()
    }

    private class FakeLink : IThreeStoveAgentLink {
        val binder = DeathBinder()
        var detachCount = 0
        var startRunCount = 0
        var failUpdate = false
        val configurations = mutableListOf<String>()
        override fun asBinder(): IBinder = binder
        override fun getStatus() = Bundle()
        override fun startRun(request: Bundle?, callback: IThreeStoveAgentRunCallback?): Bundle {
            startRunCount++
            return Bundle()
        }
        override fun respond(response: Bundle?) = Bundle()
        override fun cancelRun(ref: Bundle?) = Unit
        override fun listRuns(query: Bundle?) = Bundle()
        override fun getRun(ref: Bundle?) = Bundle()
        override fun listPresets(query: Bundle?) = Bundle()
        override fun updateConfig(config: Bundle?) {
            check(!failUpdate) { "Fixture rejects the configuration" }
            configurations.add(config!!.getString(C.KEY_LINK_CONFIG_JSON)!!)
        }
        override fun detach(reason: Bundle?) { detachCount++ }
    }

    /** Local Binder plus an explicit death signal; no process termination or remote fixture required. */
    private class DeathBinder(private val local: Binder = Binder()) : IBinder by local {
        private val recipients = CopyOnWriteArrayList<IBinder.DeathRecipient>()
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { recipients += recipient }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = recipients.remove(recipient)
        fun deliverDeath() { recipients.toList().forEach { it.binderDied() } }
    }

    private companion object {
        fun status(state: String, queued: Int = 0) = Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(C.KEY_STATUS_JSON, """{"state":"$state","queuedCount":$queued}""")
        }

        fun event(type: String) = Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(C.KEY_RUN_EVENT_JSON, """{"type":"$type"}""")
        }

        fun modelAvailable(brokers: ThreeStoveAgentLinkBrokers): Boolean = JsonParser.parseString(
            brokers.modelBroker.getBrokerInfo().getString(C.KEY_MODEL_BROKER_INFO_JSON),
        ).asJsonObject.get("available").asBoolean

        fun assertCapabilityRevoked(brokers: ThreeStoveAgentLinkBrokers) {
            var response: Bundle? = null
            brokers.capabilityBroker.dispatch(Bundle().apply {
                putString(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON,
                    """{"id":"revocation-probe","module":"device","method":"info","args":{}}""")
            }, object : IHostCapabilityCallback.Stub() {
                override fun onResponse(value: Bundle?) { response = value }
            })
            val json = JsonParser.parseString(checkNotNull(response).getString(HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON)).asJsonObject
            assertFalse(json.get("ok").asBoolean)
            assertEquals("process-dead", json.getAsJsonObject("error").get("category").asString)
        }
    }
}
