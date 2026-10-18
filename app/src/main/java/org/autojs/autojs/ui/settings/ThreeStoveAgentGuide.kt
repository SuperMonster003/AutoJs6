package org.autojs.autojs.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.agent.*
import org.autojs.autojs.core.plugin.center.*
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

/**
 * Activity-owned answer to the plugin's "Open AutoJs6" request over the process-owned Agent connection.
 * Requests are handled while the user is here; nothing is queued for a later, unrelated screen.
 */
class ThreeStoveAgentGuide(private val context: Context) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = ThreeStoveAgentConnectionOwner.get(context)
    private var dialog: MaterialDialog? = null
    private var requestJob: Job? = null
    init {
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
    }

    /**
     * Attaches, or takes the user straight to 3-Stove Agent in Plugin Center when it cannot be used.
     * Only an unrecognized signer still asks in a dialog: that confirmation has no other entry.
     */
    fun handleForegroundRequest(intent: Intent?) {
        when (val request = ThreeStoveAgentAttachRequestReceiver.takeForegroundRequest(context, intent)) {
            null -> Unit
            ThreeStoveAgentAttachRequestReceiver.ForegroundRequest.Unauthorized -> openPluginCenter(context)
            is ThreeStoveAgentAttachRequestReceiver.ForegroundRequest.Attach -> connect(request.configuration)
        }
    }

    private fun connect(configuration: String?) {
        requestJob?.cancel()
        requestJob = scope.launch {
            val inspection = owner.connect(configuration = configuration).await()
            if (!ThreeStoveAgentUiPolicy.needsGuide(inspection.state) || !isVisible()) return@launch
            if (inspection.state == ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED) confirmSigner(inspection, configuration)
            else openPluginCenter(context)
        }
    }

    fun refresh() = owner.refresh()

    override fun onResume(owner: LifecycleOwner) = refresh()
    override fun onDestroy(owner: LifecycleOwner) = dispose()

    private fun isVisible(): Boolean {
        if ((context as? Activity)?.let { it.isFinishing || it.isDestroyed } == true) return false
        return (context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != false
    }

    private fun confirmSigner(inspection: ThreeStoveAgentInspection, configuration: String?) {
        dialog?.dismiss()
        dialog = MaterialDialog.Builder(context).title(R.string.text_three_stove_agent).content(R.string.prompt_three_stove_agent_trust)
            .negativeText(R.string.dialog_button_cancel).positiveText(R.string.dialog_button_confirm)
            .onPositive { _, _ ->
                // Pin the confirmation to the current complete signing fingerprint set.
                if (inspection.signer.isNotBlank()) context.getSharedPreferences(ThreeStoveAgentPluginInspector.CONFIRMATION_STORE, Context.MODE_PRIVATE)
                    .edit { putBoolean(inspection.signer, true) }
                connect(configuration)
            }.show()
    }

    fun dispose() {
        (context as? LifecycleOwner)?.lifecycle?.removeObserver(this)
        dialog?.dismiss(); scope.cancel()
    }

    companion object {
        fun openPluginCenter(context: Context) = PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)

    }
}
