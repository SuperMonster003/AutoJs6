package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.external.shortcut.ShortcutBackupArchive
import org.autojs.autojs.external.shortcut.ShortcutBackupState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The data category `data_shortcuts` of a settings archive (docs/settings/ROADMAP.md D21 / T19): `data/shortcuts.zip`
 * is the export of the page "Shortcut backup" as it is, written by the same [ShortcutBackupArchive.writeExport], so
 * that for one state and one time both are equal byte for byte and either one can be imported by both. The manifest's
 * `data` lists the number of shortcuts. Choosing shortcuts and restoring them (`ShortcutBackupReconciler`, the missing
 * icons) is left to the detail pages of the wizards (P10.2). Pure JVM (R3).
 *
 * zh-CN: 设置归档的数据分类 `data_shortcuts` (ROADMAP D21 / T19): `data/shortcuts.zip` 即 "快捷方式备份" 页面导出的原样文件, 与之
 * 由同一个 [ShortcutBackupArchive.writeExport] 写出, 因此同一状态与同一时间下两者逐字节相同, 也都可以由任一方导入. manifest 的 `data`
 * 记录快捷方式的条数. 逐条选择与恢复 (`ShortcutBackupReconciler`, 缺图标补选) 由向导的细化页处理 (P10.2). 纯 JVM (R3).
 */
internal object ShortcutDataCodec {

    @JvmField
    val CATEGORY: SettingCategory = SettingCategory.DATA_SHORTCUTS

    /** The file of the category under `data/`. zh-CN: 该分类在 `data/` 下的文件名. */
    const val FILE_NAME = "shortcuts.zip"

    /**
     * The data file of [state] for [SettingsArchiveWriter.write]; [time] is the time of the ZIP entries (the export
     * time of the archive), the current time when null, as the page writes it.
     * zh-CN: [state] 对应的数据文件, 交给 [SettingsArchiveWriter.write]; [time] 为 ZIP 条目的时间 (归档的导出时间), 为 null 时与页面
     * 一样取当前时间.
     */
    @JvmStatic
    @JvmOverloads
    fun encode(state: ShortcutBackupState, time: Long? = null): SettingsArchiveWriter.DataFile {
        val bytes = ByteArrayOutputStream().also { ShortcutBackupArchive.writeExport(state, it, time) }.toByteArray()
        return SettingsArchiveWriter.DataFile(CATEGORY.id, state.entries.size, FILE_NAME, bytes)
    }

    /**
     * The shortcuts of `data/shortcuts.zip` (or of a file the page exported); a file the page would refuse is
     * [SettingsArchiveException.Reason.INVALID_FILE].
     * zh-CN: `data/shortcuts.zip` (或页面导出的文件) 中的快捷方式; 页面会拒绝的文件为 `INVALID_FILE`.
     */
    @JvmStatic
    fun decode(bytes: ByteArray): ShortcutBackupState = try {
        ShortcutBackupArchive.read(ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "data/$FILE_NAME: ${e.message}", e)
    }
}
