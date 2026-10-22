package org.autojs.autojs.core.pref.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs6.BuildConfig

/**
 * The data files of the data categories (docs/settings/ROADMAP.md D21 / T19) as their pages would export them now:
 * `data/shortcuts.zip` as the page "Shortcut backup" exports it ([ShortcutDataCodec]), and `data/color-library.json`
 * with the user color libraries, plain and intelligent, as the page "Color libraries" exports them
 * ([ColorLibraryDataCodec], the presets are not exported). The automatic backups (T20, P9.1) take them with the
 * settings.
 *
 * zh-CN: 数据分类 (ROADMAP D21 / T19) 当前按各自页面导出所得的数据文件: `data/shortcuts.zip` 同 "快捷方式备份" 页面的导出
 * ([ShortcutDataCodec]), `data/color-library.json` 为全部用户颜色库 (含普通库与智能库), 同 "颜色库" 页面的导出 ([ColorLibraryDataCodec],
 * 预设库不导出). 自动备份 (T20, P9.1) 与设置一起写入它们.
 */
object SettingsDataCapture {

    private const val TAG = "SettingsDataCapture"

    /**
     * The data files of the data categories among [categories], [time] being the export time of the archive; call it
     * off the main thread. A category that cannot be read is left out and logged, so that the settings are backed up
     * all the same; the shortcuts are read as stored while the device is locked.
     * zh-CN: [categories] 中数据分类的数据文件, [time] 为归档的导出时间; 不在主线程调用. 无法读取的分类会被略过并记录日志, 设置照常备份;
     * 设备锁定时快捷方式按已存储的状态读取.
     */
    @JvmStatic
    fun capture(context: Context, categories: Collection<SettingCategory>, time: Long): List<SettingsArchiveWriter.DataFile> {
        val app = context.applicationContext
        return listOfNotNull(
            SettingCategory.DATA_SHORTCUTS.takeIf { it in categories }?.let { category -> read(category) { shortcuts(app, time) } },
            SettingCategory.DATA_COLOR_LIBRARY.takeIf { it in categories }?.let { category -> read(category) { colorLibraries(app, time) } },
        )
    }

    private fun read(category: SettingCategory, read: () -> SettingsArchiveWriter.DataFile) = runCatching(read)
        .onFailure { Log.w(TAG, "Data category ${category.id} is left out: $it") }
        .getOrNull()

    private fun shortcuts(context: Context, time: Long): SettingsArchiveWriter.DataFile {
        val repository = ShortcutBackupRepository.get(context)
        // The page reconciles with the pinned shortcuts first, which needs the device unlocked.
        val state = runCatching { repository.reconcile() }.getOrElse { repository.read() }
        return ShortcutDataCodec.encode(state, time)
    }

    private fun colorLibraries(context: Context, time: Long): SettingsArchiveWriter.DataFile = runBlocking {
        val repository = ColorLibraryRepository.get(context)
        val libraries = repository.listLibraries().mapNotNull { library ->
            when {
                library.isIntelligent -> repository.loadIntelligent(library.id)?.let {
                    ColorLibraryJson.IntelligentLibrary(it.name, it.config, it.groups)
                }
                else -> ColorLibraryJson.PlainLibrary(
                    library.name,
                    repository.listColors(library.id).map { ColorLibraryJson.Color(it.color, it.name, it.createdTime, it.modifiedTime) },
                )
            }
        }
        ColorLibraryDataCodec.encode(libraries, time, app = ColorLibraryJson.AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
    }
}
