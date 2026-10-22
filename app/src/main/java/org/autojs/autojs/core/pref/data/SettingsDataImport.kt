package org.autojs.autojs.core.pref.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.theme.app.ColorLibraryExchange
import org.autojs.autojs.theme.library.ColorLibraryRepository
import java.io.ByteArrayInputStream

/**
 * Imports the data files of the data categories (docs/settings/ROADMAP.md P10.2) the way their pages do: the shortcuts
 * merge into the shortcut backup ([ShortcutBackupRepository.import]; restoring them to the desktop and choosing their
 * missing icons stay on the page "Shortcut backup"), the color libraries become new user libraries, the groups of the
 * intelligent ones with new ids ([ColorLibraryExchange.importLibrary]). Runs off the main thread.
 *
 * zh-CN: 按各自页面的方式导入数据分类的数据文件 (ROADMAP P10.2): 快捷方式合并到快捷方式备份 ([ShortcutBackupRepository.import];
 * 恢复到桌面与补选缺失的图标仍在 "快捷方式备份" 页面中进行), 颜色库成为新的用户库, 智能库的组使用新的 id
 * ([ColorLibraryExchange.importLibrary]). 不在主线程调用.
 */
object SettingsDataImport {

    /**
     * What importing a data category [id] did: the [entries] of its file, the ones new to this device ([added]: shortcut
     * ids, or the row ids of the new color libraries), the shortcuts that can be restored to the desktop ([restorable])
     * and whether one of the imported shortcuts lacks its icon ([iconsMissing]); [failure] when the import stopped, in
     * which case [added] holds what was added before.
     * zh-CN: 导入数据分类 [id] 的结果: 文件中的条目数 [entries], 其中本机新增的条目 ([added]: 快捷方式 id 或新颜色库的行 id), 可恢复到
     * 桌面的快捷方式 ([restorable]), 导入的快捷方式中是否有缺少图标的 ([iconsMissing]); 导入中断时 [failure] 为原因, [added] 为中断前
     * 已新增的条目.
     */
    data class Result @JvmOverloads constructor(
        val id: String,
        val entries: Int,
        val added: List<String>,
        val restorable: List<String> = emptyList(),
        val iconsMissing: Boolean = false,
        val failure: String? = null,
    )

    /**
     * Imports [file]; [fileName] is the origin the new color libraries record (the name of the settings file). A file
     * that cannot be imported gives a [Result.failure] rather than an exception.
     * zh-CN: 导入 [file]; [fileName] 为新颜色库记录的来源 (设置文件名). 无法导入的文件返回带 [Result.failure] 的结果, 不抛异常.
     */
    @JvmStatic
    fun import(context: Context, file: SettingsArchiveWriter.DataFile, fileName: String?): Result = when (file.id) {
        ShortcutDataCodec.CATEGORY.id -> shortcuts(context.applicationContext, file)
        ColorLibraryDataCodec.CATEGORY.id -> colorLibraries(context.applicationContext, file, fileName)
        else -> throw IllegalArgumentException("Not a data category: ${file.id}")
    }

    private fun shortcuts(context: Context, file: SettingsArchiveWriter.DataFile): Result {
        val repository = ShortcutBackupRepository.get(context)
        return try {
            val before = runCatching { repository.reconcile() }.getOrElse { repository.read() }.entries.mapTo(hashSetOf()) { it.id }
            val imported = repository.import(ByteArrayInputStream(file.bytes)).toSet()
            val entries = repository.read().entries.filter { it.id in imported }
            Result(
                file.id,
                imported.size,
                entries.map { it.id }.filterNot { it in before },
                restorable = entries.filter { it.status != ShortcutBackupEntry.Status.PINNED }.map { it.id },
                iconsMissing = entries.any { it.icon == null },
            )
        } catch (e: Exception) {
            // A conflicting shortcut id or a locked device: the backup is left as it was.
            Result(file.id, file.entries, emptyList(), failure = messageOf(e))
        }
    }

    private fun colorLibraries(context: Context, file: SettingsArchiveWriter.DataFile, fileName: String?): Result {
        val added = mutableListOf<String>()
        return try {
            val libraries = SettingsDataEntries.libraries(file.bytes)
            val reserved = ColorLibraryExchange.reservedNames(context)
            val repository = ColorLibraryRepository.get(context)
            runBlocking { libraries.forEach { added += ColorLibraryExchange.importLibrary(repository, it, fileName, reserved).toString() } }
            Result(file.id, libraries.size, added.toList())
        } catch (e: Exception) {
            Result(file.id, file.entries, added.toList(), failure = messageOf(e))
        }
    }

    private fun messageOf(e: Exception) = e.message ?: e.javaClass.simpleName
}
