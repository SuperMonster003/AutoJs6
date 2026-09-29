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
import org.autojs.autojs.core.pref.Pref

data class ThreeStoveAgentConnectionState(val state: ThreeStoveAgentUiState, val status: ThreeStoveAgentLinkStatus = ThreeStoveAgentLinkStatus())

/** On-demand process-owned link. Plugin Center alone controls whether new connections are allowed. */
class ThreeStoveAgentConnectionOwner internal constructor(
    context: Context,
    private val controller: ThreeStoveAgentLinkController = ThreeStoveAgentLinkController.get(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val inspectPlugin: () -> ThreeStoveAgentInspection = { ThreeStoveAgentPluginInspector.inspect(context.applicationContext) },
    observePluginChanges: Boolean = true,
    private val readRootConfiguration: () -> String = { Pref.getString(ROOT_CONFIGURATION_KEY, "{}") },
    private val saveRootConfiguration: (String) -> Unit = { Pref.putString(ROOT_CONFIGURATION_KEY, it) },
) {
    private val context = context.applicationContext
    private val operation = Mutex()
    private val availability = MutableStateFlow(ThreeStoveAgentUiState.AVAILABLE)
    private val mutableGuide = MutableStateFlow<ThreeStoveAgentInspection?>(null)
    val pendingGuide = mutableGuide.asStateFlow()
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

    fun requestFromPlugin(configuration: String? = null) {
        scope.launch {
            val inspection = connect(configuration = configuration).await()
            if (ThreeStoveAgentUiPolicy.needsGuide(inspection.state)) mutableGuide.value = inspection
        }
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
                mutableGuide.value = null
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
        mutableGuide.value = null
        scope.launch {
            operation.withLock {
                withContext(Dispatchers.IO) { controller.detach() }
                availability.value = ThreeStoveAgentUiState.AVAILABLE
            }
        }
    }

    suspend fun ensureConnected(showGuide: Boolean = true): Boolean = withContext(Dispatchers.Main.immediate) {
        val inspection = connect().await()
        if (showGuide && ThreeStoveAgentUiPolicy.needsGuide(inspection.state)) mutableGuide.value = inspection
        inspection.state == ThreeStoveAgentUiState.ATTACHED
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

    fun consumeGuide(inspection: ThreeStoveAgentInspection): Boolean = mutableGuide.compareAndSet(inspection, null)

    private suspend fun inspect() = withContext(Dispatchers.IO) {
        runCatching { inspectPlugin() }.getOrElse { ThreeStoveAgentInspection(ThreeStoveAgentUiState.FAILED) }
    }

    companion object {
        internal const val ROOT_CONFIGURATION_KEY = "three_stove_agent_script_root_configuration"
        @Volatile private var instance: ThreeStoveAgentConnectionOwner? = null
        fun get(context: Context): ThreeStoveAgentConnectionOwner = instance ?: synchronized(this) {
            instance ?: ThreeStoveAgentConnectionOwner(context).also { instance = it }
        }
    }
}
