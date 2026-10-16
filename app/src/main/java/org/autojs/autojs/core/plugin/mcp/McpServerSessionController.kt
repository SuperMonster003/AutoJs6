package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerContract
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle owner of one MCP server session on the host side (roadmap P1.2).
 *
 * The controller opens the server on a dedicated binding lease, links a death recipient on the
 * session binder, and, when the plugin process dies while the user still wants the server, rebinds
 * on the bounded [McpRebindPolicy] schedule. A user-initiated [close] suppresses rebinding, and so
 * does a plugin that is no longer available after its death ([pluginAvailable] is false once the
 * user force-stopped, disabled or uninstalled it): an explicit rebind would start a force-stopped
 * package again against the user's decision (roadmap P6 lifecycle matrix).
 *
 * [openServer], [linkDeath] / [unlinkDeath] and [sleep] default to the real plugin binding, the
 * session binder's death notification and [delay]; instrumentation tests replace them to drive the
 * rebind schedule with a fake plugin (a local binder never reports its death by itself).
 * zh-CN: [openServer], [linkDeath] / [unlinkDeath] 与 [sleep] 默认为真实的插件绑定, 会话 binder 的死亡通知与
 * [delay]; instrumentation 测试替换它们, 以假插件驱动重绑计划 (本地 binder 自身从不报告死亡).
 */
