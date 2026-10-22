package org.autojs.autojs.core.pref.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsBackupVault
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.theme.app.ColorLibraryExchange
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.ColorSelection
import java.io.ByteArrayInputStream

/**
 * Imports the data files of the data categories (docs/settings/ROADMAP.md P10.2) the way their pages do: the shortcuts
 * merge into the shortcut backup ([ShortcutBackupRepository.import]; restoring them to the desktop and choosing their
 * missing icons stay on the page "Shortcut backup"), the color libraries become new user libraries, the groups of the
 * intelligent ones with new ids ([ColorLibraryExchange.importLibrary]). Undoing the import deletes the entries it added
 * ([remove], P10.3). Runs off the main thread.
 *
 * zh-CN: 按各自页面的方式导入数据分类的数据文件 (ROADMAP P10.2): 快捷方式合并到快捷方式备份 ([ShortcutBackupRepository.import];
 * 恢复到桌面与补选缺失的图标仍在 "快捷方式备份" 页面中进行), 颜色库成为新的用户库, 智能库的组使用新的 id
 * ([ColorLibraryExchange.importLibrary]). 撤销导入时删除其新增的条目 ([remove], P10.3). 不在主线程调用.
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

    /**
     * What undoing the import of the data category [id] did (P10.3): of the [ids] the import added, [removed] are
     * deleted and [kept] stay (shortcuts on the desktop; the ones deleted since are neither); [selectionRemoved] when a
     * deleted color library held the current color, [intelligentRemoved] when it was the active intelligent library,
     * which the caller falls back from on the main thread; [failure] when the removal stopped, in which case [removed]
     * holds what was deleted before.
     * zh-CN: 撤销数据分类 [id] 的导入的结果 (P10.3): 导入新增的 [ids] 中删除了 [removed] 项, 保留 [kept] 项 (在桌面上的快捷方式; 其间
     * 已删除的两者都不算); 删除的颜色库持有当前颜色时 [selectionRemoved] 为 true, 是激活的智能库时 [intelligentRemoved] 为 true, 由调用方在
     * 主线程回退; 删除中断时 [failure] 为原因, [removed] 为中断前已删除的项数.
     */
    data class Removal @JvmOverloads constructor(
        val id: String,
        val ids: List<String>,
        val removed: Int,
        val kept: Int = 0,
        val selectionRemoved: Boolean = false,
        val intelligentRemoved: Boolean = false,
        val failure: String? = null,
    )

    /**
     * Deletes the entries [ids] the import added to the data category [id] ([SettingsBackupVault.LastOperation.added]):
     * the shortcuts not on the desktop, and the color libraries by their row ids. A removal that fails gives a
     * [Removal.failure] rather than an exception.
     * zh-CN: 删除导入在数据分类 [id] 中新增的条目 [ids]: 不在桌面上的快捷方式, 以及按行 id 删除颜色库. 删除失败时返回带
     * [Removal.failure] 的结果, 不抛异常.
     */
    @JvmStatic
    fun remove(context: Context, id: String, ids: List<String>): Removal = when (id) {
        ShortcutDataCodec.CATEGORY.id -> removeShortcuts(context.applicationContext, ids)
        ColorLibraryDataCodec.CATEGORY.id -> removeColorLibraries(context.applicationContext, ids)
        else -> throw IllegalArgumentException("Not a data category: $id")
    }

    private fun removeShortcuts(context: Context, ids: List<String>): Removal {
        val repository = ShortcutBackupRepository.get(context)
        return try {
            val removed = repository.remove(ids.toSet())
            Removal(ShortcutDataCodec.CATEGORY.id, ids, removed.size, kept = repository.read().entries.count { it.id in ids })
        } catch (e: Exception) {
            Removal(ShortcutDataCodec.CATEGORY.id, ids, 0, failure = messageOf(e))
        }
    }

    private fun removeColorLibraries(context: Context, ids: List<String>): Removal {
        val repository = ColorLibraryRepository.get(context)
        val selected = (ColorSelectBaseActivity.currentColorSelection() as? ColorSelection.UserColor)?.libraryRowId
        val active = IntelligentColorEngine.activeSnapshot?.libraryId
        var removed = 0
        var selectionRemoved = false
        var intelligentRemoved = false
        fun removal(failure: String? = null) = Removal(ColorLibraryDataCodec.CATEGORY.id, ids, removed, 0, selectionRemoved, intelligentRemoved, failure)
        return try {
            runBlocking {
                ids.mapNotNull { it.toLongOrNull() }.forEach { rowId ->
                    // A library deleted since is skipped.
                    val library = repository.findLibrary(rowId) ?: return@forEach
                    repository.deleteLibrary(library)
                    removed += 1
                    if (rowId == selected) selectionRemoved = true
                    if (library.isIntelligent && ColorLibraryIds.ofUserLibrary(rowId, intelligent = true) == active) intelligentRemoved = true
                }
            }
            removal()
        } catch (e: Exception) {
            removal(messageOf(e))
        }
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
