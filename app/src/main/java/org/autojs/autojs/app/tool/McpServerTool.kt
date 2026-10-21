package org.autojs.autojs.app.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.*
import org.autojs.autojs.core.plugin.mcp.*
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.ui.main.drawer.SocketItemHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.autojs6.BuildConfig
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.EmptyCoroutineContext
import org.autojs.plugin.mcp.server.api.McpServerCapabilityKeys
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds

data class McpServerToolState(val state: McpServerUiState, val status: McpServerStatus = McpServerStatus.stopped())

/** A drawer-scoped facade. Its process-scoped owner survives rotation and drawer disposal. */
class McpServerTool(override val context: Context) : SocketItemHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = McpServerOwner.get(context)
    private var inspectionJob: Job? = null
    private var dialog: MaterialDialog? = null
    val state = owner.state
    override val isInMainThread = true
    override val isConnected get() = McpServerUiPolicy.checked(state.value.state)
    override var isNormallyClosed: Boolean
        get() = owner.isNormallyClosed
        set(value) { owner.isNormallyClosed = value }

    override fun connect() = requestConnect(automatic = false)

    fun connectIfNotNormallyClosed() {
        if (!isNormallyClosed && !isConnected) requestConnect(automatic = true)
    }

    private fun requestConnect(automatic: Boolean) {
        if (isConnected || state.value.state == McpServerUiState.STOPPING || inspectionJob?.isActive == true) return
        inspectionJob = scope.launch {
            val inspection = withContext(Dispatchers.IO) { runCatching { McpServerPluginInspector.inspect(context) }.getOrNull() }
                ?: McpServerInspection(McpServerUiState.FAILED)
            owner.showAvailability(inspection.state)
            when {
                inspection.state == McpServerUiState.AVAILABLE -> {
                    owner.connect(automatic)
                    val result = state.first { it.state in setOf(McpServerUiState.RUNNING, McpServerUiState.HOST_UNAVAILABLE,
                        McpServerUiState.INCOMPATIBLE, McpServerUiState.FAILED, McpServerUiState.STOPPED) }
                    if (!automatic && result.state in setOf(McpServerUiState.INCOMPATIBLE, McpServerUiState.FAILED)) {
                        showGuide(inspection.copy(state = result.state))
                    }
                }
                automatic -> owner.isNormallyClosed = true
                else -> showGuide(inspection)
            }
        }
    }

    private fun showGuide(inspection: McpServerInspection) {
        val body = when (inspection.state) {
            McpServerUiState.NOT_INSTALLED -> context.getString(R.string.prompt_mcp_server_install)
            McpServerUiState.APPLICATION_DISABLED -> context.getString(R.string.prompt_mcp_server_application_disabled)
            McpServerUiState.ACTIVATION_REQUIRED -> context.getString(R.string.prompt_mcp_server_activate)
            McpServerUiState.PLUGIN_DISABLED -> context.getString(R.string.prompt_mcp_server_enable)
            McpServerUiState.AUTHORIZATION_REQUIRED -> context.getString(R.string.prompt_mcp_server_authorize)
            McpServerUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.prompt_mcp_server_trust)
            McpServerUiState.INCOMPATIBLE -> if (inspection.requiredHostVersion > BuildConfig.VERSION_CODE)
                context.getString(R.string.prompt_mcp_server_incompatible, inspection.requiredHostVersion)
                else context.getString(R.string.prompt_mcp_server_settings_unavailable)
            else -> context.getString(R.string.prompt_mcp_server_failed)
        }
        val positive = when (inspection.state) {
            McpServerUiState.ACTIVATION_REQUIRED -> R.string.dialog_button_activate
            McpServerUiState.PLUGIN_DISABLED -> R.string.text_enable_plugin
            McpServerUiState.TRUST_CONFIRMATION_REQUIRED -> R.string.dialog_button_confirm
            McpServerUiState.APPLICATION_DISABLED -> R.string.text_go_to_settings
            else -> R.string.text_plugin_center
        }
        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_mcp_server).content(body)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(positive)
            .onPositive { _, _ ->
                when (inspection.state) {
                    McpServerUiState.APPLICATION_DISABLED -> IntentUtils.launchAppDetailsSettings(context, McpServerIds.DEFAULT_PACKAGE_NAME)
                    McpServerUiState.ACTIVATION_REQUIRED -> {
                        if (PluginWakeManager.buildWakeIntent(context, McpServerIds.DEFAULT_PACKAGE_NAME)?.startSafely(context, true) == true) {
                            // A plugin suspended while it was unreachable is wanted again once the user activates it.
                            // zh-CN: 因无法连接而被挂起的插件, 在用户激活后视为重新需要.
                            PluginEnableStore.restoreIfSuspended(context, McpServerIds.DEFAULT_PACKAGE_NAME)
                            scope.launch { delay(500); requestConnect(false) }
                        }
                    }
                    McpServerUiState.PLUGIN_DISABLED -> {
                        PluginLocalNetworkPermission.runBeforeEnable(context, McpServerIds.DEFAULT_PACKAGE_NAME, "MCP Server") {
                            PluginEnableStore.setEnabled(context, McpServerIds.DEFAULT_PACKAGE_NAME, true)
                            requestConnect(false)
                        }
                    }
                    McpServerUiState.TRUST_CONFIRMATION_REQUIRED -> {
                        // Confirmation is pinned to all current signing fingerprints, never just the package name.
                        // An empty signer is never read back as confirmed (McpServerPluginInspector), so it is not written.
                        if (inspection.signer.isNotEmpty()) CredentialKeys.MCP_SERVER_TRUST_CONFIRMATION.key(inspection.signer).set(true)
                        requestConnect(false)
                    }
                    else -> openPluginCenter(context)
                }
            }
            .show()
    }

    override fun disconnect() {
        inspectionJob?.cancel()
        dialog?.dismiss()
        owner.disconnect()
    }

    override fun dispose() {
        dialog?.dismiss()
        scope.cancel()
    }

    companion object {
        fun openPluginCenter(context: Context) = PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = "MCP Server")

        fun subtitle(context: Context, value: McpServerToolState): String? = when (value.state) {
            McpServerUiState.NOT_INSTALLED -> context.getString(R.string.mcp_server_state_not_installed)
            McpServerUiState.APPLICATION_DISABLED, McpServerUiState.PLUGIN_DISABLED -> context.getString(R.string.mcp_server_state_disabled)
            McpServerUiState.ACTIVATION_REQUIRED -> context.getString(R.string.mcp_server_state_activation)
            McpServerUiState.AUTHORIZATION_REQUIRED, McpServerUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.mcp_server_state_authorization)
            McpServerUiState.INCOMPATIBLE -> context.getString(R.string.mcp_server_state_incompatible)
            McpServerUiState.CHECKING, McpServerUiState.STARTING -> context.getString(R.string.mcp_server_state_starting)
            McpServerUiState.RUNNING -> value.status.endpoints.joinToString("\n").ifBlank { context.getString(R.string.mcp_server_state_running) }
            McpServerUiState.HOST_UNAVAILABLE -> context.getString(R.string.mcp_server_state_host_unavailable)
            McpServerUiState.STOPPING -> context.getString(R.string.mcp_server_state_stopping)
            McpServerUiState.FAILED -> context.getString(R.string.mcp_server_state_failed)
            else -> null
        }
    }
}

