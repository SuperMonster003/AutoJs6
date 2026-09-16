package org.autojs.autojs.core.plugin.bun

import android.content.Context
import androidx.core.content.edit
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract

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

    /** Dynamic capability `ui.toast`: short text toasts, at most four per run. */
    private const val KEY_UI_TOAST = "ui_toast"

    /** Dynamic capability `device.info`: brand, model and Android version from `android.os.Build`, no identifiers. */
    private const val KEY_DEVICE_INFO = "device_info"

    fun isUiToastGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_UI_TOAST, true)

    fun setUiToastGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_UI_TOAST, granted) }
    }

    fun isDeviceInfoGranted(context: Context): Boolean =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DEVICE_INFO, true)

    fun setDeviceInfoGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_DEVICE_INFO, granted) }
    }

    /** Capability IDs of the runtime bridge (plugin M7, dynamic calls) that are currently granted, in contract order. */
    fun grantedBridgeCapabilities(context: Context): List<String> = buildList {
        if (isUiToastGranted(context)) add(BunRuntimeContract.HOST_CAPABILITY_UI_TOAST)
        if (isDeviceInfoGranted(context)) add(BunRuntimeContract.HOST_CAPABILITY_DEVICE_INFO)
    }

}
