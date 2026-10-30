package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.theme.library.ColorLibraryJson
import java.time.ZoneId

/**
 * The entries of the data categories as the detail pages of the wizards list and choose them (docs/settings/ROADMAP.md
 * P10.2): the shortcuts of `data/shortcuts.zip` by their shortcut ids, the libraries of `data/color-library.json` by
 * their index in the file (user libraries only, the presets are never exported). [select] keeps the chosen entries of
 * a data file, written again by its codec. A selection is a list of entry ids per category id; a category without one
 * keeps every entry. Pure JVM (R3).
 *
 * zh-CN: 向导细化页列出与选择的数据分类条目 (ROADMAP P10.2): `data/shortcuts.zip` 中的快捷方式以快捷方式 id 标识,
 * `data/color-library.json` 中的库以其在文件中的下标标识 (只有用户库, 预设库从不导出). [select] 保留数据文件中选中的条目, 由对应
 * 编解码重新写出. 选择为每个分类 id 对应的条目 id 列表; 没有选择的分类保留全部条目. 纯 JVM (R3).
 */
object SettingsDataEntries {

    /** One entry: its [id] within the category, its [title] and an optional [detail] line. zh-CN: 一个条目. */
    data class Entry(val id: String, val title: String, val detail: String? = null)

    /**
     * The entries of the data file [bytes] of [category]; [SettingsArchiveException] for a file its page would refuse.
     * zh-CN: [category] 的数据文件 [bytes] 中的条目; 页面会拒绝的文件抛 [SettingsArchiveException].
     */
    @JvmStatic
    fun entries(category: String, bytes: ByteArray): List<Entry> = when (category) {
        ShortcutDataCodec.CATEGORY.id -> ShortcutDataCodec.decode(bytes).entries.map { Entry(it.id, it.shortLabel, it.path) }
        ColorLibraryDataCodec.CATEGORY.id -> libraries(bytes).mapIndexed { index, library -> Entry(index.toString(), library.name) }
        else -> throw IllegalArgumentException("Not a data category: $category")
    }

    /**
     * [file] with only the entries [ids] (all of them when null), written again with [time] as its export time; null
     * when no entry is left. Ids the file does not have are ignored.
     * zh-CN: 只保留条目 [ids] 的 [file] (为 null 时保留全部), 以 [time] 为导出时间重新写出; 没有剩下条目时为 null. 文件中没有的 id
     * 被忽略.
     */
    @JvmStatic
    @JvmOverloads
    fun select(
        file: SettingsArchiveWriter.DataFile,
        ids: Collection<String>?,
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        app: ColorLibraryJson.AppInfo? = null,
    ): SettingsArchiveWriter.DataFile? {
        if (file.entries == 0) return null
        if (ids == null) return file
        val chosen = ids.toSet()
        val selected = when (file.id) {
            ShortcutDataCodec.CATEGORY.id -> {
                val state = ShortcutDataCodec.decode(file.bytes)
                ShortcutDataCodec.encode(state.copy(entries = state.entries.filter { it.id in chosen }), time)
            }
            ColorLibraryDataCodec.CATEGORY.id -> {
                val libraries = libraries(file.bytes).filterIndexed { index, _ -> index.toString() in chosen }
                ColorLibraryDataCodec.encode(libraries, time, zone, app)
            }
            else -> throw IllegalArgumentException("Not a data category: ${file.id}")
        }
        return selected.takeIf { it.entries > 0 }
    }

    /**
     * The color libraries of the data file [bytes] of `data_color_library`; [SettingsArchiveException] for a file the
     * page would refuse.
     * zh-CN: `data_color_library` 数据文件 [bytes] 中的颜色库; 页面会拒绝的文件抛 [SettingsArchiveException].
     */
    @JvmStatic
    fun libraries(bytes: ByteArray): List<ColorLibraryJson.Library> = try {
        ColorLibraryDataCodec.decode(bytes).libraries.map { it.library }
    } catch (e: ColorLibraryJson.ColorLibraryJsonException) {
        throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "data/${ColorLibraryDataCodec.FILE_NAME}: ${e.reason}", e)
    }

    /** True for the categories whose entries [select] chooses. zh-CN: [select] 可逐条选择的分类为 true. */
    @JvmStatic
    fun isRefinable(category: SettingCategory): Boolean = category == ShortcutDataCodec.CATEGORY || category == ColorLibraryDataCodec.CATEGORY
}
