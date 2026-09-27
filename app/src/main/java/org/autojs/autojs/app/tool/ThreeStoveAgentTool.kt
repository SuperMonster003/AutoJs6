package org.autojs.autojs.app.tool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.agent.*
import org.autojs.autojs.core.plugin.center.*
import org.autojs.autojs.ui.main.drawer.SocketItemHelper
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

/** Activity-owned dialogs over the process-owned Agent connection. */
class ThreeStoveAgentTool(override val context: Context) : SocketItemHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = ThreeStoveAgentConnectionOwner.get(context)
    private var dialog: MaterialDialog? = null
    private var requestJob: Job? = null
    val state = owner.state
    override val isInMainThread = true
    override val isConnected get() = ThreeStoveAgentUiPolicy.checked(state.value.state)
    override var isNormallyClosed: Boolean
        get() = owner.isNormallyClosed
        set(value) { owner.isNormallyClosed = value }

    init { scope.launch { owner.pendingGuide.collect { showPendingGuide() } } }

    override fun connect() {
        if (requestJob?.isActive == true) return
        requestJob = scope.launch {
            val inspection = owner.connect().await()
            if (ThreeStoveAgentUiPolicy.needsGuide(inspection.state)) showGuide(inspection)
        }
    }

    fun refresh() { owner.refresh(); showPendingGuide() }

    private fun showPendingGuide() {
        if ((context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
        val pending = owner.pendingGuide.value ?: return
        if (owner.consumeGuide(pending)) showGuide(pending)
    }

    private fun showGuide(inspection: ThreeStoveAgentInspection) {
        if ((context as? Activity)?.let { it.isFinishing || it.isDestroyed } == true) return
        val content = when (inspection.state) {
            ThreeStoveAgentUiState.NOT_INSTALLED -> context.getString(R.string.prompt_three_stove_agent_install)
            ThreeStoveAgentUiState.APPLICATION_DISABLED -> context.getString(R.string.prompt_three_stove_agent_application_disabled)
            ThreeStoveAgentUiState.ACTIVATION_REQUIRED -> context.getString(R.string.prompt_three_stove_agent_activate)
            ThreeStoveAgentUiState.PLUGIN_DISABLED -> context.getString(R.string.prompt_three_stove_agent_enable)
            ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED -> context.getString(R.string.prompt_three_stove_agent_authorize)
            ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.prompt_three_stove_agent_trust)
            ThreeStoveAgentUiState.INCOMPATIBLE -> if (inspection.requiredHostVersion > BuildConfig.VERSION_CODE)
                context.getString(R.string.prompt_three_stove_agent_incompatible, inspection.requiredHostVersion)
                else context.getString(R.string.prompt_three_stove_agent_failed)
            else -> context.getString(R.string.prompt_three_stove_agent_failed)
        }
        val positive = when (inspection.state) {
            ThreeStoveAgentUiState.NOT_INSTALLED -> R.string.text_download
            ThreeStoveAgentUiState.APPLICATION_DISABLED -> R.string.text_go_to_settings
            ThreeStoveAgentUiState.ACTIVATION_REQUIRED -> R.string.dialog_button_activate
            ThreeStoveAgentUiState.PLUGIN_DISABLED -> R.string.text_enable_plugin
            ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED -> R.string.dialog_button_confirm
            else -> R.string.text_plugin_center
        }
        dialog?.dismiss()
        dialog = MaterialDialog.Builder(context).title(R.string.text_three_stove_agent).content(content)
            .negativeText(R.string.dialog_button_cancel).positiveText(positive)
            .onPositive { _, _ ->
                when (inspection.state) {
                    ThreeStoveAgentUiState.NOT_INSTALLED -> Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.url_three_stove_agent_releases))).startSafely(context, true)
                    ThreeStoveAgentUiState.APPLICATION_DISABLED -> IntentUtils.launchAppDetailsSettings(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
                    ThreeStoveAgentUiState.ACTIVATION_REQUIRED -> {
                        if (PluginWakeManager.buildWakeIntent(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)?.startSafely(context, true) == true) {
                            scope.launch { delay(500); connect() }
                        }
                    }
                    ThreeStoveAgentUiState.PLUGIN_DISABLED -> {
                        PluginEnableStore.setEnabled(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, true)
                        connect()
                    }
                    ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED -> {
                        // Pin the confirmation to the current complete signing fingerprint set.
                        if (inspection.signer.isNotBlank()) context.getSharedPreferences(ThreeStoveAgentPluginInspector.CONFIRMATION_STORE, Context.MODE_PRIVATE)
                            .edit { putBoolean(inspection.signer, true) }
                        connect()
                    }
                    else -> openPluginCenter(context)
                }
            }.show()
    }

    override fun disconnect() { requestJob?.cancel(); dialog?.dismiss(); owner.disconnect() }
    override fun dispose() { dialog?.dismiss(); scope.cancel() }

    companion object {
        fun openPluginCenter(context: Context) = PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = "Three Stove Agent")

        fun subtitle(context: Context, value: ThreeStoveAgentToolState): String = when (value.state) {
            ThreeStoveAgentUiState.NOT_INSTALLED -> context.getString(R.string.three_stove_agent_state_not_installed)
            ThreeStoveAgentUiState.APPLICATION_DISABLED, ThreeStoveAgentUiState.PLUGIN_DISABLED -> context.getString(R.string.three_stove_agent_state_disabled)
            ThreeStoveAgentUiState.ACTIVATION_REQUIRED -> context.getString(R.string.three_stove_agent_state_activation)
            ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED, ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.three_stove_agent_state_authorization)
            ThreeStoveAgentUiState.INCOMPATIBLE -> context.getString(R.string.three_stove_agent_state_incompatible)
            ThreeStoveAgentUiState.ATTACHING -> context.getString(R.string.three_stove_agent_state_attaching)
            ThreeStoveAgentUiState.ATTACHED -> context.getString(R.string.three_stove_agent_state_attached,
                if (value.status.runningRunId == null) 0 else 1, value.status.queuedCount)
            ThreeStoveAgentUiState.HOST_UNAVAILABLE -> context.getString(R.string.three_stove_agent_state_host_unavailable)
            ThreeStoveAgentUiState.FAILED -> context.getString(R.string.three_stove_agent_state_failed)
            else -> context.getString(R.string.three_stove_agent_state_detached)
        }
    }
}
