package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import java.io.File

/**
 * Persists the most recently resolved metadata of installed AIDL plugins, so that
 * a cold-start snapshot can render the last known real list state without binding
 * any plugin process. Every successful complete discovery refreshes this store.
 *
 * zh-CN: 持久化已安装 AIDL 插件最近一次成功解析的元数据, 使冷启动快照无需绑定
 * 任何插件进程即可渲染上次已知的真实列表状态. 每次完整发现成功后刷新此存储.
 */
object InstalledPluginMetadataStore {

    private const val TAG = "InstalledPluginMeta"
    private const val CACHE_DIR_NAME = "plugin_center"
    private const val CACHE_FILE_NAME = "autojs6_installed_plugin_metadata_v1.json"

    private val gson = Gson()
    private val lock = Any()

    @Volatile
    private var memoryEntries: Map<String, Entry>? = null

    data class Entry(
        val packageName: String,
        val versionCode: Long?,
        val lastUpdateTime: Long?,
        val languageTag: String?,
        val title: String?,
        val description: String?,
        val author: String?,
        val instruction: String?,
        val versionDate: String?,
        val requiresHostVersion: Long?,
        val supportedAbis: List<String>?,
        val engine: String?,
        val variant: String?,
        val engineId: String?,
    ) {
        /**
         * Version-coupled fields (versionDate, requiresHostVersion, supportedAbis) are only
         * trustworthy when the entry was resolved from exactly the installed package build.
         *
         * zh-CN: 与版本耦合的字段 (versionDate, requiresHostVersion, supportedAbis) 仅在
         * 条目解析自当前安装的同一包构建时才可信.
         */
        fun matchesInstalledPackage(versionCode: Long?, lastUpdateTime: Long?): Boolean {
            return this.versionCode != null && this.versionCode == versionCode &&
                    this.lastUpdateTime != null && this.lastUpdateTime == lastUpdateTime
        }
    }

    fun readAll(context: Context): Map<String, Entry> {
        memoryEntries?.let { return it }
        synchronized(lock) {
            memoryEntries?.let { return it }
            val parsed = runCatching {
                val file = resolveCacheFile(context)
                if (file.exists() && file.canRead()) parseJson(file.readText()) else emptyMap()
            }.getOrElse { error ->
                Log.w(TAG, "Failed to read installed plugin metadata cache: ${error.message}")
                emptyMap()
            }
            memoryEntries = parsed
            return parsed
        }
    }

    fun replaceAll(context: Context, entries: Map<String, Entry>) {
        synchronized(lock) {
            if (memoryEntries == entries) {
                return
            }
            memoryEntries = entries
            runCatching {
                resolveCacheFile(context).writeText(toJson(entries.values))
            }.onFailure { error ->
                Log.w(TAG, "Failed to write installed plugin metadata cache: ${error.message}")
            }
        }
    }

    /**
     * Retained packages keep their previous entry when the current discovery could not
     * resolve fresh metadata for them; entries of removed packages are pruned.
     *
     * zh-CN: 本次发现未能解析出新元数据的包保留其旧条目; 已移除的包对应条目被清理.
     */
    fun mergeEntries(
        existing: Map<String, Entry>,
        fresh: Collection<Entry>,
        retainedPackages: Set<String>,
    ): Map<String, Entry> {
        val merged = LinkedHashMap<String, Entry>()
        existing.forEach { (packageName, entry) ->
            if (packageName in retainedPackages) {
                merged[packageName] = entry
            }
        }
        fresh.forEach { entry -> merged[entry.packageName] = entry }
        return merged
    }

    fun toJson(entries: Collection<Entry>): String {
        val payload = CachePayload(entries = entries.map { it.toRaw() })
        return gson.toJson(payload)
    }

    fun parseJson(json: String): Map<String, Entry> {
        val payload = runCatching { gson.fromJson(json, CachePayload::class.java) }.getOrNull() ?: return emptyMap()
        return payload.entries
            .orEmpty()
            .mapNotNull { it?.toEntryOrNull() }
            .associateBy { it.packageName }
    }

    private fun Entry.toRaw() = RawEntry(
        packageName = packageName,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        languageTag = languageTag,
        title = title,
        description = description,
        author = author,
        instruction = instruction,
        versionDate = versionDate,
        requiresHostVersion = requiresHostVersion,
        supportedAbis = supportedAbis,
        engine = engine,
        variant = variant,
        engineId = engineId,
    )

    private fun RawEntry.toEntryOrNull(): Entry? {
        val validPackageName = packageName?.takeIf { it.isNotBlank() } ?: return null
        return Entry(
            packageName = validPackageName,
            versionCode = versionCode,
            lastUpdateTime = lastUpdateTime,
            languageTag = languageTag,
            title = title,
            description = description,
            author = author,
            instruction = instruction,
            versionDate = versionDate,
            requiresHostVersion = requiresHostVersion,
            supportedAbis = supportedAbis?.filterNotNull(),
            engine = engine,
            variant = variant,
            engineId = engineId,
        )
    }

    private fun resolveCacheFile(context: Context): File {
        val dir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
        return File(dir, CACHE_FILE_NAME)
    }

    // Gson-facing payload classes stay fully nullable to survive malformed cache files,
    // and are kept from minification because they are populated reflectively.
    // zh-CN: 面向 Gson 的载体类全部可空以容忍损坏的缓存文件, 且因反射填充而不可被混淆.

    @Keep
    private class CachePayload(
        val entries: List<RawEntry?>? = null,
    )

    @Keep
    private class RawEntry(
        val packageName: String? = null,
        val versionCode: Long? = null,
        val lastUpdateTime: Long? = null,
        val languageTag: String? = null,
        val title: String? = null,
        val description: String? = null,
        val author: String? = null,
        val instruction: String? = null,
        val versionDate: String? = null,
        val requiresHostVersion: Long? = null,
        val supportedAbis: List<String?>? = null,
        val engine: String? = null,
        val variant: String? = null,
        val engineId: String? = null,
    )

}
