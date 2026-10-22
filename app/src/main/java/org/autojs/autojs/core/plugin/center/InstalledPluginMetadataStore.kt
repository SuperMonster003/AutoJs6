package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.putOrRemove
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
        val nativePageAlignment: Long? = null,
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

    /**
     * Writes the cache with [SettingsJson] (docs/settings/ROADMAP.md P5.3), byte for byte as `Gson().toJson` wrote the
     * former payload classes on Android: null members are omitted, strings are escaped HTML-safe like Gson's default,
     * and the members of each entry are in name order, because Gson took them from `Class.getDeclaredFields`, which ART
     * returns sorted by name (the dex format sorts the fields of a class), not in declaration order.
     *
     * zh-CN: 以 SettingsJson 写缓存 (ROADMAP P5.3), 与 Android 上 `Gson().toJson` 写出原载体类的结果逐字节相同: 省略为 null 的
     * 成员, 字符串按 Gson 默认的 HTML 安全方式转义; 每个条目的成员按名称排序, 因为 Gson 按 `Class.getDeclaredFields` 取成员,
     * ART 按名称排序返回 (dex 格式对类的字段排序), 而不是声明顺序.
     */
    fun toJson(entries: Collection<Entry>): String =
        SettingsJson.stringify(linkedMapOf<String, Any?>("entries" to entries.map(::encodeEntry)), htmlSafe = true)

    /**
     * Reads the cache ([toJson], and every file that Gson wrote); malformed content gives an empty map. Members are read
     * as Gson read them into the former payload classes: a missing or null member stays null, unknown members are ignored,
     * null entries and entries without a package name are skipped, null ABIs are dropped, and a member of another type
     * makes the whole cache unreadable. Unlike Gson, the text must be strict JSON without duplicate keys, and a number
     * written as a string or a string written as a number or boolean is no longer accepted; Gson never wrote either.
     *
     * zh-CN: 读取缓存 ([toJson] 以及 Gson 写出的全部文件); 内容损坏时返回空映射. 成员按 Gson 读入原载体类的方式读取: 缺少或为
     * null 的成员保持 null, 忽略未知成员, 跳过为 null 或没有包名的条目, 丢弃为 null 的 ABI, 成员类型不符时整个缓存不可读. 与 Gson
     * 不同, 文本须为没有重复键的严格 JSON, 也不再接受写成字符串的数字或写成数字, 布尔值的字符串; Gson 从未写出这些形式.
     */
    fun parseJson(json: String): Map<String, Entry> = runCatching {
        val entries = SettingsJson.parseMap(json)["entries"] ?: return@runCatching emptyList<Entry>()
        require(entries is List<*>) { "Invalid entries" }
        entries.mapNotNull { element ->
            if (element == null) return@mapNotNull null
            require(element is Map<*, *>) { "Invalid entry" }
            @Suppress("UNCHECKED_CAST")
            decodeEntry(element as Map<String, Any?>)
        }
    }.getOrElse { emptyList() }.associateBy { it.packageName }

    // Members in name order (see toJson); putOrRemove omits null members like Gson's default.
    private fun encodeEntry(entry: Entry): Map<String, Any?> = linkedMapOf<String, Any?>()
        .putOrRemove("author", entry.author)
        .putOrRemove("description", entry.description)
        .putOrRemove("engine", entry.engine)
        .putOrRemove("engineId", entry.engineId)
        .putOrRemove("instruction", entry.instruction)
        .putOrRemove("languageTag", entry.languageTag)
        .putOrRemove("lastUpdateTime", entry.lastUpdateTime)
        .putOrRemove("nativePageAlignment", entry.nativePageAlignment)
        .putOrRemove("packageName", entry.packageName)
        .putOrRemove("requiresHostVersion", entry.requiresHostVersion)
        .putOrRemove("supportedAbis", entry.supportedAbis)
        .putOrRemove("title", entry.title)
        .putOrRemove("variant", entry.variant)
        .putOrRemove("versionCode", entry.versionCode)
        .putOrRemove("versionDate", entry.versionDate)

    private fun decodeEntry(raw: Map<String, Any?>): Entry? {
        // Every member is checked before an entry is skipped: Gson failed the whole cache on a member of another type.
        val packageName = raw.stringMember("packageName")
        val versionCode = raw.longMember("versionCode")
        val lastUpdateTime = raw.longMember("lastUpdateTime")
        val languageTag = raw.stringMember("languageTag")
        val title = raw.stringMember("title")
        val description = raw.stringMember("description")
        val author = raw.stringMember("author")
        val instruction = raw.stringMember("instruction")
        val versionDate = raw.stringMember("versionDate")
        val requiresHostVersion = raw.longMember("requiresHostVersion")
        val supportedAbis = raw["supportedAbis"]?.let { abis ->
            require(abis is List<*>) { "Invalid supportedAbis" }
            abis.mapNotNull { abi -> abi?.let { requireNotNull(it as? String) { "Invalid supportedAbis" } } }
        }
        val engine = raw.stringMember("engine")
        val variant = raw.stringMember("variant")
        val engineId = raw.stringMember("engineId")
        val nativePageAlignment = raw.longMember("nativePageAlignment")
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
            supportedAbis = supportedAbis,
            nativePageAlignment = PluginNativeAlignmentPolicy.parse(nativePageAlignment),
            engine = engine,
            variant = variant,
            engineId = engineId,
        )
    }

    // A string member, null when missing or null; another type fails like Gson's type mismatch.
    private fun Map<String, Any?>.stringMember(key: String): String? {
        val value = this[key] ?: return null
        return requireNotNull(value as? String) { "Invalid $key" }
    }

    // A number member, null when missing or null; Gson's nextLong also took an integral fraction such as 1.0.
    private fun Map<String, Any?>.longMember(key: String): Long? {
        val value = this[key] ?: return null
        return requireNotNull((value as? Number)?.let(SettingsJson::toLong)) { "Invalid $key" }
    }

    private fun resolveCacheFile(context: Context): File {
        val dir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
        return File(dir, CACHE_FILE_NAME)
    }

}
