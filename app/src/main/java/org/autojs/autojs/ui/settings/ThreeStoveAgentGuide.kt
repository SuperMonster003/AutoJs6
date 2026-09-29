package org.autojs.autojs.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.agent.*
import org.autojs.autojs.core.plugin.center.*
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

/** Activity-owned dialogs over the process-owned Agent connection. */
class ThreeStoveAgentGuide(private val context: Context) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = ThreeStoveAgentConnectionOwner.get(context)
    private var dialog: MaterialDialog? = null
    private var requestJob: Job? = null
    init {
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
        scope.launch { owner.pendingGuide.collect { showPendingGuide() } }
    }

    private fun connect() {
        if (requestJob?.isActive == true) return
        requestJob = scope.launch {
            val inspection = owner.connect().await()
            if (ThreeStoveAgentUiPolicy.needsGuide(inspection.state)) showGuide(inspection)
        }
    }

    fun refresh() { owner.refresh(); showPendingGuide() }

    override fun onResume(owner: LifecycleOwner) = refresh()
    override fun onDestroy(owner: LifecycleOwner) = dispose()

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
            ThreeStoveAgentUiState.PLUGIN_DISABLED -> R.string.text_plugin_center
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

    fun dispose() {
        (context as? LifecycleOwner)?.lifecycle?.removeObserver(this)
        dialog?.dismiss(); scope.cancel()
    }

    companion object {
        fun openPluginCenter(context: Context) = PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = "Three Stove Agent")

    }
}
