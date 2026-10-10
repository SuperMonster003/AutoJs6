package org.autojs.autojs.core.plugin.agent

import android.content.*
import android.os.*
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.three.stove.agent.api.*
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.fake.IBrokerConformanceDriver
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Production controller/registry against a separate UID's deliberately faulty Agent Binder. */
class ThreeStoveAgentPeerConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixturePackage = "org.autojs.plugin.three.stove.agent.fake"
    private fun envelope(key: String, json: String) = Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, json) }
    private fun status(queued: Int = 0) = envelope(C.KEY_STATUS_JSON, """{"state":"attached","queuedCount":$queued}""")
    private fun event(type: String, sequence: Long, id: String, extra: String = "") = envelope(C.KEY_RUN_EVENT_JSON,
        """{"runId":"$id","type":"$type","sequence":$sequence$extra}""")
    private suspend fun await(message: String, check: () -> Boolean) {
        withTimeout(10_000) { while (!check()) delay(20) }
        assertTrue(message, check())
    }
    private inner class Binding : Closeable {
        val value = AtomicReference<IBrokerConformanceDriver?>()
        private val connected = CountDownLatch(1)
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                assertNull(service.queryLocalInterface("org.autojs.plugin.three.stove.agent.fake.IBrokerConformanceDriver"))
                value.set(IBrokerConformanceDriver.Stub.asInterface(service)); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { value.set(null) }
        }
        init {
            assertTrue(context.bindService(Intent().setComponent(ComponentName(fixturePackage, "$fixturePackage.BrokerConformanceService")), connection, Context.BIND_AUTO_CREATE))
            assertTrue(connected.await(10, TimeUnit.SECONDS))
        }
        override fun close() { context.unbindService(connection) }
    }
    private inner class Fixture : Closeable {
        val control = Binding()
        val driver get() = checkNotNull(control.value.get())
        val uid = driver.identity.getInt("uid")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val losses = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<Bundle>()
        val brokers = CopyOnWriteArrayList<ThreeStoveAgentLinkBrokers>()
        val released = CopyOnWriteArrayList<Int>()
        val controller = ThreeStoveAgentLinkController(context, scope,
            listener = object : ThreeStoveAgentLinkController.Listener {
                override fun onLinkLost(errorCode: String) { losses += errorCode }
                override fun onEvent(event: Bundle) { events += event }
            }, available = { true }, uid = { uid }, attachLink = { config, owned, callback ->
                val binding = Binding()
                try {
                    val api = IThreeStoveAgentPlugin.Stub.asInterface(checkNotNull(binding.value.get()).plugin)
                    assertNull(api.asBinder().queryLocalInterface("org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentPlugin"))
                    val index = brokers.size; brokers += owned
                    AidlPluginHost.LeasedCall(api.attach(config, owned.modelBroker, owned.capabilityBroker, callback), org.autojs.autojs.core.plugin.ServiceBindingLease { released += index; binding.close() }.apply { markRegistered() })
                } catch (failure: Throwable) { binding.close(); throw failure }
            })
        fun send(operation: String, generation: Int = 0, data: Bundle = Bundle()) = driver.control(operation, generation, data)
        fun emit(id: String, value: Bundle, generation: Int = 0) { send("run", generation, value.apply { putString("fixture.runId", id) }) }
        fun start() = controller.scriptRuns.start("Conformance echo \u4e2d\u6587", JsonObject().apply { addProperty("interaction", "script") })
        override fun close() {
            runBlocking { controller.detach() }
            scope.cancel(); control.close()
        }
    }
    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        assumeTrue("Enable autojs.threeStoveAgent.peer.conformance.enabled", InstrumentationRegistry.getArguments().getString("autojs.threeStoveAgent.peer.conformance.enabled") == "true")
        Fixture().use { fixture ->
            assertNotEquals(Process.myUid(), fixture.uid)
            assertNotEquals(Process.myPid(), fixture.driver.identity.getInt("pid"))
            block(fixture)
        }
    }

    @Test fun earlyEchoCallbackSurvivesAdmissionAndDetachRevokesCapabilityGrant() = withFixture { f ->
        f.controller.attach("""{"fixtureMode":"echo"}""")
        val run = f.start()
        await("Early event reaches the host handle") { run.settled }
        assertEquals("completed", run.state); assertEquals(run.goal, run.result!!["summary"].asString)
        assertEquals(1, f.send("stats").getInt("starts"))
        f.controller.detach()
        assertEquals("detached", f.controller.status.value.state)
        assertEquals(listOf(0), f.released); assertEquals(1, f.send("stats").getInt("detaches"))
        val reply = AtomicReference<Bundle>(); val received = CountDownLatch(1)
        f.driver.dispatch(f.brokers.single().capabilityBroker, Bundle().apply {
            putString(H.KEY_BRIDGE_REQUEST_JSON, """{"id":"revoked","module":"device","method":"info","args":{}}""")
        }, object : IHostCapabilityCallback.Stub() {
            override fun onResponse(response: Bundle) { assertEquals(f.uid, Binder.getCallingUid()); reply.set(response); received.countDown() }
        })
        assertTrue(received.await(5, TimeUnit.SECONDS)); assertFalse(reply.get().getBoolean(H.KEY_BRIDGE_RESPONSE_OK))
        assertTrue(reply.get().toString(), reply.get().getString(H.KEY_BRIDGE_RESPONSE_JSON)!!.contains("ERR_AUTOJS6_BRIDGE_PROCESS_DEAD"))
    }

    @Test fun hostileLinkCallbacksAndRetiredGenerationCannotChangeCurrentAttachment() = withFixture { f ->
        f.controller.attach()
        listOf(status(C.MAX_RUN_QUEUE + 1), status().apply { putInt(C.KEY_CONTRACT_VERSION, 99) },
            envelope(C.KEY_STATUS_JSON, """{"state":"invented"}"""),
            envelope(C.KEY_STATUS_JSON, "x".repeat(C.MAX_EVENT_JSON_BYTES + 1)),
            Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putInt(C.KEY_STATUS_JSON, 7) }).forEach { f.send("status", data = it) }
        f.send("event", data = envelope(C.KEY_RUN_EVENT_JSON, """{"type":"unexpected"}"""))
        f.send("status", data = status(2)); await("Valid callback still works") { f.controller.status.value.queuedCount == 2 }
        assertTrue(f.events.isEmpty()); assertTrue(f.losses.isEmpty())
        f.controller.detach(); f.controller.attach()
        val oldLosses = f.losses.size
        f.send("status", data = envelope(C.KEY_STATUS_JSON, """{"state":"failed"}"""))
        f.send("event", data = envelope(C.KEY_RUN_EVENT_JSON, """{"type":"queue_changed","marker":"old"}"""))
        f.send("event", 1, envelope(C.KEY_RUN_EVENT_JSON, """{"type":"queue_changed","marker":"new"}"""))
        await("Current generation event") { f.events.isNotEmpty() }
        assertEquals(1, f.events.size); assertTrue(f.events.single().getString(C.KEY_RUN_EVENT_JSON)!!.contains("new"))
        assertEquals(oldLosses, f.losses.size); assertEquals("attached", f.controller.status.value.state)
    }

    @Test fun runCallbacksFenceIdsSequenceDuplicatesAndTerminalState() = withFixture { f ->
        f.controller.attach(); val run = f.start(); val observed = CopyOnWriteArrayList<JsonObject>()
        run.observe { observed += it }.use {
            f.emit(run.id, event("done", 999, "wrong-run", ",\"status\":\"completed\""))
            f.emit(run.id, event("progress", 2, run.id, ",\"message\":\"accepted\""))
            f.emit(run.id, event("progress", 1, run.id, ",\"message\":\"stale\""))
            f.emit(run.id, event("progress", 2, run.id, ",\"message\":\"duplicate\""))
            f.emit(run.id, event("done", 3, run.id, ",\"status\":\"completed\",\"summary\":\"finished\""))
            await("Valid terminal event") { run.settled }
            f.emit(run.id, event("done", 4, run.id, ",\"status\":\"failed\""))
            f.send("status", data = status(1)); await("Drain peer callbacks") { f.controller.status.value.queuedCount == 1 }
            assertEquals("completed", run.state); assertEquals("finished", run.result!!["summary"].asString)
            assertEquals(listOf("progress", "done"), observed.map { it["type"].asString })
        }
    }

    @Test fun malformedRunCallbacksFailOnlyTheirOwnHandle() = withFixture { f ->
        f.controller.attach()
        for (malformed in listOf("not-json", "x".repeat(C.MAX_EVENT_JSON_BYTES + 1), "{\"type\":\"invented\",\"sequence\":1,\"runId\":\"REPLACE\"}")) {
            val run = f.start()
            f.emit(run.id, envelope(C.KEY_RUN_EVENT_JSON, malformed.replace("REPLACE", run.id)))
            await("Invalid event fails handle") { run.settled }
            assertEquals(C.ERROR_INVALID_REQUEST, run.failure!!["code"].asString)
            assertEquals("attached", f.controller.status.value.state)
        }
        val valid = f.start(); f.emit(valid.id, event("done", 1, valid.id, ",\"status\":\"completed\""))
        await("Next handle still works") { valid.settled }; assertEquals("completed", valid.state)
    }

    @Test fun rejectedTransferredDescriptorsAreClosedOnLinkAndRunCallbacks() = withFixture { f ->
        f.controller.attach(); val run = f.start()
        for (route in listOf("status", "event", "run")) {
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val data = if (route == "status") status(3) else event("progress", 1, run.id)
                data.putParcelable(C.KEY_PAYLOAD_FD, pipe[0])
                if (route == "run") f.emit(run.id, data) else f.send(route, data = data)
                pipe[0].close()
                await("Receiver closes rejected descriptor: $route") {
                    try { Os.write(pipe[1].fileDescriptor, byteArrayOf(1), 0, 1); false }
                    catch (error: ErrnoException) { error.errno == OsConstants.EPIPE }
                }
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
        assertFalse(run.settled); assertEquals(0, f.controller.status.value.queuedCount)
        f.emit(run.id, event("done", 1, run.id, ",\"status\":\"completed\""))
        await("Valid event after FD rejection") { run.settled }; assertEquals("completed", run.state)
    }

    @Test fun actualPeerDeathFailsHandlesAndRebindsWithoutStartingATask() = withFixture { f ->
        assertTrue("Isolated AVD only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        f.controller.attach(); val run = f.start(); val oldPid = f.driver.identity.getInt("pid")
        f.send("kill")
        await("Handle failed after real Binder death") { run.settled }
        assertEquals(C.ERROR_PLUGIN_UNAVAILABLE, run.failure!!["code"].asString)
        await("Fresh attachment") { f.brokers.size == 2 && f.controller.status.value.state == "attached" && f.control.value.get() != null }
        assertNotEquals(oldPid, f.driver.identity.getInt("pid")); assertEquals(listOf(0), f.released)
        assertEquals(0, f.send("stats").getInt("starts"))
        val fresh = f.start(); f.emit(fresh.id, event("done", 1, fresh.id, ",\"status\":\"completed\""))
        await("Explicit new task") { fresh.settled }; assertEquals("completed", fresh.state)
    }
}
