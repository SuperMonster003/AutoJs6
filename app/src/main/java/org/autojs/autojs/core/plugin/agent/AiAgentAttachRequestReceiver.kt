package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.ai.agent.api.AiAgentContract

/**
 * Protected by the PLUGIN permission. Intent package extras are not proof
 * of caller identity, and Binder.getCallingUid() in onReceive identifies the delivery process.
 * The plugin supplies an immutable PendingIntent created in its own UID as an identity token.
 * It is never sent or retained. This also works on Android versions without sentFromUid.
 */
class AiAgentAttachRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isAuthorizedRequest(context, intent)) {
            // Neither extras nor parser exceptions belong in the host log.
            Log.i(TAG, "Attach request rejected: identity not authorized")
            return
        }
        deliver(context, intent)
    }

    companion object {
        private const val TAG = "AiAgentAttach"
        /** The existing main activity is the foreground fallback; it requires the same proof. */
        fun handleForegroundRequest(context: Context, intent: Intent?) {
            if (intent?.getBooleanExtra(AiAgentActions.EXTRA_AI_AGENT_ATTACH, false) != true) return
            val request = Intent(intent).setAction(AiAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName)
            intent.removeExtra(AiAgentActions.EXTRA_AI_AGENT_ATTACH)
            intent.removeExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY)
            intent.removeExtra(AiAgentContract.KEY_LINK_CONFIG_JSON)
            if (isAuthorizedRequest(context, request)) deliver(context, request)
        }

        private fun deliver(context: Context, intent: Intent) {
            val configuration = if (intent.hasExtra(AiAgentContract.KEY_LINK_CONFIG_JSON)) {
                runCatching { AiAgentScriptRoots.configuration(requireNotNull(intent.getStringExtra(AiAgentContract.KEY_LINK_CONFIG_JSON))) }
                    .getOrNull() ?: return
            } else null
            AiAgentConnectionOwner.get(context).requestFromPlugin(configuration)
        }

        @Suppress("DEPRECATION")
        internal fun isAuthorizedRequest(context: Context, intent: Intent): Boolean = runCatching {
            if (intent.action != AiAgentActions.ACTION_ATTACH_REQUEST || intent.`package` != context.packageName) return false
            val identity = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY, PendingIntent::class.java)
            } else {
                intent.getParcelableExtra<PendingIntent>(AiAgentActions.EXTRA_ATTACH_IDENTITY)
            } ?: return false
            if (Build.VERSION.SDK_INT >= 31 && !identity.isImmutable) return false
            val expectedUid = context.packageManager.getApplicationInfo(AiAgentIds.DEFAULT_PACKAGE_NAME, 0).uid
            if (identity.creatorPackage != AiAgentIds.DEFAULT_PACKAGE_NAME || identity.creatorUid != expectedUid) return false
            val trust = PluginTrustManager.resolveTrustInfo(context, AiAgentIds.DEFAULT_PACKAGE_NAME)
            trust.isOfficial || trust.isTrusted
        }.getOrDefault(false)
    }
}
