package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson

/**
 * `manifest.json` of a settings archive (docs/settings/ROADMAP.md D5 / D9 / T7, appendix B): the format and its
 * version, the export time, the host and device, and per category / data category / plugin the number of entries and
 * the file that holds them. The reader lists the categories from it before any category file is opened (T7).
 *
 * [plugins] lists the settings of official plugins (D3, appendix C Q7, P13.1): one [PluginEntry] per plugin directory
 * `plugins/<packageName>/settings.json`, like a category named by its package name; the first phase has no plugin
 * provider and writes an empty array. Reading is lenient (D9): unknown fields are ignored, and a [formatVersion] above
 * [FORMAT_VERSION] is accepted ([isNewerFormat], the import wizard warns).
 *
 * P7.1 checks what an import relies on: the format name, a [formatVersion] of at least 1, a host package name, entry
 * counts that are not negative, category, data and plugin ids listed once, data files under `data/` and plugin ids
 * that are package names (P13.1); anything else is [SettingsArchiveException.Reason.INVALID_MANIFEST]. An archive of a
 * newer format or of another host package (an `inrt` build, a build with another application id) is not refused but
 * carries a [Warning] ([warnings], appendix C Q26).
 *
 * zh-CN: 设置归档的 `manifest.json` (ROADMAP D5 / D9 / T7, 附录 B): 格式与版本, 导出时间, 宿主与设备, 以及每个分类 / 数据分类 /
 * 插件的条目数与所在文件. 读取时先由它列出分类, 再打开分类文件 (T7). [plugins] 列出官方插件的设置 (D3, 附录 C Q7, P13.1): 每个插件
 * 目录 `plugins/<包名>/settings.json` 一个 [PluginEntry], 相当于以包名命名的分类; 第一期没有插件提供方, 写空数组. 读取宽松 (D9):
 * 忽略未知字段, 接受高于 [FORMAT_VERSION] 的 [formatVersion] ([isNewerFormat], 由导入向导提示). P7.1 校验导入所依赖的内容: 格式名,
 * 不小于 1 的 [formatVersion], 宿主包名, 非负的条目数, 不重复的分类, 数据分类与插件 id, 位于 `data/` 下的数据文件, 以及为包名的插件
 * id (P13.1); 不符即 `INVALID_MANIFEST`. 更新格式或其他宿主包名 (`inrt` 打包应用, 改了 application id 的构建) 的归档不拒绝, 而是
 * 带上 [Warning] ([warnings], 附录 C Q26).
 */
