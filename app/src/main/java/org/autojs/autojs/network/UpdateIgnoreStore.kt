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

    fun ignoreVersion(packageName: String, versionCode: Long) {
        val set = getMutableStringSet(packageName).apply {
            add(versionCode.toString())
        }
        sp.edit { putStringSet(key(packageName), set) }
    }

    fun unignoreVersion(packageName: String, versionCode: Long) {
        val set = getMutableStringSet(packageName).apply {
            remove(versionCode.toString())
        }
        sp.edit { putStringSet(key(packageName), set) }
    }

    fun isIgnored(packageName: String, versionCode: Long): Boolean {
        val set = getStringSet(packageName)
        return versionCode.toString() in set
    }

    fun ignoredVersionCodes(packageName: String): Set<Long> {
        val set = getStringSet(packageName)
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun key(packageName: String) = "key_\$_ignored_plugin_\$_$packageName"

    private fun getStringSet(packageName: String) =
        sp.getStringSet(key(packageName), emptySet()) ?: emptySet()

    private fun getMutableStringSet(packageName: String) =
        sp.getStringSet(key(packageName), emptySet())?.toMutableSet() ?: mutableSetOf()

}
