package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson

/**
 * `manifest.json` of a settings archive (docs/settings/ROADMAP.md D5 / D9 / T7, appendix B): the format and its
 * version, the export time, the host and device, and per category / data category / plugin the number of entries and
 * the file that holds them. The reader lists the categories from it before any category file is opened (T7).
 *
 * [plugins] is reserved for the settings of official plugins (D3, appendix C Q7): the first phase writes an empty
 * array, and the entries of a newer archive are kept as plain JSON objects. Reading is lenient (D9): unknown fields are
 * ignored, and a [formatVersion] above [FORMAT_VERSION] is accepted ([isNewerFormat], the import wizard warns).
 *
 * zh-CN: 设置归档的 `manifest.json` (ROADMAP D5 / D9 / T7, 附录 B): 格式与版本, 导出时间, 宿主与设备, 以及每个分类 / 数据分类 /
 * 插件的条目数与所在文件. 读取时先由它列出分类, 再打开分类文件 (T7). [plugins] 为官方插件设置预留 (D3, 附录 C Q7): 第一期写空数组,
 * 更新版本归档中的条目按普通 JSON 对象保留. 读取宽松 (D9): 忽略未知字段, 接受高于 [FORMAT_VERSION] 的 [formatVersion]
 * ([isNewerFormat], 由导入向导提示).
 */
data class SettingsArchiveManifest @JvmOverloads constructor(
    val exportedAt: String,
    val host: Host,
    val device: Device,
    val categories: List<CategoryEntry>,
    val data: List<DataEntry> = emptyList(),
    val plugins: List<Map<String, Any?>> = emptyList(),
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

    /** True for an archive of a newer format than this version reads (D9). zh-CN: 归档格式比本版本新时为 true (D9). */
    val isNewerFormat: Boolean
        get() = formatVersion > FORMAT_VERSION

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "format" to FORMAT,
        "formatVersion" to formatVersion,
        "exportedAt" to exportedAt,
        "host" to linkedMapOf("packageName" to host.packageName, "versionCode" to host.versionCode, "versionName" to host.versionName),
        "device" to linkedMapOf("model" to device.model, "sdk" to device.sdk),
        "categories" to categories.map { linkedMapOf("id" to it.id, "entries" to it.entries, "sensitive" to it.sensitive) },
        "data" to data.map { linkedMapOf("id" to it.id, "entries" to it.entries, "file" to it.file) },
        "plugins" to plugins,
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
            return SettingsArchiveManifest(
                exportedAt = root.str("exportedAt"),
                host = Host(host.str("packageName"), host.int("versionCode"), host.str("versionName")),
                device = Device(device.str("model"), device.int("sdk")),
                categories = root.list("categories").map { CategoryEntry(it.str("id").also(::checkCategoryId), it.int("entries"), it["sensitive"] as? Boolean ?: invalid("no sensitive flag")) },
                data = root.list("data", optional = true).map { DataEntry(it.str("id"), it.int("entries"), it.str("file")) },
                plugins = root.list("plugins", optional = true).map { entry -> entry.entries.associateTo(LinkedHashMap()) { (k, v) -> k as String to v } },
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

        private fun invalid(message: String): Nothing =
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_MANIFEST, "manifest.json: $message")

        private fun Map<*, *>.obj(name: String): Map<*, *> = this[name] as? Map<*, *> ?: invalid("no $name object")

        private fun Map<*, *>.str(name: String): String = this[name] as? String ?: invalid("no $name text")

        private fun Map<*, *>.int(name: String): Int = SettingsJson.toInt(this[name]) ?: invalid("no $name number")

        private fun Map<*, *>.list(name: String, optional: Boolean = false): List<Map<*, *>> {
            val list = this[name] as? List<*> ?: if (optional && this[name] == null) return emptyList() else invalid("no $name array")
            return list.map { it as? Map<*, *> ?: invalid("an element of $name is not an object") }
        }
    }
}
