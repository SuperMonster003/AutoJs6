package org.autojs.autojs.core.plugin.center

import android.content.Context
import org.autojs.autojs.core.pref.keys.CredentialKeys

/**
 * Plugins authorized per signing fingerprint, in the named store `plugin_center_authorization` as the key family
 * [CredentialKeys.PLUGIN_AUTHORIZATION] (docs/settings/ROADMAP.md P3.4, sensitive, names unchanged). The [Context]
 * parameters are kept for the callers; the store is opened by the settings layer with the application context.
 */
object PluginAuthorizationStore {

    @Suppress("UNUSED_PARAMETER")
    fun isGranted(context: Context, packageName: String, fingerprints: List<String>): Boolean {
        if (fingerprints.isEmpty()) return false
        return fingerprints.any { fp -> key(packageName, fp).get() }
    }

    @Suppress("UNUSED_PARAMETER")
    fun grant(context: Context, packageName: String, fingerprint: String?) {
        fingerprint ?: return
        val key = key(packageName, fingerprint)
        if (key.get()) return
        key.set(true)
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    @Suppress("UNUSED_PARAMETER")
    fun revoke(context: Context, packageName: String, fingerprint: String?) {
        fingerprint ?: return
        val key = key(packageName, fingerprint)
        if (!key.isStored()) return
        key.remove()
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    /** `key_$_plugin_auth_$_<package name>$_<fingerprint>`, as before. */
    private fun key(packageName: String, fingerprint: String) = CredentialKeys.PLUGIN_AUTHORIZATION.key("$packageName\$_$fingerprint")
}
