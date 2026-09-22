package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentIds

/**
 * Registered under the PLUGIN signature permission in P1.5. Intent package extras are not proof
 * of caller identity, and Binder.getCallingUid() in onReceive identifies the delivery process.
 * The plugin supplies an immutable PendingIntent created in its own UID as an identity token.
 * It is never sent or retained. This also works on Android versions without sentFromUid.
 */
class AiAgentAttachRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isAuthorizedRequest(context, intent)) return
        AiAgentLinkController.get(context).requestAttach()
    }

    companion object {
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