/** Only this process-level object owns a session and the remembered user intent. No boot receiver. */
private class McpServerOwner(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(McpServerToolState(McpServerUiState.STOPPED))
    val state = mutableState.asStateFlow()
    private var startJob: Job? = null
    @Volatile private var expectedClose = false
    private val statusSequence = AtomicLong()
    private val operation = Mutex()
    private var wanted = false
    var isNormallyClosed: Boolean
        get() = MiscStateKeys.MCP_SERVER_NORMALLY_CLOSED.get()
        set(value) { MiscStateKeys.MCP_SERVER_NORMALLY_CLOSED.set(value) }
    private val controller: McpServerSessionController = McpServerSessionController(this.context, object : McpServerSessionController.Listener {
        override fun onStatus(status: McpServerStatus) {
            if (expectedClose) return
            val sequence = statusSequence.incrementAndGet()
            scope.launch {
                if (expectedClose || sequence != statusSequence.get()) return@launch
                if (status.state == McpServerContract.STATE_STOPPED && status.lastErrorCode == McpServerContract.REASON_CONFIG_CHANGED) {
                    mutableState.value = McpServerToolState(McpServerUiState.STARTING, status)
                    return@launch
                }
                val ui = McpServerUiPolicy.state(status)
                mutableState.value = McpServerToolState(ui, status)
                if (ui == McpServerUiState.RUNNING) isNormallyClosed = false
                if (ui in setOf(McpServerUiState.STOPPED, McpServerUiState.FAILED) && wanted) {
                    // Notification stop and exhausted rebinds close the host switch and release its broker.
                    wanted = false
                    isNormallyClosed = true
                    closeSessionPreservingState()
                    // A plugin that died and is no longer available (force-stopped in the system
                    // settings) shows what the user has to do next, as a fresh install does.
                    if (status.lastErrorCode == McpServerContract.REASON_PLUGIN_DIED) showInspectedAvailability()
                }
            }
        }
        override fun onEvent(event: McpServerEvent) = Unit
    })

    init {
        PluginStateChangeRegistry.addListener { packageName ->
            if (packageName == McpServerIds.DEFAULT_PACKAGE_NAME) scope.launch { onPluginChanged() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.data?.schemeSpecificPart == McpServerIds.DEFAULT_PACKAGE_NAME) scope.launch { onPluginChanged() }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // A plugin installed before AutoJs6 started but never activated gets its hint without a switch attempt.
        scope.launch { refreshGuidance() }
    }

    private suspend fun onPluginChanged() = if (wanted) validateActivePlugin() else refreshGuidance()

    private suspend fun validateActivePlugin() {
        if (!wanted) return
        val inspection = withContext(Dispatchers.IO) { runCatching { McpServerPluginInspector.inspect(context) }.getOrNull() }
        if (inspection?.state != McpServerUiState.AVAILABLE) disconnect()
    }

    /**
     * A plugin installed, enabled or updated after a refused attempt clears the drawer guidance without a host restart,
     * and an installed plugin that still needs activation shows that while the switch is off ([McpServerUiPolicy.idleGuidance]).
     */
    private suspend fun refreshGuidance() {
        val current = mutableState.value.state
        if (!McpServerUiPolicy.guidance(current) && current != McpServerUiState.STOPPED) return
        val inspection = withContext(Dispatchers.IO) { runCatching { McpServerPluginInspector.inspect(context) }.getOrNull() } ?: return
        if (wanted) return
        McpServerUiPolicy.refreshedGuidance(mutableState.value.state, inspection.state)?.let { mutableState.value = McpServerToolState(it) }
    }

    fun showAvailability(state: McpServerUiState) {
        if (!wanted) mutableState.value = McpServerToolState(state)
    }

    private suspend fun showInspectedAvailability() {
        val inspection = withContext(Dispatchers.IO) { runCatching { McpServerPluginInspector.inspect(context) }.getOrNull() } ?: return
        if (inspection.state != McpServerUiState.AVAILABLE) showAvailability(inspection.state)
    }

    fun connect(automatic: Boolean) {
        if (wanted || startJob?.isActive == true) return
        wanted = true
        mutableState.value = McpServerToolState(McpServerUiState.CHECKING)
        // Switching the server on wakes the plugin even while an earlier wake that did not help backs it off (ColorOS);
        // restoring it on start only wakes on demand.
        // zh-CN: 手动打开开关时即使先前的唤醒无效处于退避期也会唤醒插件 (ColorOS); 启动时的自动恢复只按需唤醒.
        startJob = scope.launch(if (automatic) EmptyCoroutineContext else PluginAutoWake.UserRequest) {
            try {
                val caps = withContext(Dispatchers.IO) { McpServerPluginHost.getCapabilities(context) }
                if (caps.getInt(McpServerCapabilityKeys.SETTINGS_VERSION, 0) < McpServerContract.SETTINGS_VERSION) {
                    wanted = false
                    isNormallyClosed = true
                    mutableState.value = McpServerToolState(McpServerUiState.INCOMPATIBLE)
                    return@launch
                }
                if (automatic && caps.getBoolean(McpServerCapabilityKeys.USER_STOPPED, true)) {
                    wanted = false
                    isNormallyClosed = true
                    mutableState.value = McpServerToolState(McpServerUiState.STOPPED)
                    return@launch
                }
                isNormallyClosed = false
                operation.withLock { withContext(Dispatchers.IO) { controller.open(McpServerConfig(useSavedSettings = true)) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                wanted = false
                isNormallyClosed = true
                mutableState.value = McpServerToolState(McpServerUiState.FAILED)
                closeSessionPreservingState()
            }
        }
    }

    fun disconnect() {
        wanted = false
        statusSequence.incrementAndGet()
        isNormallyClosed = true
        startJob?.cancel()
        mutableState.value = McpServerToolState(McpServerUiState.STOPPING)
        scope.launch {
            closeSessionPreservingState()
            mutableState.value = McpServerToolState(McpServerUiState.STOPPED)
        }
    }

    private suspend fun closeSessionPreservingState(): Unit = operation.withLock {
        expectedClose = true
        try { withContext(Dispatchers.IO) { controller.close() } }
        finally { expectedClose = false }
    }

    companion object {
        @Volatile private var instance: McpServerOwner? = null
        fun get(context: Context): McpServerOwner = instance ?: synchronized(this) {
            instance ?: McpServerOwner(context).also { instance = it }
        }
    }
}
