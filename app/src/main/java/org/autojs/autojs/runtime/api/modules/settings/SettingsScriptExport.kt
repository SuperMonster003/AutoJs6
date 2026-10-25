package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.ui.settings.transfer.SettingsExporter
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.File
import java.io.IOException
import java.time.ZoneId

/**
 * The pure part of `settings.export(path?, categories?)` and `settings.categories()` (docs/settings/ROADMAP.md D12 / T15,
 * P12.1): the arguments, the file to write and the archive, written by the pipeline of the export wizard
 * ([SettingsExporter.write], P6.4) so that it is the archive of appendix B. Scripts never export `credentials` (T15 /
 * R6): a request for it is reported in [Selection.skipped], and the archive holds no sealed file. A data category
 * without entries is left out as the wizard and the automatic backups leave it out ([SkipReason.EMPTY]). Pure JVM (R3):
 * the snapshot, the data files, the host and the clock come from [Settings].
 *
 * zh-CN: `settings.export(path?, categories?)` 与 `settings.categories()` 的纯逻辑部分 (ROADMAP D12 / T15, P12.1): 参数, 写入的文件与
 * 归档; 归档由导出向导的管线 ([SettingsExporter.write], P6.4) 写出, 即附录 B 的格式. 脚本不导出 `credentials` (T15 / R6): 请求它时记入
 * [Selection.skipped], 归档中没有加密文件. 没有条目的数据分类与向导和自动备份一样略去 ([SkipReason.EMPTY]). 纯 JVM (R3): 快照, 数据文件,
 * 宿主与时钟由 [Settings] 提供.
 */
internal object SettingsScriptExport {

    /** Why a requested category is not in the archive. zh-CN: 请求的分类没有写入归档的原因. */
    enum class SkipReason(val label: String) {

        /** `credentials` (T15 / R6): never exported by scripts. zh-CN: `credentials`, 脚本从不导出. */
        SENSITIVE("sensitive"),

        /** A data category without entries now. zh-CN: 当前没有条目的数据分类. */
        EMPTY("empty"),
    }

    data class Skip(val id: String, val reason: SkipReason)

    /** The categories to write, in page order, and those requested but left out. zh-CN: 要写入的分类 (页面顺序) 与请求了但略去的分类. */
    data class Selection(val categories: List<SettingCategory>, val skipped: List<Skip>)

    /**
     * What was written: [categories] are the ids in the archive (settings and data, page order), [entries] their entries,
     * [unexportable] the stored values that have no type tag (as the done page of the wizard counts them).
     * zh-CN: 写入结果: [categories] 为归档中的分类 id (设置与数据, 页面顺序), [entries] 为其条目数, [unexportable] 为没有类型标签的
     * 存储值个数 (与向导完成页的计数相同).
     */
    data class Result(val file: File, val categories: List<String>, val entries: Int, val unexportable: Int, val skipped: List<Skip>)

    /**
     * The categories exported when the script names none: those selected by default in the export wizard (appendix A.1),
     * data categories included; never `credentials`, `developer` nor `other`.
     * zh-CN: 脚本未指定分类时导出的分类: 导出向导默认勾选的分类 (附录 A.1), 含数据分类; 不含 `credentials`, `developer` 与 `other`.
     */
    @JvmField
    val DEFAULT_CATEGORIES: List<SettingCategory> = SettingCategory.ALL.filter { it.defaultSelected }

    /**
     * The path of the script argument [value]: null when nullish (the default directory), else a string that is not
     * blank; anything else is an [IllegalArgumentException]. It is resolved against the script's directory by the caller.
     * zh-CN: 脚本参数 [value] 中的路径: null / undefined 时为 null (默认目录), 否则为非空白字符串; 其他值抛 [IllegalArgumentException].
     * 由调用方按脚本所在目录解析.
     */
    @JvmStatic
    fun pathOf(value: Any?): String? {
        val raw = unwrap(value)
        if (isNullish(raw)) return null
        val path = (raw as? CharSequence)?.toString() ?: throw IllegalArgumentException("settings.export: path must be a string")
        require(path.isNotBlank()) { "settings.export: path cannot be blank" }
        return path
    }

    /**
     * The category ids of the script argument [value]: null when nullish (the default categories), a string as one id,
     * an array (or a Java list or array) of strings as given. Anything else, a non-string element or an empty array is
     * an [IllegalArgumentException].
     * zh-CN: 脚本参数 [value] 中的分类 id: null / undefined 时为 null (默认分类), 字符串为一个 id, 字符串数组 (或 Java 列表, 数组) 按原样.
     * 其他值, 非字符串元素或空数组抛 [IllegalArgumentException].
     */
    @JvmStatic
    fun categoryIds(value: Any?): List<String>? {
        val raw = unwrap(value)
        if (isNullish(raw)) return null
        val elements: List<Any?> = when (raw) {
            is CharSequence -> return listOf(raw.toString())
            is NativeArray -> raw.toList()
            is List<*> -> raw
            is Array<*> -> raw.toList()
            else -> throw IllegalArgumentException("settings.export: categories must be a string or an array of strings")
        }
        require(elements.isNotEmpty()) { "settings.export: categories cannot be an empty array" }
        return elements.mapIndexed { index, element ->
            unwrap(element) as? CharSequence ?: throw IllegalArgumentException("settings.export: categories[$index] must be a string")
        }.map { it.toString() }
    }

