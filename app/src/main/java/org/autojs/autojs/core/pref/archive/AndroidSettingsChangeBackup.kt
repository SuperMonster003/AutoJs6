package org.autojs.autojs.core.pref.archive

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Mechanism
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import java.util.concurrent.TimeUnit

/**
 * The change backup of the app (docs/settings/ROADMAP.md D22 / T20, P9.3): [SettingsChangeDebounce] on the changes of
 * [SettingsStores], scheduling [ChangeBackupWorker] as a unique one-time WorkManager work, which writes the backup through
 * [AndroidSettingsAutoBackup]. Nothing listens until [start]: [AndroidSettingsAutoBackup.start] calls [start] and [stop]
 * as the switch of the change backup (off by default, a `general` setting) goes, at the app start and whenever it changes.
 *
 * zh-CN: 应用的变更后备份 (ROADMAP D22 / T20, P9.3): 以 [SettingsChangeDebounce] 监听 [SettingsStores] 的变化, 把 [ChangeBackupWorker] 安排为唯一的
 * WorkManager 一次性任务, 由它经 [AndroidSettingsAutoBackup] 写备份. 调用 [start] 之前不监听: [AndroidSettingsAutoBackup.start] 在应用启动时以及
 * 变更后备份的开关 (默认关, `general` 分类的设置) 变化时, 按开关调用 [start] 或 [stop].
 */
object AndroidSettingsChangeBackup {

    /** The unique work name. zh-CN: 唯一任务名. */
    const val WORK_NAME = "settings_change_backup"

    private var debounce: SettingsChangeDebounce? = null

    private var subscription: AutoCloseable? = null

    /** Starts listening to the changes of the settings. zh-CN: 开始监听设置的变化. */
    @JvmStatic
    fun start(context: Context) = synchronized(this) {
        if (subscription == null) subscription = SettingsStores.addChangeListener(debounce(context))
    }

    /** Stops listening and cancels a pending backup. zh-CN: 停止监听并取消尚未执行的备份. */
    @JvmStatic
    fun stop(context: Context) = synchronized(this) {
        subscription?.close()
        subscription = null
        debounce(context).cancel()
    }

    // The switches and the period take no change backup, and a backup due after the switch went off is dropped.
    // zh-CN: 开关与周期的变化不触发变更后备份, 开关关闭后到期的备份被丢弃.
    private fun debounce(context: Context): SettingsChangeDebounce = synchronized(this) {
        debounce ?: SettingsChangeDebounce(
            WorkScheduler(context.applicationContext),
            filter = SettingsChangeDebounce.Filter(ownKeys = GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS),
            enabled = { GeneralKeys.SETTINGS_AUTO_BACKUP_CHANGE.get() },
        ).also { debounce = it }
    }

    /** Runs the change backup once the settings stayed unchanged. zh-CN: 设置不再变化后执行变更后备份. */
    class ChangeBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            if (debounce(applicationContext).onDue()) AndroidSettingsAutoBackup.write(applicationContext, Mechanism.CHANGE)
            return Result.success()
        }
    }

    /**
     * The work replaces a pending one, so the latest delay wins; a work rescheduled by itself (the quiet time is not
     * over yet) is replaced while it finishes, which WorkManager allows.
     * zh-CN: 新任务替换尚未执行的任务, 因此以最新的延迟为准; 任务在执行中重新安排自身 (静默时间未满) 时, 正在结束的任务被替换, WorkManager
     * 允许这样做.
     */
    private class WorkScheduler(private val context: Context) : SettingsChangeDebounce.Scheduler {

        override fun schedule(delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<ChangeBackupWorker>().setInitialDelay(delayMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
