package org.autojs.autojs.core.pref.archive

import android.content.Context
import android.os.Build
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Mechanism
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Outcome
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.BuildConfig
import java.io.File
import java.io.IOException

/**
 * The automatic backups of the app (docs/settings/ROADMAP.md D13 / D22 / T20, P9): [SettingsAutoBackup] in
 * `<working directory>/backups/settings/`, holding the settings of [SettingsAutoBackup.CATEGORIES] as the stores have
 * them now. The data files of the data categories join once P10.2 gathers them; the switches of the three mechanisms
 * (`general` settings, T20) join with the pages, when [write] returns [Outcome.Off] for a mechanism switched off.
 *
 * zh-CN: 应用的自动备份 (ROADMAP D13 / D22 / T20, P9): 以 [SettingsAutoBackup] 写入 `<工作目录>/backups/settings/`, 内容为
 * [SettingsAutoBackup.CATEGORIES] 当前的存储值. 数据分类的文件待 P10.2 提供采集后加入; 三种机制的开关 (`general` 分类的设置, T20) 随页面
 * 接入, 届时关闭的机制由 [write] 返回 [Outcome.Off].
 */
object AndroidSettingsAutoBackup {

    /** `backups/settings/` under the working directory. zh-CN: 工作目录下的 `backups/settings/`. */
    @JvmStatic
    fun directory(): File = SettingsAutoBackup.directoryIn(File(WorkingDirectoryUtils.path))

    /**
     * The operation backup (T20): call it once an import, a reset to defaults or an undo is done and before its result
     * page shows, off the main thread. It throws no I/O error: the result page tells [Outcome.Failed] without blocking.
     * zh-CN: 操作后备份 (T20): 在导入, 恢复默认或撤销完成之后, 结果页显示之前调用, 不在主线程. 不抛 I/O 错误: 结果页提示
     * [Outcome.Failed], 不阻断.
     */
    @JvmStatic
    fun afterOperation(context: Context): Outcome = write(context, Mechanism.OPERATION)

    /**
     * A backup of [mechanism] now, off the main thread; the workers of the periodic (P9.2) and change (P9.3) backups
     * call it too. Throws no I/O error ([Outcome.Failed]).
     * zh-CN: 立即写一份 [mechanism] 的备份, 不在主线程调用; 周期备份 (P9.2) 与变更后备份 (P9.3) 的任务也调用它. 不抛 I/O 错误
     * ([Outcome.Failed]).
     */
    @JvmStatic
    fun write(context: Context, mechanism: Mechanism): Outcome = try {
        val snapshot = SettingsSnapshot.capture(SettingsAutoBackup.CATEGORIES)
        SettingsAutoBackup(directory()).tryWrite(mechanism, snapshot, host(context), device())
    } catch (e: SecurityException) {
        Outcome.Failed(IOException(e.message, e))
    }

    private fun host(context: Context) = SettingsArchiveManifest.Host(context.packageName, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    private fun device() = SettingsArchiveManifest.Device(Build.MODEL.orEmpty(), Build.VERSION.SDK_INT)
}
