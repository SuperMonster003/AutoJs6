package org.autojs.autojs.network

import android.content.Context
import androidx.core.content.edit
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

object UpdateIgnoreStore {

    private val context by lazy { GlobalAppContext.get() }

    private val sp by lazy {
        context.getSharedPreferences(context.getString(R.string.key_ignored_updates), Context.MODE_PRIVATE)
    }

    fun ignoreVersion(packageName: String, versionCode: Long, versionName: String? = null) {
        val set = getMutableStringSet(packageName).apply {
            add(encodeVersion(versionCode, versionName))
        }
        sp.edit { putStringSet(key(packageName), set) }
    }

    fun unignoreVersion(packageName: String, versionCode: Long) {
        val set = getMutableStringSet(packageName).apply {
            removeAll { it.startsWith("$versionCode|") || it == versionCode.toString() }
        }
        sp.edit { putStringSet(key(packageName), set) }
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

    private fun key(packageName: String) = "key_\$_ignored_plugin_\$_$packageName"

    private fun getStringSet(packageName: String) =
        sp.getStringSet(key(packageName), emptySet()) ?: emptySet()

    private fun getMutableStringSet(packageName: String) =
        sp.getStringSet(key(packageName), emptySet())?.toMutableSet() ?: mutableSetOf()

}