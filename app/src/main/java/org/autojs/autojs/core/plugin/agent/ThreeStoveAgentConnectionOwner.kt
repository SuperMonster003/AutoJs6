package org.autojs.autojs.core.plugin.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys

data class ThreeStoveAgentConnectionState(val state: ThreeStoveAgentUiState, val status: ThreeStoveAgentLinkStatus = ThreeStoveAgentLinkStatus())

/** On-demand process-owned link. Plugin Center alone controls whether new connections are allowed. */
class ThreeStoveAgentConnectionOwner internal constructor(
    context: Context,
    private val controller: ThreeStoveAgentLinkController = ThreeStoveAgentLinkController.get(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val inspectPlugin: () -> ThreeStoveAgentInspection = { ThreeStoveAgentPluginInspector.inspect(context.applicationContext) },
    observePluginChanges: Boolean = true,
    private val readRootConfiguration: () -> String = ExtensibilityKeys::threeStoveAgentScriptRootConfigurationText,
    private val saveRootConfiguration: (String) -> Unit = ExtensibilityKeys::setThreeStoveAgentScriptRootConfigurationText,
) {
    private val context = context.applicationContext
    private val operation = Mutex()
    private val availability = MutableStateFlow(ThreeStoveAgentUiState.AVAILABLE)
    val state = combine(availability, controller.status) { available, status ->
        ThreeStoveAgentConnectionState(if (available == ThreeStoveAgentUiState.AVAILABLE) ThreeStoveAgentUiPolicy.linkState(status.state) else available, status)
    }.stateIn(scope, SharingStarted.Eagerly, ThreeStoveAgentConnectionState(ThreeStoveAgentUiState.DETACHED))
    private var connectJob: Deferred<ThreeStoveAgentInspection>? = null
    private var connectingConfiguration: String? = null
    private var refreshJob: Job? = null
    init {
        if (observePluginChanges) observePluginChanges()
    }

    private fun observePluginChanges() {
        PluginStateChangeRegistry.addListener { packageName ->
            if (packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.data?.schemeSpecificPart != ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) return
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                refresh()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * A background request from the plugin only connects. When the plugin is unavailable, its own
     * workbench explains why; the host keeps no guide to show later on an unrelated screen.
     */
    fun requestFromPlugin(configuration: String? = null) {
        connect(configuration = configuration)
    }

    /** Called on the main thread, including script requests marshalled here by ensureConnected. */
    fun connect(configuration: String? = null): Deferred<ThreeStoveAgentInspection> {
        val normalized = configuration?.let(ThreeStoveAgentScriptRoots::configuration)
        connectJob?.takeIf { it.isActive }?.let {
            if (normalized == null || normalized == connectingConfiguration) return it
            it.cancel()
        }
        connectingConfiguration = normalized
        return scope.async {
            operation.withLock {
                availability.value = ThreeStoveAgentUiState.ATTACHING
                var inspection = inspect()
                if (inspection.state == ThreeStoveAgentUiState.ACTIVATION_REQUIRED &&
                    PluginWakeManager.tryAutoWakeIfNeeded(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)) {
                    delay(300)
                    inspection = inspect()
                }
                availability.value = inspection.state
                if (inspection.state != ThreeStoveAgentUiState.AVAILABLE) return@withLock inspection
                try {
                    withContext(Dispatchers.IO) {
                        if (controller.status.value.state == "attached") {
                            if (normalized != null) controller.updateScriptRoots(normalized)
                        } else {
                            val settings = normalized ?: runCatching { ThreeStoveAgentScriptRoots.configuration(readRootConfiguration()) }.getOrDefault("{}")
                            controller.attach(settings)
                        }
                        // Persist only a configuration the host validated and the plugin accepted.
                        if (normalized != null) saveRootConfiguration(normalized)
                    }
                    // Disablement can race a slow bind. Recheck before exposing the completed link.
                    val current = inspect()
                    if (current.state != ThreeStoveAgentUiState.AVAILABLE) {
                        withContext(Dispatchers.IO) { controller.detach("plugin-unavailable") }
                        availability.value = current.state
                        current
                    } else inspection.copy(state = ThreeStoveAgentUiState.ATTACHED)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    availability.value = ThreeStoveAgentUiState.FAILED
                    inspection.copy(state = ThreeStoveAgentUiState.FAILED)
                }
            }
        }.also { connectJob = it }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            operation.withLock {
                withContext(Dispatchers.IO) { controller.detach() }
                availability.value = ThreeStoveAgentUiState.AVAILABLE
            }
        }
    }

    /** Script connections report the unavailable state in their error instead of queueing a guide. */
    suspend fun ensureConnected(): ThreeStoveAgentUiState = withContext(Dispatchers.Main.immediate) {
        connect().await().state
    }

    fun refresh() {
        scope.launch {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                operation.withLock {
                    val inspection = inspect()
                    if (inspection.state != ThreeStoveAgentUiState.AVAILABLE && controller.status.value.state in setOf("attaching", "attached", "failed")) {
                        withContext(Dispatchers.IO) { controller.detach("plugin-unavailable") }
                    }
                    availability.value = inspection.state
                }
            }
        }
    }

    private suspend fun inspect() = withContext(Dispatchers.IO) {
        runCatching { inspectPlugin() }.getOrElse { ThreeStoveAgentInspection(ThreeStoveAgentUiState.FAILED) }
    }

    companion object {
        /** The name of [ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION] (device tests read and restore it). */
        internal val ROOT_CONFIGURATION_KEY: String
            get() = ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.name
        @Volatile private var instance: ThreeStoveAgentConnectionOwner? = null
        fun get(context: Context): ThreeStoveAgentConnectionOwner = instance ?: synchronized(this) {
            instance ?: ThreeStoveAgentConnectionOwner(context).also { instance = it }
        }
    }
}
