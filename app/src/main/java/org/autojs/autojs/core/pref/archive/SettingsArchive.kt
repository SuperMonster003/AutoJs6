package org.autojs.autojs.core.pref.archive

import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The layout of a settings archive, format v1 (docs/settings/ROADMAP.md D5 / T7, appendix B): a zip of [MANIFEST],
 * `categories/<id>.json`, `data/<file>` and `plugins/<packageName>/settings.json` (D3, P13.1; none in the first phase),
 * at most [MAX_BYTES] and named `autojs6-settings-<yyyymmdd-hhmmss>.zip` by default. Pure JVM (R3).
 *
 * zh-CN: 设置归档 v1 的结构 (ROADMAP D5 / T7, 附录 B): zip 内含 [MANIFEST], `categories/<id>.json`, `data/<文件>` 与
 * `plugins/<包名>/settings.json` (D3, P13.1; 第一期没有), 大小不超过 [MAX_BYTES], 默认文件名为
 * `autojs6-settings-<yyyymmdd-hhmmss>.zip`. 纯 JVM (R3).
 */
object SettingsArchive {

    const val MANIFEST = "manifest.json"
    const val CATEGORIES_DIR = "categories/"
    const val DATA_DIR = "data/"
    const val PLUGINS_DIR = "plugins/"

    /** The file of a plugin directory (D3, P13.1 / P13.2). zh-CN: 插件目录中的文件名 (D3, P13.1 / P13.2). */
    const val PLUGIN_SETTINGS_FILE = "settings.json"

    /** The limit of an archive, and of every file in it once inflated (T7). zh-CN: 归档及其中每个文件解压后的大小上限 (T7). */
    const val MAX_BYTES: Long = 16L * 1024 * 1024

    private val CATEGORY_ID = Regex("[a-z][a-z0-9_]*")

    private val DATA_FILE_NAME = Regex("[a-z0-9][a-z0-9._-]*")

    /** An application id: two or more dot-separated Java identifiers. */
    private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

    private val EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.ROOT)

    private val FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    /**
     * True for a category id: the pattern of `SettingCategory`, which also holds for the ids of newer versions.
     * zh-CN: 是否为分类 id: 即 `SettingCategory` 的模式, 更新版本的 id 同样适用.
     */
    @JvmStatic
    fun isCategoryId(id: String): Boolean = CATEGORY_ID.matches(id)

    /** The file of the category [id]: `categories/<id>.json`. */
    @JvmStatic
    fun categoryFile(id: String): String {
        require(isCategoryId(id)) { "Invalid category id \"$id\"" }
        return "$CATEGORIES_DIR$id.json"
    }

    /** The file of a data category: `data/<name>`, where [name] is lower case without directories. */
    @JvmStatic
    fun dataFile(name: String): String {
        require(DATA_FILE_NAME.matches(name)) { "Invalid data file name \"$name\"" }
        return DATA_DIR + name
    }

    /**
     * True for a package name (of the host or of a plugin): an application id, so that it names one directory.
     * zh-CN: 是否为包名 (宿主或插件): 即 application id, 因此只对应一级目录.
     */
    @JvmStatic
    fun isPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

    /** The settings file of the plugin [packageName] (D3): `plugins/<packageName>/settings.json`. */
    @JvmStatic
    fun pluginFile(packageName: String): String {
        require(isPackageName(packageName)) { "Invalid plugin package name \"$packageName\"" }
        return "$PLUGINS_DIR$packageName/$PLUGIN_SETTINGS_FILE"
    }

    /**
     * The time of an export as the manifest writes it (appendix B), for example `2026-10-20T15:30:00+08:00`.
     * zh-CN: manifest 中的导出时间 (附录 B), 例如 `2026-10-20T15:30:00+08:00`.
     */
    @JvmStatic
    @JvmOverloads
    fun exportTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = EXPORT_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    /**
     * The default file name of an export (D5), for example `autojs6-settings-20261020-153000.zip`.
     * zh-CN: 导出的默认文件名 (D5), 例如 `autojs6-settings-20261020-153000.zip`.
     */
    @JvmStatic
    @JvmOverloads
    fun defaultFileName(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "autojs6-settings-" + FILE_NAME_TIME.format(Instant.ofEpochMilli(millis).atZone(zone)) + ".zip"
}

/**
 * Why a settings archive cannot be written or read (T7); the wizards (P6.4 / P7) show a message per [reason].
 * zh-CN: 设置归档无法写入或读取的原因 (T7); 向导 (P6.4 / P7) 按 [reason] 显示提示.
 */
class SettingsArchiveException @JvmOverloads constructor(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    enum class Reason {
        /** The archive, or a file in it once inflated, is larger than [SettingsArchive.MAX_BYTES]. */
        TOO_LARGE,

        /** Not a zip, or a zip without any file. */
        NOT_AN_ARCHIVE,

        /** `manifest.json` is malformed or of another format. */
        INVALID_MANIFEST,

        /** `manifest.json` or a file it lists is missing. */
        MISSING_FILE,

        /** A category file is malformed or names another category, or a sealed one is altered (D23). */
        INVALID_FILE,

        /** A sealed category file has no slot of the key given: another device, another signature, or no key (D23). */
        LOCKED,
    }
}
