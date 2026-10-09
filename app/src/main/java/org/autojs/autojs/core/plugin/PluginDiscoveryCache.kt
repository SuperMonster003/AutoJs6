package org.autojs.autojs.core.plugin

import android.content.Context
import android.os.Build
import org.autojs.plugin.common.api.PluginInfo

/**
 * Process-wide memo of the [PluginInfo] a plugin service advertised, keyed by the installed
 * package identity.
 *
 * Discovery otherwise binds the plugin service and calls `getInfo` on every selection, which for
 * out-of-process compiler plugins means starting that process before the host even knows whether
 * the request will be served from its own cache. An entry is reused only while the package keeps
 * the same version code and last-update time, so updates, reinstalls and removals naturally miss;
 * disabled components never reach the cache because the service query that precedes it already
 * excludes them. Failures are not cached. Hosts opt in per action because some plugins advertise
 * dynamic capabilities that callers may want fresh.
 */
class PluginDiscoveryCache {

    data class PackageIdentity(
        val versionCode: Long,
        val lastUpdateTime: Long,
    )

    private data class Key(
        val action: String,
        val category: String?,
        val packageName: String,
        val serviceName: String,
    )

    private class Entry(
        val identity: PackageIdentity,
        val pluginInfo: PluginInfo,
    )

    private val lock = Any()
    private val entries = HashMap<Key, Entry>()

    /** Returns the cached info when [identity] still matches the stored one; drops stale entries. */
    fun lookup(
        identity: PackageIdentity?,
        action: String,
        category: String?,
        packageName: String,
        serviceName: String,
    ): PluginInfo? {
        val key = Key(action, category, packageName, serviceName)
        synchronized(lock) {
            val entry = entries[key] ?: return null
            if (identity == null || entry.identity != identity) {
                entries.remove(key)
                return null
            }
            return entry.pluginInfo
        }
    }

    fun store(
        identity: PackageIdentity?,
        action: String,
        category: String?,
        packageName: String,
        serviceName: String,
        pluginInfo: PluginInfo,
    ) {
        if (identity == null) return
        synchronized(lock) {
            entries[Key(action, category, packageName, serviceName)] = Entry(identity, pluginInfo)
        }
    }

    /** Drops every entry, or only those of [packageName] when given. */
    fun invalidate(packageName: String? = null) {
        synchronized(lock) {
            if (packageName == null) {
                entries.clear()
            } else {
                entries.keys.removeAll { key -> key.packageName == packageName }
            }
        }
    }

    internal fun sizeForTesting(): Int = synchronized(lock) { entries.size }

    companion object {

        val shared = PluginDiscoveryCache()

        /** Installed identity of [packageName], or `null` when it is not installed. */
        fun installedPackageIdentity(context: Context, packageName: String): PackageIdentity? =
            runCatching {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }
                PackageIdentity(versionCode = versionCode, lastUpdateTime = info.lastUpdateTime)
            }.getOrNull()
    }
}
