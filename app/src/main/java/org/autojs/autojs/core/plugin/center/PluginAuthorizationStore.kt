package org.autojs.autojs.core.plugin.center

import android.content.Context
import androidx.core.content.edit

object PluginAuthorizationStore {

    private const val SP_NAME = "plugin_center_authorization"

    fun isGranted(context: Context, packageName: String, fingerprints: List<String>): Boolean {
        if (fingerprints.isEmpty()) return false
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return fingerprints.any { fp -> sp.getBoolean(key(packageName, fp), false) }
    }

    fun grant(context: Context, packageName: String, fingerprint: String?) {
        fingerprint ?: return
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit { putBoolean(key(packageName, fingerprint), true) }
    }

    fun revoke(context: Context, packageName: String, fingerprint: String?) {
        fingerprint ?: return
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit { remove(key(packageName, fingerprint)) }
    }

    private fun key(packageName: String, fingerprint: String) = "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
}
