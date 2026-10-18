package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLinkCallback
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import java.util.UUID

data class ThreeStoveAgentLinkStatus(
    val state: String = "detached",
    val runningRunId: String? = null,
    val queuedCount: Int = 0,
    val errorCode: String? = null,
)

/** Owns leases and both brokers. Reattachment restores a link only; it never replays a run. */
class ThreeStoveAgentLinkController(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val listener: Listener = object : Listener {},
    private val available: () -> Boolean = {
        ThreeStoveAgentPluginInspector.inspect(context.applicationContext).state == ThreeStoveAgentUiState.AVAILABLE
    },
    private val uid: () -> Int = { context.packageManager.getApplicationInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, 0).uid },
    private val brokerFactory: (String, Int) -> ThreeStoveAgentLinkBrokers = { id, pluginUid ->
        ThreeStoveAgentLinkBrokers(context.applicationContext, id, pluginUid)
    },
    private val attachLink: suspend (Bundle, ThreeStoveAgentLinkBrokers, IThreeStoveAgentLinkCallback) -> AidlPluginHost.LeasedCall<IThreeStoveAgentLink> = { config, brokers, callback ->
        ThreeStoveAgentPluginHost.attach(context.applicationContext, config, brokers, callback)
    },
    private val connectOnDemand: (suspend () -> ThreeStoveAgentUiState)? = null,
) {
    interface Listener {
        fun onStatus(status: ThreeStoveAgentLinkStatus) {}
        fun onEvent(event: Bundle) {}
        /** Consumers must fail all handles of this generation; a later attach cannot resume them. */
        fun onLinkLost(errorCode: String) {}
    }

    private data class Active(
        val generation: Long,
        val lease: AidlPluginHost.LeasedCall<IThreeStoveAgentLink>,
        val brokers: ThreeStoveAgentLinkBrokers,
        val death: IBinder.DeathRecipient,
    )

    private val mutex = Mutex()
    private val policy = ThreeStoveAgentLinkPolicy()
    private val mutableStatus = MutableStateFlow(ThreeStoveAgentLinkStatus())
    private var active: Active? = null
    private var rebindJob: Job? = null
    private var desiredConfig = "{}"
    val status = mutableStatus.asStateFlow()
    private val scriptListeners = java.util.concurrent.CopyOnWriteArraySet<(String) -> Unit>()
    internal val scriptRuns by lazy { ThreeStoveAgentRunRegistry(this, uid) }

    internal fun observeLoss(listener: (String) -> Unit): java.io.Closeable {
        scriptListeners.add(listener)
        return java.io.Closeable { scriptListeners.remove(listener) }
    }

    private fun notifyLoss(code: String) {
        runCatching { listener.onLinkLost(code) }
        scriptListeners.forEach { runCatching { it(code) } }
    }

    /** Broadcast delivery only schedules work; binding never blocks the receiver's main thread. */
    fun requestAttach() {
        scope.launch { runCatching { attach() } }
    }

    /** ATTACHED, or why the plugin cannot be used now (DETACHED when this caller may not bind). */
    internal suspend fun ensureConnected(allowConnect: Boolean = true): ThreeStoveAgentUiState = when {
        status.value.state == "attached" -> if (available()) ThreeStoveAgentUiState.ATTACHED else ThreeStoveAgentUiState.FAILED
        !allowConnect -> ThreeStoveAgentUiState.DETACHED
        else -> connectOnDemand?.invoke() ?: ThreeStoveAgentUiState.DETACHED
    }

    suspend fun attach(configJson: String = "{}"): Unit = mutex.withLock {
        validateConfig(configJson)
        check(active == null) { "3-Stove Agent is already attached" }
        check(available()) { C.ERROR_PLUGIN_UNAVAILABLE }
        rebindJob?.cancel()
        desiredConfig = configJson
        val generation = policy.attach()
        attachLocked(generation)
    }

    suspend fun detach(code: String = "user-request"): Unit = mutex.withLock {
        policy.detach()
        rebindJob?.cancel()
        rebindJob = null
        val current = active
        active = null
        current?.let {
            // Revoke first so a faulty/slow plugin cannot keep using its grant during detach.
            it.brokers.destroy(reason(code))
            runCatching { it.lease.value.detach(reason(code)) }
            release(it, code)
        }
        publish(ThreeStoveAgentLinkStatus())
        notifyLoss(C.ERROR_LINK_DETACHED)
    }

    suspend fun updateConfig(configJson: String): Unit = mutex.withLock {
        updateConfigLocked(configJson)
    }

    /** Preserve the host's grant and locale when applying the plugin's root-only settings. */
    suspend fun updateScriptRoots(configJson: String): Unit = mutex.withLock {
        val roots = JsonParser.parseString(ThreeStoveAgentScriptRoots.configuration(configJson)).asJsonObject.get("scriptRoots")
        val next = JsonParser.parseString(desiredConfig).asJsonObject.apply { add("scriptRoots", roots) }
        val previous = JsonParser.parseString(desiredConfig).asJsonObject.get("scriptRoots") ?: com.google.gson.JsonArray()
        if (previous == roots) return@withLock
        updateConfigLocked(next.toString())
    }

    private fun updateConfigLocked(configJson: String) {
        validateConfig(configJson)
        val current = checkNotNull(active) { C.ERROR_LINK_DETACHED }
        val commitRoots = current.brokers.prepareConfig(configJson)
        current.lease.value.updateConfig(config(configJson))
        commitRoots()
        desiredConfig = configJson
    }

    /** Only the current lease can be used; task ownership and callback lifetimes are added in P5. */
    suspend fun <T> withLink(block: (IThreeStoveAgentLink) -> T): T = mutex.withLock {
        block(checkNotNull(active) { C.ERROR_LINK_DETACHED }.lease.value)
    }

    private suspend fun attachLocked(generation: Long) {
        publish(ThreeStoveAgentLinkStatus("attaching"))
        var brokers: ThreeStoveAgentLinkBrokers? = null
        var leased: AidlPluginHost.LeasedCall<IThreeStoveAgentLink>? = null
        try {
            check(available()) { C.ERROR_PLUGIN_UNAVAILABLE }
            val pluginUid = uid()
            brokers = brokerFactory("agent-${UUID.randomUUID()}", pluginUid)
            brokers.prepareConfig(desiredConfig)()
            leased = attachLink(config(desiredConfig), brokers, LinkCallback(generation, pluginUid))
            val death = IBinder.DeathRecipient { scope.launch { died(generation) } }
            leased.value.asBinder().linkToDeath(death, 0)
            active = Active(generation, leased, brokers, death)
            publish(ThreeStoveAgentLinkStatus("attached"))
        } catch (error: Throwable) {
            brokers?.destroy(reason("attach-failed"))
            runCatching { leased?.bindingLease?.close() }
            policy.failed(generation)
            publish(ThreeStoveAgentLinkStatus("failed", errorCode = C.ERROR_PLUGIN_UNAVAILABLE))
            // Deliberately no retry: the plugin may have accepted attach before the transport failed.
            throw error
        }
    }

    private suspend fun died(generation: Long): Unit = mutex.withLock {
        val current = active?.takeIf { it.generation == generation } ?: return@withLock
        active = null
        release(current, "plugin-died")
        notifyLoss(C.ERROR_PLUGIN_UNAVAILABLE)
        val delayMs = policy.died(generation, runCatching { available() }.getOrDefault(false))
        publish(ThreeStoveAgentLinkStatus("failed", errorCode = C.ERROR_PLUGIN_UNAVAILABLE))
        if (delayMs != null) {
            val nextGeneration = policy.generation
            rebindJob = scope.launch {
                delay(delayMs)
                mutex.withLock {
                    if (policy.accepts(nextGeneration) && active == null) {
                        runCatching { attachLocked(nextGeneration) }
                    }
                }
            }
        }
    }

    private fun release(current: Active, code: String) {
        runCatching { current.lease.value.asBinder().unlinkToDeath(current.death, 0) }
        current.brokers.destroy(reason(code))
        runCatching { current.lease.bindingLease.close() }
    }

    private fun publish(value: ThreeStoveAgentLinkStatus) {
        mutableStatus.value = value
        runCatching { listener.onStatus(value) }
    }

    private inner class LinkCallback(private val generation: Long, private val pluginUid: Int) : IThreeStoveAgentLinkCallback.Stub() {
        override fun onStatus(status: Bundle?) {
            if (rejectDescriptor(status)) return
            if (Binder.getCallingUid() != pluginUid) return
            val parsed = runCatching {
                require(status?.getInt(C.KEY_CONTRACT_VERSION) == C.CONTRACT_VERSION)
                val json = requireNotNull(status.getString(C.KEY_STATUS_JSON))
                require(json.toByteArray(Charsets.UTF_8).size <= C.MAX_EVENT_JSON_BYTES)
                val value = JsonParser.parseString(json).asJsonObject
                val state = value.get("state").asString
                require(state in setOf("attached", "host_unavailable", "failed", "detached"))
                val queued = value.get("queuedCount")?.asInt ?: 0
                require(queued in 0..C.MAX_RUN_QUEUE)
                ThreeStoveAgentLinkStatus(state, value.get("runningRunId")?.takeUnless { it.isJsonNull }?.asString, queued)
            }.getOrNull() ?: return
            scope.launch {
                mutex.withLock {
                    if (policy.accepts(generation)) {
                        publish(parsed)
                        if (parsed.state in setOf("failed", "detached", "host_unavailable")) {
                            policy.failed(generation)
                            active?.let { release(it, "plugin-detached") }
                            active = null
                            notifyLoss(if (parsed.state == "host_unavailable") C.ERROR_HOST_UNAVAILABLE else C.ERROR_LINK_DETACHED)
                        }
                    }
                }
            }
        }

        override fun onEvent(event: Bundle?) {
            if (rejectDescriptor(event)) return
            if (Binder.getCallingUid() != pluginUid || event == null) return
            val copy = runCatching {
                require(event.getInt(C.KEY_CONTRACT_VERSION) == C.CONTRACT_VERSION)
                val json = requireNotNull(event.getString(C.KEY_RUN_EVENT_JSON))
                require(json.toByteArray(Charsets.UTF_8).size <= C.MAX_EVENT_JSON_BYTES)
                val type = JsonParser.parseString(json).asJsonObject.get("type").asString
                require(type in setOf("queue_changed", "preset_changed"))
                Bundle().apply {
                    putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
                    putString(C.KEY_RUN_EVENT_JSON, json)
                }
            }.getOrNull() ?: return
            scope.launch { mutex.withLock { if (policy.accepts(generation)) runCatching { listener.onEvent(copy) } } }
        }
    }

    companion object {
        @Volatile private var instance: ThreeStoveAgentLinkController? = null

        fun get(context: Context): ThreeStoveAgentLinkController = instance ?: synchronized(this) {
            instance ?: ThreeStoveAgentLinkController(context.applicationContext, connectOnDemand = {
                ThreeStoveAgentConnectionOwner.get(context.applicationContext).ensureConnected()
            }).also { instance = it }
        }

        internal fun reason(code: String) = Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(HostCapabilityContract.KEY_REASON_JSON, com.google.gson.JsonObject().apply { addProperty("code", code) }.toString())
        }

        private fun config(json: String) = Bundle().apply {
            putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION)
            putString(C.KEY_LINK_CONFIG_JSON, json)
        }

        /** Link status/events are small inline-only records. Never retain an unexpected FD. */
        private fun rejectDescriptor(bundle: Bundle?): Boolean = HostCapabilityBrokerCore.closeUnexpectedPayload(bundle)

        internal fun validateConfig(json: String) {
            require(json.toByteArray(Charsets.UTF_8).size <= C.MAX_CONTEXT_BYTES) { "Link config is too large" }
            require(JsonParser.parseString(json).isJsonObject) { "Link config must be an object" }
        }
    }
}
