package org.autojs.autojs.core.plugin.bun

import android.content.Context
import androidx.core.content.edit

/**
 * Per-capability grants of the Bun capability bridge (plugin ROADMAP M7).
 *
 * Every capability the host offers to Bun scripts is switched independently of the plugin enable
 * state and of every other capability. A revoked capability is simply not offered in the next
 * `runScript` request, so the plugin never sees it and the script never receives it; nothing has to
 * be negotiated with, or torn down inside, the plugin.
 */
object BunHostCapabilityGrants {

    private const val SP_NAME = "bun_host_capability_grants"

    /** Read-only host info snapshot: host package, version and advisory locale/build date. */
    private const val KEY_HOST_INFO = "host_info"

    fun isHostInfoGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HOST_INFO, true)

    fun setHostInfoGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_HOST_INFO, granted) }
    }

}
