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
import org.autojs.plugin.ai.agent.api.AiAgentIds

/** Activity-owned dialogs over the process-owned Agent connection. */
class AiAgentTool(override val context: Context) : SocketItemHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = AiAgentConnectionOwner.get(context)
    private var dialog: MaterialDialog? = null
    private var requestJob: Job? = null
    val state = owner.state
    override val isInMainThread = true
    override val isConnected get() = AiAgentUiPolicy.checked(state.value.state)
    override var isNormallyClosed: Boolean
        get() = owner.isNormallyClosed
        set(value) { owner.isNormallyClosed = value }

    init { scope.launch { owner.pendingGuide.collect { showPendingGuide() } } }

    override fun connect() {
        if (requestJob?.isActive == true) return
        requestJob = scope.launch {
            val inspection = owner.connect().await()
            if (AiAgentUiPolicy.needsGuide(inspection.state)) showGuide(inspection)
        }
    }

    fun refresh() { owner.refresh(); showPendingGuide() }

    private fun showPendingGuide() {
        if ((context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
        val pending = owner.pendingGuide.value ?: return
        if (owner.consumeGuide(pending)) showGuide(pending)
    }

    private fun showGuide(inspection: AiAgentInspection) {
        if ((context as? Activity)?.let { it.isFinishing || it.isDestroyed } == true) return
        val content = when (inspection.state) {
            AiAgentUiState.NOT_INSTALLED -> context.getString(R.string.prompt_ai_agent_install)
            AiAgentUiState.APPLICATION_DISABLED -> context.getString(R.string.prompt_ai_agent_application_disabled)
            AiAgentUiState.ACTIVATION_REQUIRED -> context.getString(R.string.prompt_ai_agent_activate)
            AiAgentUiState.PLUGIN_DISABLED -> context.getString(R.string.prompt_ai_agent_enable)
            AiAgentUiState.AUTHORIZATION_REQUIRED -> context.getString(R.string.prompt_ai_agent_authorize)
            AiAgentUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.prompt_ai_agent_trust)
            AiAgentUiState.INCOMPATIBLE -> if (inspection.requiredHostVersion > BuildConfig.VERSION_CODE)
                context.getString(R.string.prompt_ai_agent_incompatible, inspection.requiredHostVersion)
                else context.getString(R.string.prompt_ai_agent_failed)
            else -> context.getString(R.string.prompt_ai_agent_failed)
        }
        val positive = when (inspection.state) {
            AiAgentUiState.NOT_INSTALLED -> R.string.text_download
            AiAgentUiState.APPLICATION_DISABLED -> R.string.text_go_to_settings
            AiAgentUiState.ACTIVATION_REQUIRED -> R.string.dialog_button_activate
            AiAgentUiState.PLUGIN_DISABLED -> R.string.text_enable_plugin
            AiAgentUiState.TRUST_CONFIRMATION_REQUIRED -> R.string.dialog_button_confirm
            else -> R.string.text_plugin_center
        }
        dialog?.dismiss()
        dialog = MaterialDialog.Builder(context).title(R.string.text_ai_agent).content(content)
            .negativeText(R.string.dialog_button_cancel).positiveText(positive)
            .onPositive { _, _ ->
                when (inspection.state) {
                    AiAgentUiState.NOT_INSTALLED -> Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.url_ai_agent_releases))).startSafely(context, true)
                    AiAgentUiState.APPLICATION_DISABLED -> IntentUtils.launchAppDetailsSettings(context, AiAgentIds.DEFAULT_PACKAGE_NAME)
                    AiAgentUiState.ACTIVATION_REQUIRED -> {
                        if (PluginWakeManager.buildWakeIntent(context, AiAgentIds.DEFAULT_PACKAGE_NAME)?.startSafely(context, true) == true) {
                            scope.launch { delay(500); connect() }
                        }
                    }
                    AiAgentUiState.PLUGIN_DISABLED -> {
                        PluginEnableStore.setEnabled(context, AiAgentIds.DEFAULT_PACKAGE_NAME, true)
                        connect()
                    }
                    AiAgentUiState.TRUST_CONFIRMATION_REQUIRED -> {
                        // Pin the confirmation to the current complete signing fingerprint set.
                        if (inspection.signer.isNotBlank()) context.getSharedPreferences(AiAgentPluginInspector.CONFIRMATION_STORE, Context.MODE_PRIVATE)
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
        fun openPluginCenter(context: Context) = PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = "AI Agent")

        fun subtitle(context: Context, value: AiAgentToolState): String = when (value.state) {
            AiAgentUiState.NOT_INSTALLED -> context.getString(R.string.ai_agent_state_not_installed)
            AiAgentUiState.APPLICATION_DISABLED, AiAgentUiState.PLUGIN_DISABLED -> context.getString(R.string.ai_agent_state_disabled)
            AiAgentUiState.ACTIVATION_REQUIRED -> context.getString(R.string.ai_agent_state_activation)
            AiAgentUiState.AUTHORIZATION_REQUIRED, AiAgentUiState.TRUST_CONFIRMATION_REQUIRED -> context.getString(R.string.ai_agent_state_authorization)
            AiAgentUiState.INCOMPATIBLE -> context.getString(R.string.ai_agent_state_incompatible)
            AiAgentUiState.ATTACHING -> context.getString(R.string.ai_agent_state_attaching)
            AiAgentUiState.ATTACHED -> context.getString(R.string.ai_agent_state_attached,
                if (value.status.runningRunId == null) 0 else 1, value.status.queuedCount)
            AiAgentUiState.HOST_UNAVAILABLE -> context.getString(R.string.ai_agent_state_host_unavailable)
            AiAgentUiState.FAILED -> context.getString(R.string.ai_agent_state_failed)
            else -> context.getString(R.string.ai_agent_state_detached)
        }
    }
}