data class SettingsArchiveManifest @JvmOverloads constructor(
    val exportedAt: String,
    val host: Host,
    val device: Device,
    val categories: List<CategoryEntry>,
    val data: List<DataEntry> = emptyList(),
    val plugins: List<PluginEntry> = emptyList(),
    val formatVersion: Int = FORMAT_VERSION,
) {

    data class Host(val packageName: String, val versionCode: Int, val versionName: String)

    data class Device(val model: String, val sdk: Int)

    /** A settings category of the archive, held by [SettingsArchive.categoryFile]. zh-CN: 归档中的一个设置分类. */
    data class CategoryEntry(val id: String, val entries: Int, val sensitive: Boolean) {
        val file: String
            get() = SettingsArchive.categoryFile(id)
    }

    /** A data category of the archive (T19), held by [file] under `data/`. zh-CN: 归档中的一个数据分类 (T19). */
    data class DataEntry(val id: String, val entries: Int, val file: String)

    /**
     * The settings of an official plugin (D3, P13.1): [id] is its package name, [entries] the number its export reported
     * (P13.2), held by [SettingsArchive.pluginFile].
     * zh-CN: 一个官方插件的设置 (D3, P13.1): [id] 为其包名, [entries] 为其导出时报告的条目数 (P13.2), 所在文件见
     * [SettingsArchive.pluginFile].
     */
    data class PluginEntry(val id: String, val entries: Int) {
        val file: String
            get() = SettingsArchive.pluginFile(id)
    }

    /**
     * What the import wizard warns about before the categories are selected (D9, appendix C Q26); the import goes on
     * once the user accepts.
     * zh-CN: 导入向导在选择分类之前提示的事项 (D9, 附录 C Q26); 用户确认后导入照常进行.
     */
    enum class Warning {
        /** [formatVersion] is above [FORMAT_VERSION]: entries of the newer format are skipped (`NEWER_FORMAT`). */
        NEWER_FORMAT,

        /** The archive was exported by another host package than the one importing it. */
        OTHER_HOST_PACKAGE,
    }

    /** True for an archive of a newer format than this version reads (D9). zh-CN: 归档格式比本版本新时为 true (D9). */
    val isNewerFormat: Boolean
        get() = formatVersion > FORMAT_VERSION

    /**
     * The warnings of this archive for the host [hostPackageName] that imports it, in the order of [Warning].
     * zh-CN: 由包名为 [hostPackageName] 的宿主导入时, 本归档的提示事项, 按 [Warning] 的顺序.
     */
    fun warnings(hostPackageName: String): Set<Warning> = linkedSetOf<Warning>().apply {
        if (isNewerFormat) add(Warning.NEWER_FORMAT)
        if (host.packageName != hostPackageName) add(Warning.OTHER_HOST_PACKAGE)
    }

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "format" to FORMAT,
        "formatVersion" to formatVersion,
        "exportedAt" to exportedAt,
        "host" to linkedMapOf("packageName" to host.packageName, "versionCode" to host.versionCode, "versionName" to host.versionName),
        "device" to linkedMapOf("model" to device.model, "sdk" to device.sdk),
        "categories" to categories.map { linkedMapOf("id" to it.id, "entries" to it.entries, "sensitive" to it.sensitive) },
        "data" to data.map { linkedMapOf("id" to it.id, "entries" to it.entries, "file" to it.file) },
        "plugins" to plugins.map { linkedMapOf("id" to it.id, "entries" to it.entries) },
    )

    /** The text of `manifest.json`, indented by 2. zh-CN: `manifest.json` 的文本, 缩进 2. */
    fun toText(): String = SettingsJson.stringify(toJson(), indent = 2)

    companion object {

        const val FORMAT = "autojs6-settings"
        const val FORMAT_VERSION = 1

        /**
         * The manifest of the JSON document [json]; throws [SettingsArchiveException] ([SettingsArchiveException.Reason.INVALID_MANIFEST])
         * when it is of another format or misses a required field.
         * zh-CN: JSON 文档 [json] 对应的 manifest; 格式不符或缺少必需字段时抛 [SettingsArchiveException] (`INVALID_MANIFEST`).
         */
        @JvmStatic
        fun fromJson(json: Any?): SettingsArchiveManifest {
            val root = json as? Map<*, *> ?: invalid("not a JSON object")
            if (root["format"] != FORMAT) invalid("the format is ${root["format"]}, not $FORMAT")
            val version = SettingsJson.toInt(root["formatVersion"])?.takeIf { it >= 1 } ?: invalid("no valid formatVersion")
            val host = root.obj("host")
            val device = root.obj("device")
            val categories = root.list("categories").map {
                CategoryEntry(it.str("id").also(::checkCategoryId), it.count("entries"), it["sensitive"] as? Boolean ?: invalid("no sensitive flag"))
            }
            val data = root.list("data", optional = true).map { DataEntry(it.str("id").also(::checkCategoryId), it.count("entries"), it.str("file").also(::checkDataFile)) }
            val plugins = root.list("plugins", optional = true).map { PluginEntry(it.str("id").also(::checkPluginId), it.count("entries")) }
            checkUnique("categories", categories.map { it.id })
            checkUnique("data", data.map { it.id })
            checkUnique("data files", data.map { it.file })
            checkUnique("plugins", plugins.map { it.id })
            return SettingsArchiveManifest(
                exportedAt = root.str("exportedAt"),
                host = Host(host.str("packageName").also(::checkPackageName), host.int("versionCode"), host.str("versionName")),
                device = Device(device.str("model"), device.int("sdk")),
                categories = categories,
                data = data,
                plugins = plugins,
                formatVersion = version,
            )
        }

        /** [fromJson] of the text of `manifest.json`. zh-CN: 由 `manifest.json` 的文本解码. */
        @JvmStatic
        fun fromText(text: String): SettingsArchiveManifest = try {
            fromJson(SettingsJson.parse(text))
        } catch (e: SettingsJson.SettingsJsonException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_MANIFEST, "manifest.json: ${e.message}", e)
        }

        private fun checkCategoryId(id: String) {
            if (!SettingsArchive.isCategoryId(id)) invalid("invalid category id \"$id\"")
        }

        private fun checkDataFile(file: String) {
            val valid = file.startsWith(SettingsArchive.DATA_DIR) && runCatching { SettingsArchive.dataFile(file.removePrefix(SettingsArchive.DATA_DIR)) }.isSuccess
            if (!valid) invalid("invalid data file \"$file\"")
        }

        private fun checkPackageName(name: String) {
            if (!SettingsArchive.isPackageName(name)) invalid("invalid host package name \"$name\"")
        }

        private fun checkPluginId(id: String) {
            if (!SettingsArchive.isPackageName(id)) invalid("invalid plugin package name \"$id\"")
        }

        private fun checkUnique(name: String, values: List<String>) {
            val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) invalid("$name listed more than once: $duplicates")
        }

        private fun invalid(message: String): Nothing =
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_MANIFEST, "manifest.json: $message")

        private fun Map<*, *>.obj(name: String): Map<*, *> = this[name] as? Map<*, *> ?: invalid("no $name object")

        private fun Map<*, *>.str(name: String): String = this[name] as? String ?: invalid("no $name text")

        private fun Map<*, *>.int(name: String): Int = SettingsJson.toInt(this[name]) ?: invalid("no $name number")

        private fun Map<*, *>.count(name: String): Int = int(name).takeIf { it >= 0 } ?: invalid("a negative $name count")

        private fun Map<*, *>.list(name: String, optional: Boolean = false): List<Map<*, *>> {
            val list = this[name] as? List<*> ?: if (optional && this[name] == null) return emptyList() else invalid("no $name array")
            return list.map { it as? Map<*, *> ?: invalid("an element of $name is not an object") }
        }
    }
}
