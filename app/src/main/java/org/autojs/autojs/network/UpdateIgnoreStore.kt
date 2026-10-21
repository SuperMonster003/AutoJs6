package org.autojs.autojs.network

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.UpdatesKeys
import org.autojs.autojs6.R

/**
 * Ignored plugin versions in the named store named like `R.string.key_ignored_updates`, the key family
 * [UpdatesKeys.IGNORED_PLUGIN_VERSIONS] (docs/settings/ROADMAP.md P3.3, names unchanged).
 */
object UpdateIgnoreStore {

    private val context by lazy { GlobalAppContext.get() }

    fun ignoreVersion(packageName: String, versionCode: Long, versionName: String? = null) {
        val set = getMutableStringSet(packageName).apply {
            add(encodeVersion(versionCode, versionName))
        }
        key(packageName).set(set)
    }

    fun unignoreVersion(packageName: String, versionCode: Long) {
        val set = getMutableStringSet(packageName).apply {
            removeAll { it.startsWith("$versionCode|") || it == versionCode.toString() }
        }
        key(packageName).set(set)
    }

    fun isIgnored(packageName: String, versionCode: Long): Boolean {
        val set = getStringSet(packageName)
        return set.any { it.startsWith("$versionCode|") || it == versionCode.toString() }
    }

    fun ignoredVersionCodes(packageName: String): Set<Long> {
        val set = getStringSet(packageName)
        return set.mapNotNull { decodeVersionCode(it) }.toSet()
    }

    fun ignoredVersions(packageName: String): Set<Pair<Long, String>> {
        val set = getStringSet(packageName)
        return set.mapNotNull { decodeVersion(it) }.toSet()
    }

    private fun encodeVersion(versionCode: Long, versionName: String?): String {
        return if (versionName != null) {
            "$versionCode|$versionName"
        } else {
            versionCode.toString()
        }
    }

    private fun decodeVersionCode(encoded: String): Long? {
        return encoded.split("|").firstOrNull()?.toLongOrNull()
    }

    private fun decodeVersion(encoded: String): Pair<Long, String>? {
        val parts = encoded.split("|", limit = 2)
        val versionCode = parts.firstOrNull()?.toLongOrNull() ?: return null
        val versionName = parts.getOrNull(1) ?: context.getString(R.string.text_unknown)
        return versionCode to versionName
    }

    /** `key_$_ignored_plugin_$_<package name>`. */
    private fun key(packageName: String) = UpdatesKeys.IGNORED_PLUGIN_VERSIONS.key(packageName)

    private fun getStringSet(packageName: String): Set<String> = key(packageName).get()

    private fun getMutableStringSet(packageName: String): MutableSet<String> = key(packageName).get().toMutableSet()

}