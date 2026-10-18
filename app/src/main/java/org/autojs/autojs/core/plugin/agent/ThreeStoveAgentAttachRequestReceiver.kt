package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

/**
 * Protected by the PLUGIN permission. Intent package extras are not proof
 * of caller identity, and Binder.getCallingUid() in onReceive identifies the delivery process.
 * The plugin supplies an immutable PendingIntent created in its own UID as an identity token.
 * It is never sent or retained. This also works on Android versions without sentFromUid.
 */
class ThreeStoveAgentAttachRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isAuthorizedRequest(context, intent)) {
            // Neither extras nor parser exceptions belong in the host log.
            Log.i(TAG, "Attach request rejected: identity not authorized")
            return
        }
        deliver(context, intent)
    }

    /** What the plugin's "Open AutoJs6" request asks of the main activity. */
    sealed interface ForegroundRequest {
        /** A proven plugin that is not authorized yet: show it in Plugin Center. */
        object Unauthorized : ForegroundRequest
        data class Attach(val configuration: String?) : ForegroundRequest
    }

    companion object {
        private const val TAG = "ThreeStoveAgentAttach"

        /**
         * The existing main activity is the foreground fallback; it requires the same proof.
         * The request extras are removed so that recreating the activity does not repeat it.
         */
        fun takeForegroundRequest(context: Context, intent: Intent?): ForegroundRequest? {
            if (intent?.getBooleanExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH, false) != true) return null
            val request = Intent(intent).setAction(ThreeStoveAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName)
            intent.removeExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH)
            intent.removeExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY)
            intent.removeExtra(ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON)
            if (!hasPluginIdentity(context, request)) return null
            if (!isAuthorized(context)) return ForegroundRequest.Unauthorized
            return requestedConfiguration(request).getOrNull()?.let { ForegroundRequest.Attach(it.value) }
        }

        private fun deliver(context: Context, intent: Intent) {
            val configuration = requestedConfiguration(intent).getOrNull() ?: return
            ThreeStoveAgentConnectionOwner.get(context).requestFromPlugin(configuration.value)
        }

        private class Requested(val value: String?)

        /** No extra keeps the saved roots; an invalid configuration rejects the whole request. */
        private fun requestedConfiguration(intent: Intent): Result<Requested> = runCatching {
            Requested(if (intent.hasExtra(ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON)) {
                ThreeStoveAgentScriptRoots.configuration(requireNotNull(intent.getStringExtra(ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON)))
            } else null)
        }

        internal fun isAuthorizedRequest(context: Context, intent: Intent): Boolean =
            hasPluginIdentity(context, intent) && isAuthorized(context)

        @Suppress("DEPRECATION")
        private fun hasPluginIdentity(context: Context, intent: Intent): Boolean = runCatching {
            if (intent.action != ThreeStoveAgentActions.ACTION_ATTACH_REQUEST || intent.`package` != context.packageName) return false
            val identity = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY, PendingIntent::class.java)
            } else {
                intent.getParcelableExtra<PendingIntent>(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY)
            } ?: return false
            if (Build.VERSION.SDK_INT >= 31 && !identity.isImmutable) return false
            val expectedUid = context.packageManager.getApplicationInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, 0).uid
            identity.creatorPackage == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME && identity.creatorUid == expectedUid
        }.getOrDefault(false)

        private fun isAuthorized(context: Context): Boolean = runCatching {
            val trust = PluginTrustManager.resolveTrustInfo(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
            trust.authorizedState != org.autojs.autojs.core.plugin.center.PluginAuthorizedState.REQUIRED
        }.getOrDefault(false)
    }
}
