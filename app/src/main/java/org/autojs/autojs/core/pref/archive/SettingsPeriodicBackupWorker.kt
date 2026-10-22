package org.autojs.autojs.core.pref.archive

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Mechanism
import org.autojs.autojs.core.pref.keys.GeneralKeys
import java.util.concurrent.TimeUnit

/**
 * The periodic backup of the app (docs/settings/ROADMAP.md D22 / T20, P9.2): a unique WorkManager periodic work that
 * writes a backup through [AndroidSettingsAutoBackup] every 1, 7 or 30 days ([GeneralKeys.settingsAutoBackupPeriodDays]).
 * [schedule] enqueues it while the switch (off by default, a `general` setting) is on and cancels it otherwise; the app
 * calls it at its start and whenever the switch or the period changes ([AndroidSettingsAutoBackup.start]). A failed
 * backup is not retried: [AndroidSettingsAutoBackup] posts a notification and the next period tries again.
 *
 * zh-CN: 应用的周期备份 (ROADMAP D22 / T20, P9.2): 唯一的 WorkManager 周期任务, 每 1, 7 或 30 天 ([GeneralKeys.settingsAutoBackupPeriodDays])
 * 经 [AndroidSettingsAutoBackup] 写一份备份. [schedule] 在开关 (默认关, `general` 分类的设置) 打开时排入任务, 关闭时取消; 应用启动时以及开关或
 * 周期变化时调用 ([AndroidSettingsAutoBackup.start]). 备份失败不重试: [AndroidSettingsAutoBackup] 发出通知, 下一个周期再试.
 */
class SettingsPeriodicBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        AndroidSettingsAutoBackup.write(applicationContext, Mechanism.PERIODIC)
        return Result.success()
    }

    companion object {

        /** The unique work name. zh-CN: 唯一任务名. */
        const val WORK_NAME = "settings_periodic_backup"

        /**
         * The repeat interval in days that the switch and the period ask for, null when the switch is off (no work).
         * zh-CN: 开关与周期所要求的重复间隔天数, 开关关闭时为 null (不排任务).
         */
        @JvmStatic
        fun repeatDays(): Long? = if (GeneralKeys.SETTINGS_AUTO_BACKUP_PERIODIC.get()) GeneralKeys.settingsAutoBackupPeriodDays.toLong() else null

        /**
         * Enqueues the work with the current period or cancels it. An unchanged period keeps the time of the next run
         * ([ExistingPeriodicWorkPolicy.UPDATE]), so calling it at every start of the app does not postpone the backup.
         * zh-CN: 按当前周期排入任务或取消任务. 周期不变时保留下一次执行的时间 ([ExistingPeriodicWorkPolicy.UPDATE]), 因此应用每次启动都调用也
         * 不会推迟备份.
         */
        @JvmStatic
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            when (val days = repeatDays()) {
                null -> workManager.cancelUniqueWork(WORK_NAME)
                else -> workManager.enqueueUniquePeriodicWork(
                    WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<SettingsPeriodicBackupWorker>(days, TimeUnit.DAYS).build(),
                )
            }
        }
    }
}