    /**
     * The [Selection] of [ids] (null for [DEFAULT_CATEGORIES]): known ids only, each once, in page order; `credentials` is
     * skipped as [SkipReason.SENSITIVE]. An unknown id, or nothing left to export, is an [IllegalArgumentException].
     * zh-CN: [ids] 的 [Selection] (为 null 时为 [DEFAULT_CATEGORIES]): 只接受已知 id, 去重, 按页面顺序; `credentials` 以
     * [SkipReason.SENSITIVE] 略去. 未知 id 或没有可导出的分类时抛 [IllegalArgumentException].
     */
    @JvmStatic
    @JvmOverloads
    fun select(ids: List<String>?, all: List<SettingCategory> = SettingCategory.ALL): Selection {
        if (ids == null) return Selection(DEFAULT_CATEGORIES.filter { it in all }, emptyList())
        val known = all.associateBy { it.id }
        val unknown = ids.filter { it !in known }
        require(unknown.isEmpty()) {
            "settings.export: unknown categories ${unknown.joinToString { "\"$it\"" }}; known: ${all.joinToString { it.id }}"
        }
        val requested = ids.toSet()
        val chosen = all.filter { it.id in requested }
        val (sensitive, categories) = chosen.partition { it.sensitive }
        require(categories.isNotEmpty()) { "settings.export: scripts cannot export ${sensitive.joinToString { it.id }}, nothing else is requested" }
        return Selection(categories, sensitive.map { Skip(it.id, SkipReason.SENSITIVE) })
    }

    /**
     * The archive file for [path], already resolved against the script's directory (`files.path`): null writes to
     * `<working directory>/backups/settings/` ([workingDirectory], T15 / T20), a directory (an existing one, or a path
     * ending with "/") gets the default file name of D5 there; both add `-1`, `-2`, ... instead of replacing a file of the
     * same second. Any other path is the file itself, replaced if it exists.
     * zh-CN: [path] 对应的归档文件 ([path] 已按脚本所在目录解析): 为 null 时写入 `<工作目录>/backups/settings/` (T15 / T20); 目录 (已存在,
     * 或以 "/" 结尾) 在其中使用 D5 的默认文件名; 这两种情况遇到同一秒的同名文件时追加 `-1`, `-2` 等而不替换. 其他路径即文件本身, 已存在时
     * 替换.
     */
    @JvmStatic
    @JvmOverloads
    fun target(path: String?, workingDirectory: File, now: Long, zone: ZoneId = ZoneId.systemDefault()): File {
        val directory = when {
            path == null -> SettingsAutoBackup.directoryIn(workingDirectory)
            path.endsWith('/') || File(path).isDirectory -> File(path)
            else -> return File(path)
        }
        val name = SettingsArchive.defaultFileName(now, zone).removeSuffix(ZIP)
        return generateSequence(0) { it + 1 }
            .map { File(directory, if (it == 0) "$name$ZIP" else "$name-$it$ZIP") }
            .first { !it.exists() }
    }

    /**
     * Writes the settings of [snapshot] (`SettingsSnapshot.capture(selection.categories)`) and the [data] files of the data
     * categories of [selection] to [file] as exported at [now], through a temporary file next to it, so that no partial
     * archive is left behind; the data categories of [selection] without a data file are skipped as
     * [SkipReason.EMPTY]. Throws [IOException] (or `SettingsArchiveException.TOO_LARGE`) when that fails.
     * zh-CN: 把 [snapshot] 的设置与 [selection] 中数据分类的数据文件 [data] 以导出时间 [now] 写入 [file], 先写同目录的临时文件, 不留下不完整
     * 的归档; [selection] 中没有数据文件的数据分类记为 [SkipReason.EMPTY]. 失败时抛 [IOException] (或 `SettingsArchiveException` 的
     * `TOO_LARGE`).
     */
    @JvmStatic
    fun write(
        file: File,
        selection: Selection,
        snapshot: SettingsSnapshot,
        data: List<SettingsArchiveWriter.DataFile>,
        now: Long,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
    ): Result {
        val wanted = selection.categories.map { it.id }.toSet()
        val settings = SettingsSnapshot(snapshot.categories.filter { it.id in wanted }, snapshot.unexportable)
        val files = data.filter { it.id in wanted }
        val target = file.absoluteFile
        val directory = target.parentFile ?: throw IOException("No directory for $target")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val temp = File.createTempFile(".autojs6-settings-", ".tmp", directory)
        try {
            temp.outputStream().use { SettingsExporter.write(it, settings, now, host, device, data = files) }
            if (target.exists() && !target.delete()) throw IOException("Cannot replace $target")
            if (!temp.renameTo(target)) throw IOException("Cannot rename $temp to $target")
        } finally {
            temp.delete()
        }
        val written = settings.categories.map { it.id }.toSet() + files.map { it.id }
        val empty = selection.categories.filter { it.isData && it.id !in written }.map { Skip(it.id, SkipReason.EMPTY) }
        return Result(
            target,
            selection.categories.map { it.id }.filter { it in written },
            settings.categories.sumOf { it.entries } + files.sumOf { it.entries },
            settings.unexportable.size,
            selection.skipped + empty,
        )
    }

    /**
     * The entries of every category for `settings.categories()`, in page order: those of [snapshot] (all settings
     * categories) and of [data] (the data categories with entries); 0 for the rest.
     * zh-CN: `settings.categories()` 中每个分类的条目数, 按页面顺序: 取自 [snapshot] (全部设置分类) 与 [data] (有条目的数据分类), 其余为 0.
     */
    @JvmStatic
    @JvmOverloads
    fun entries(
        snapshot: SettingsSnapshot,
        data: List<SettingsArchiveWriter.DataFile>,
        all: List<SettingCategory> = SettingCategory.ALL,
    ): List<Pair<SettingCategory, Int>> {
        val counts = snapshot.categories.associate { it.id to it.entries } + data.associate { it.id to it.entries }
        return all.map { it to (counts[it.id] ?: 0) }
    }

    private const val ZIP = ".zip"

    private fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value)
}
