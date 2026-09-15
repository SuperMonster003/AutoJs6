package org.autojs.plugin.mcp.server.fake.service

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.fake.FakeMcpCallbackPlan
import org.autojs.plugin.mcp.server.fake.FakeMcpServerFixture
import org.autojs.plugin.mcp.server.fake.FakeMcpServerScenario
import org.autojs.plugin.mcp.server.fake.FakeMcpSessionMachine
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One server session of the fixture. Startup runs on the worker: read the broker info, send one
 * `device.info` request through the host broker (the production plugin does the same), then
 * report `running` as many times as the scenario plans. Callbacks go through the serial lane.
 */
internal class RemoteFakeMcpServerSession(
    private val ownerUid: Int,
    port: Int,
    private val bindScope: String,
    private val protocolMode: String,
    private val broker: IMcpHostCapabilityBroker,
    private val callback: IMcpServerCallback,
    private val scenario: FakeMcpServerScenario,
    private val verifier: HostCallerVerifier,
    private val worker: ExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteFakeMcpServerSession) -> Unit,
) : IMcpServerSession.Stub() {

    private val plan = FakeMcpCallbackPlan.forScenario(scenario)
    private val machine = FakeMcpSessionMachine(port)
    private val lock = Any()
    private val statusCallbacks = AtomicInteger(0)
    private val eventCallbacks = AtomicInteger(0)

    @Volatile
    private var brokerInfo: Bundle? = null

    @Volatile
    private var probeResponseJson: String? = null

    @Volatile
    private var oversizedResponseJson: String? = null

    fun start() {
        worker.execute { runStartup() }
    }

    /** The service is going away; no callbacks are sent for that. */
    fun serviceDestroyed() {
        synchronized(lock) { machine.close() }
    }

    override fun getStatus(): Bundle {
        verifier.enforceSessionOwner(ownerUid)
        return snapshot()
    }

    override fun updateConfig(config: Bundle?) {
        verifier.enforceSessionOwner(ownerUid)
        val port = FakeMcpBundles.validatedPort(config)
        val running = synchronized(lock) {
            machine.updatePort(port)
            machine.state == FakeMcpSessionMachine.State.RUNNING
        }
        if (running) repeat(plan.runningStatusCallbacks) { emitStatus("update") }
    }

    override fun stop(reason: Bundle?) {
        verifier.enforceSessionOwner(ownerUid)
        val stopped = synchronized(lock) { machine.stop(reason?.getString(McpServerContract.KEY_ERROR_CODE)) }
        if (stopped) repeat(plan.stoppedStatusCallbacks) { emitStatus("stop") }
    }

    override fun close() {
        verifier.enforceSessionOwner(ownerUid)
        val (wasRunning, closed) = synchronized(lock) {
            val running = machine.state == FakeMcpSessionMachine.State.RUNNING
            running to machine.close()
        }
        if (!closed) return
        if (wasRunning) repeat(plan.stoppedStatusCallbacks) { emitStatus("close") }
        // Hostile by design: a plugin that keeps talking after the host released the session.
        repeat(plan.statusCallbacksAfterClose) { emitStatus("after-close") }
        onFinished(this)
    }

    private fun runStartup() {
        brokerInfo = runCatching { broker.getBrokerInfo() }.getOrNull()
        probeResponseJson = dispatchAndAwait(FakeMcpServerFixture.probeRequestJson())
        if (plan.oversizedBrokerRequest) {
            oversizedResponseJson = dispatchAndAwait(FakeMcpServerFixture.oversizedRequestJson())
        }
        val running = synchronized(lock) { machine.markRunning(System.currentTimeMillis()) }
        if (!running) return
        repeat(plan.runningStatusCallbacks) { emitStatus("running") }
        repeat(plan.eventCallbacks) {
            emitEvent(McpServerContract.EVENT_WARNING, FakeMcpServerFixture.EVENT_TEXT_PREFIX + scenario.metadataValue)
        }
        if (plan.killProcessAfterRunning) {
            // Queued behind the callbacks above, so the host has seen `running` before the death.
            callbackLane.dispatch("kill") {
                Thread.sleep(FakeMcpServerFixture.DEATH_DELAY_MS)
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun dispatchAndAwait(requestJson: String): String? {
        val latch = CountDownLatch(1)
        val holder = AtomicReference<String?>(null)
        val responseCallback = object : IMcpHostCapabilityCallback.Stub() {
            @Suppress("DEPRECATION")
            override fun onResponse(response: Bundle?) {
                // A payload descriptor is never expected for device.info; close one if the host sent it.
                runCatching { response?.getParcelable<ParcelFileDescriptor>(McpServerContract.KEY_BRIDGE_PAYLOAD_FD)?.close() }
                holder.set(summarize(response?.getString(McpServerContract.KEY_BRIDGE_RESPONSE_JSON)))
                latch.countDown()
            }
        }
        runCatching { broker.dispatch(FakeMcpBundles.bridgeRequest(requestJson), responseCallback) }
            .onFailure { error ->
                holder.set("{\"fixtureDispatchFailure\":\"${error.javaClass.simpleName}\"}")
                latch.countDown()
            }
        latch.await(FakeMcpServerFixture.PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return holder.get()
    }

    /** Keeps only id, ok, error category and message of a broker response, so the status stays small. */
    private fun summarize(responseJson: String?): String {
        if (responseJson == null) return "{\"fixtureResponse\":\"missing\"}"
        return runCatching {
            val value = JSONObject(responseJson)
            JSONObject().apply {
                put("id", value.optString("id"))
                put("ok", value.optBoolean("ok", false))
                put("bytes", responseJson.length)
                value.optJSONObject("error")?.let { error ->
                    put("category", error.optString("category"))
                    put("message", error.optString("message").take(MAX_SUMMARY_MESSAGE_CHARS))
                }
            }.toString()
        }.getOrElse { "{\"fixtureParseFailure\":true,\"bytes\":${responseJson.length}}" }
    }

    private fun snapshot(): Bundle = synchronized(lock) {
        FakeMcpBundles.status(
            scenario = scenario,
            machine = machine,
            plan = plan,
            bindScope = bindScope,
            protocolMode = protocolMode,
            observed = FakeMcpBundles.Observed(
                statusCallbacks = statusCallbacks.get(),
                eventCallbacks = eventCallbacks.get(),
                brokerInfoReceived = brokerInfo != null,
                brokerMaxConcurrentCalls = brokerInfo?.getInt(McpServerContract.KEY_GRANT_MAX_CONCURRENT_CALLS, 0) ?: 0,
                probeResponseJson = probeResponseJson,
                oversizedResponseJson = oversizedResponseJson,
            ),
        )
    }

    private fun emitStatus(label: String) {
        val bundle = snapshot()
        callbackLane.dispatch("status:$label") {
            callback.onStatus(bundle)
            statusCallbacks.incrementAndGet()
        }
    }

    private fun emitEvent(type: String, text: String) {
        val bundle = FakeMcpBundles.event(type, text)
        callbackLane.dispatch("event:$type") {
            callback.onEvent(bundle)
            eventCallbacks.incrementAndGet()
        }
    }

    private companion object {
        const val MAX_SUMMARY_MESSAGE_CHARS = 256
    }
}
