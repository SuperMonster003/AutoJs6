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
import org.autojs.plugin.ai.agent.api.AiAgentIds

data class AiAgentToolState(val state: AiAgentUiState, val status: AiAgentLinkStatus = AiAgentLinkStatus())

/** Process-owned connection intent. Drawer disposal never detaches a live link or starts a run. */
class AiAgentConnectionOwner internal constructor(
    context: Context,
    private val controller: AiAgentLinkController = AiAgentLinkController.get(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val inspectPlugin: () -> AiAgentInspection = { AiAgentPluginInspector.inspect(context.applicationContext) },
    observePluginChanges: Boolean = true,
) {
    private val context = context.applicationContext
    private val operation = Mutex()
    private val availability = MutableStateFlow(AiAgentUiState.AVAILABLE)
    private val mutableGuide = MutableStateFlow<AiAgentInspection?>(null)
    val pendingGuide = mutableGuide.asStateFlow()
    val state = combine(availability, controller.status) { available, status ->
        AiAgentToolState(if (available == AiAgentUiState.AVAILABLE) AiAgentUiPolicy.linkState(status.state) else available, status)
    }.stateIn(scope, SharingStarted.Eagerly, AiAgentToolState(AiAgentUiState.DETACHED))
    private var connectJob: Deferred<AiAgentInspection>? = null
    private var refreshJob: Job? = null
    var isNormallyClosed: Boolean
        get() = controller.isNormallyClosed
        set(value) { controller.isNormallyClosed = value }

    init {
        if (observePluginChanges) observePluginChanges()
    }

    private fun observePluginChanges() {
        PluginStateChangeRegistry.addListener { packageName ->
            if (packageName == AiAgentIds.DEFAULT_PACKAGE_NAME) refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.data?.schemeSpecificPart != AiAgentIds.DEFAULT_PACKAGE_NAME) return
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                refresh()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Only host startup restores remembered connections. There is deliberately no boot receiver. */
    fun restoreIfWanted() {
        scope.launch { if (!isNormallyClosed) connect(automatic = true).await() }
    }

    fun requestFromPlugin() {
        scope.launch {
            val inspection = connect(automatic = false).await()
            if (AiAgentUiPolicy.needsGuide(inspection.state)) mutableGuide.value = inspection
        }
    }

    /** Called on the main thread, including through the receiver and drawer facade. */
    fun connect(automatic: Boolean = false): Deferred<AiAgentInspection> {
        connectJob?.takeIf { it.isActive }?.let { return it }
        return scope.async {
            operation.withLock {
                if (automatic && isNormallyClosed) return@withLock AiAgentInspection(AiAgentUiState.DETACHED)
                mutableGuide.value = null
                availability.value = AiAgentUiState.ATTACHING
                val inspection = inspect()
                availability.value = inspection.state
                if (inspection.state != AiAgentUiState.AVAILABLE) return@withLock inspection
                if (controller.status.value.state == "attached") return@withLock inspection.copy(state = AiAgentUiState.ATTACHED)
                try {
                    withContext(Dispatchers.IO) { controller.attach() }
                    inspection.copy(state = AiAgentUiState.ATTACHED)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    availability.value = AiAgentUiState.FAILED
                    inspection.copy(state = AiAgentUiState.FAILED)
                }
            }
        }.also { connectJob = it }
    }

    fun disconnect() {
        isNormallyClosed = true
        connectJob?.cancel()
        mutableGuide.value = null
        scope.launch {
            operation.withLock {
                withContext(Dispatchers.IO) { controller.detach() }
                availability.value = AiAgentUiState.AVAILABLE
            }
        }
    }

    fun refresh() {
        scope.launch {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                operation.withLock {
                    val inspection = inspect()
                    if (inspection.state != AiAgentUiState.AVAILABLE && controller.status.value.state in setOf("attaching", "attached", "failed")) {
                        withContext(Dispatchers.IO) { controller.detach("plugin-unavailable") }
                    }
                    availability.value = inspection.state
                }
            }
        }
    }

    fun consumeGuide(inspection: AiAgentInspection): Boolean = mutableGuide.compareAndSet(inspection, null)

    private suspend fun inspect() = withContext(Dispatchers.IO) {
        runCatching { inspectPlugin() }.getOrElse { AiAgentInspection(AiAgentUiState.FAILED) }
    }

    companion object {
        @Volatile private var instance: AiAgentConnectionOwner? = null
        fun get(context: Context): AiAgentConnectionOwner = instance ?: synchronized(this) {
            instance ?: AiAgentConnectionOwner(context).also { instance = it }
        }
    }
}