class McpServerSessionController(
    context: Context,
    private val listener: Listener,
    private val brokerFactory: (sessionId: String) -> IMcpHostCapabilityBroker = { sessionId ->
        McpHostCapabilityBroker(context, sessionId, McpCapabilityGrant.DEFAULT)
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pluginAvailable: () -> Boolean = {
        McpServerPluginInspector.inspect(context.applicationContext).state == McpServerUiState.AVAILABLE
    },
    private val openServer: suspend (Context, McpServerConfig, IMcpHostCapabilityBroker, IMcpServerCallback) -> AidlPluginHost.LeasedCall<IMcpServerSession> =
        McpServerPluginHost::openServer,
    private val linkDeath: (IBinder, IBinder.DeathRecipient) -> Unit = { binder, recipient -> binder.linkToDeath(recipient, 0) },
    private val unlinkDeath: (IBinder, IBinder.DeathRecipient) -> Unit = { binder, recipient -> binder.unlinkToDeath(recipient, 0) },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    interface Listener {
        fun onStatus(status: McpServerStatus)
        fun onEvent(event: McpServerEvent)
    }

    private inner class Active(
        val sessionId: String,
        val config: McpServerConfig,
        val leased: AidlPluginHost.LeasedCall<IMcpServerSession>,
        val broker: IMcpHostCapabilityBroker,
        val deathRecipient: IBinder.DeathRecipient,
        val callback: SessionCallback,
    ) {
        val session: IMcpServerSession
            get() = leased.value
    }

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val closedByUser = AtomicBoolean(true)
    private val statusFlow = MutableStateFlow(McpServerStatus.stopped())

    @Volatile
    private var active: Active? = null

    @Volatile
    private var currentSessionId: String? = null

    @Volatile
    private var desiredConfig: McpServerConfig? = null

    private var rebindAttempt = 0
    private var rebindJob: Job? = null

    val status: StateFlow<McpServerStatus> = statusFlow.asStateFlow()

    val isOpen: Boolean
        get() = active != null

    /** Opens a server with [config]; throws when a session is already open or the plugin refuses. */
    suspend fun open(config: McpServerConfig): McpServerStatus = mutex.withLock {
        check(active == null) { "${McpServerContract.ERROR_ALREADY_OPEN}: an MCP server session is already open" }
        config.validate()?.let { throw IllegalArgumentException("${it.code}: ${it.message}") }
        closedByUser.set(false)
        desiredConfig = config
        rebindAttempt = 0
        rebindJob?.cancel()
        rebindJob = null
        openLocked(config)
    }

    suspend fun updateConfig(config: McpServerConfig): Unit = mutex.withLock {
        config.validate()?.let { throw IllegalArgumentException("${it.code}: ${it.message}") }
        val current = checkNotNull(active) { "no MCP server session is open" }
        current.session.updateConfig(McpServerBundles.configToBundle(config))
        desiredConfig = config
        active = Active(current.sessionId, config, current.leased, current.broker, current.deathRecipient, current.callback)
    }

    /** Asks the plugin to stop listening but keeps the session; [close] releases everything. */
    suspend fun stop(reasonCode: String = McpServerContract.REASON_USER_REQUEST, message: String? = null): Unit = mutex.withLock {
        val current = active ?: return@withLock
        runCatching { current.session.stop(McpServerBundles.reasonBundle(reasonCode, message)) }
            .onFailure { Log.w(TAG, "stop failed for ${current.sessionId}: ${it.message}") }
    }

    /** Stops and closes the session, releases the lease, destroys the broker, and cancels rebinding. */
    suspend fun close(reasonCode: String = McpServerContract.REASON_USER_REQUEST, message: String? = null): Unit = mutex.withLock {
        closedByUser.set(true)
        rebindJob?.cancel()
        rebindJob = null
        val current = active
        if (current != null) {
            runCatching { current.session.stop(McpServerBundles.reasonBundle(reasonCode, message)) }
            runCatching { current.session.close() }
            releaseLocked(current, reasonCode, message)
        }
        publish(McpServerStatus.stopped(reasonCode, message))
    }

    suspend fun refreshStatus(): McpServerStatus = mutex.withLock {
        val current = active ?: return@withLock statusFlow.value
        pullStatus(current, current.callback) ?: statusFlow.value
    }

    private suspend fun openLocked(config: McpServerConfig, rebinding: Boolean = false): McpServerStatus {
        val sessionId = "mcp-" + UUID.randomUUID().toString()
        currentSessionId = sessionId
        val broker = brokerFactory(sessionId)
        val callback = SessionCallback(sessionId)
        publish(McpServerStatus.starting(config))
        val leased = try {
            openServer(appContext, config, broker, callback)
        } catch (error: Throwable) {
            runCatching { broker.destroy(McpServerBundles.reasonBundle(McpServerContract.ERROR_INTERNAL, error.message)) }
            currentSessionId = null
            if (!rebinding) publish(McpServerStatus.failed(McpServerContract.ERROR_INTERNAL, error.message ?: error.javaClass.name))
            throw error
        }
        val deathRecipient = IBinder.DeathRecipient { onSessionDied(sessionId) }
        val binder = leased.value.asBinder()
        try {
            linkDeath(binder, deathRecipient)
        } catch (error: RemoteException) {
            runCatching { leased.bindingLease.close() }
            runCatching { broker.destroy(McpServerBundles.reasonBundle(McpServerContract.REASON_PLUGIN_DIED, error.message)) }
            currentSessionId = null
            if (!rebinding) publish(McpServerStatus.failed(McpServerContract.REASON_PLUGIN_DIED, "MCP Server died while opening"))
            throw IllegalStateException("MCP Server died while opening", error)
        }
        val current = Active(sessionId, config, leased, broker, deathRecipient, callback)
        active = current
        return pullStatus(current, callback) ?: McpServerStatus.starting(config)
    }

    /**
     * Pulls `getStatus()` from the plugin and publishes it unless the plugin's callback delivered a
     * status while the pull was in flight: the plugin starts its listener as soon as `openServer`
     * is called, so `running` may arrive through the callback before the pulled snapshot, and the
     * snapshot must not roll the flow back to `starting`. Returns null when the pull failed.
     */
    private fun pullStatus(current: Active, callback: SessionCallback): McpServerStatus? {
        val before = callback.statusCount
        val snapshot = runCatching { McpServerBundles.statusFromBundle(current.session.getStatus()) }
            .getOrNull() ?: return null
        synchronized(callback) {
            if (callback.statusCount == before) {
                // openServer queues listener startup. Its initial getStatus can still be stopped;
                // preserve starting until the first callback instead of closing the drawer switch.
                if (before == 0 && snapshot.state == McpServerContract.STATE_STOPPED) return statusFlow.value
                publish(snapshot)
                return snapshot
            }
        }
        return statusFlow.value
    }

    private fun releaseLocked(current: Active, reasonCode: String, message: String?) {
        runCatching { unlinkDeath(current.leased.value.asBinder(), current.deathRecipient) }
        runCatching { current.leased.bindingLease.close() }
        runCatching { current.broker.destroy(McpServerBundles.reasonBundle(reasonCode, message)) }
        if (active === current) {
            active = null
        }
        if (currentSessionId == current.sessionId) {
            currentSessionId = null
        }
    }

    private fun onSessionDied(sessionId: String) {
        scope.launch {
            mutex.withLock {
                val current = active?.takeIf { it.sessionId == sessionId } ?: return@withLock
                Log.w(TAG, "MCP Server session $sessionId died")
                releaseLocked(current, McpServerContract.REASON_PLUGIN_DIED, null)
                if (closedByUser.get()) {
                    publish(McpServerStatus.stopped(McpServerContract.REASON_PLUGIN_DIED))
                    return@withLock
                }
                if (!isPluginAvailable()) {
                    stopForUnavailablePluginLocked()
                    return@withLock
                }
                scheduleRebindLocked(current.config)
            }
        }
    }

    private fun isPluginAvailable(): Boolean = runCatching { pluginAvailable() }.getOrDefault(true)

    /** The plugin package is stopped, disabled or gone: the session ends instead of being rebound. */
    private fun stopForUnavailablePluginLocked() {
        Log.w(TAG, "MCP Server is no longer available after its process died; not rebinding")
        closedByUser.set(true)
        rebindAttempt = 0
        rebindJob?.cancel()
        rebindJob = null
        publish(
            McpServerStatus.stopped(
                McpServerContract.REASON_PLUGIN_DIED,
                "MCP Server was stopped outside AutoJs6; turn the switch on to activate it again",
            ),
        )
    }

    private fun scheduleRebindLocked(config: McpServerConfig) {
        val attempt = rebindAttempt + 1
        val delayMs = McpRebindPolicy.delayForAttempt(attempt)
        if (delayMs == null || McpRebindPolicy.isExhausted(attempt)) {
            rebindAttempt = 0
            publish(
                McpServerStatus.stopped(
                    McpServerContract.REASON_PLUGIN_DIED,
                    "MCP Server did not come back after ${McpRebindPolicy.MAX_ATTEMPTS} rebind attempts",
                ),
            )
            return
        }
        rebindAttempt = attempt
        publish(
            McpServerStatus.starting(
                config,
                lastErrorCode = McpServerContract.REASON_PLUGIN_DIED,
                lastError = "MCP Server died; rebinding (attempt $attempt of ${McpRebindPolicy.MAX_ATTEMPTS}) in $delayMs ms",
            ),
        )
        rebindJob?.cancel()
        rebindJob = scope.launch {
            sleep(delayMs)
            mutex.withLock {
                if (closedByUser.get() || active != null) {
                    return@withLock
                }
                if (!isPluginAvailable()) {
                    stopForUnavailablePluginLocked()
                    return@withLock
                }
                val target = desiredConfig ?: config
                try {
                    // Every attempt may wake a plugin ColorOS refuses to bind, even after a wake that did not help.
                    // zh-CN: 每次尝试都可以唤醒 ColorOS 拒绝绑定的插件, 即使之前的唤醒无效.
                    withContext(PluginAutoWake.Rebind) { openLocked(target, rebinding = true) }
                    rebindAttempt = 0
                } catch (error: Throwable) {
                    Log.w(TAG, "MCP Server rebind attempt $attempt failed: ${error.message}")
                    scheduleRebindLocked(target)
                }
            }
        }
    }

    private fun publish(status: McpServerStatus) {
        statusFlow.value = status
        runCatching { listener.onStatus(status) }.onFailure { Log.w(TAG, "status listener failed: ${it.message}") }
    }

    private inner class SessionCallback(private val sessionId: String) : IMcpServerCallback.Stub() {

        /** Number of status callbacks accepted so far; guarded by `synchronized(this)`. */
        @Volatile
        var statusCount = 0
            private set

        override fun onStatus(status: Bundle?) {
            if (sessionId != currentSessionId) {
                return
            }
            synchronized(this) {
                statusCount++
                publish(McpServerBundles.statusFromBundle(status))
            }
        }

        override fun onEvent(event: Bundle?) {
            if (sessionId != currentSessionId) {
                return
            }
            val parsed = McpServerBundles.eventFromBundle(event) ?: return
            runCatching { listener.onEvent(parsed) }.onFailure { Log.w(TAG, "event listener failed: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "McpServerSession"
    }
}
