package org.autojs.autojs.core.pref.archive

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Mechanism
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup.Outcome
import org.autojs.autojs.core.pref.data.SettingsDataCapture
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.autojs.autojs.external.foreground.AppForegroundService
import org.autojs.autojs.ui.settings.transfer.SettingsTransferActivity
import org.autojs.autojs.util.ForegroundServiceUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException

/**
 * The automatic backups of the app (docs/settings/ROADMAP.md D13 / D22 / T20, P9): [SettingsAutoBackup] in
 * `<working directory>/backups/settings/`, holding the settings of [SettingsAutoBackup.CATEGORIES] as the stores have
 * them now and the data files of its data categories ([SettingsDataCapture]). Each mechanism has a switch, a `general`
 * setting ([GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS]); [write] returns [Outcome.Off] for a mechanism switched off. [start]
 * follows the switches and the period from any source (the page, an import, a reset, a script) and schedules the
 * periodic backup ([SettingsPeriodicBackupWorker]) and the change backup ([AndroidSettingsChangeBackup]) accordingly. A
 * periodic or change backup that fails, for example in a working directory that is not writable, posts a notification
 * and is not retried: the next period or change tries again ("pause" of T20). The operation backup tells it on the
 * result page.
 *
 * zh-CN: 应用的自动备份 (ROADMAP D13 / D22 / T20, P9): 以 [SettingsAutoBackup] 写入 `<工作目录>/backups/settings/`, 内容为
 * [SettingsAutoBackup.CATEGORIES] 当前的存储值及其中数据分类的数据文件 ([SettingsDataCapture]). 每种机制各有一个开关, 是 `general` 分类的
 * 设置 ([GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS]); 关闭的机制由 [write] 返回 [Outcome.Off]. [start] 跟随开关与周期来自任何来源 (页面,
 * 导入, 恢复默认, 脚本) 的变化, 据此安排周期备份 ([SettingsPeriodicBackupWorker]) 与变更后备份 ([AndroidSettingsChangeBackup]). 周期备份
 * 或变更后备份失败 (例如工作目录不可写) 时发出通知且不重试: 下一个周期或下一次变更再试 (T20 的 "暂停"). 操作后备份的失败在结果页提示.
 */
object AndroidSettingsAutoBackup {

    private const val TAG = "AndroidSettingsAutoBackup"

    /** One notification for the failures, the latest replacing the one before. zh-CN: 失败只有一条通知, 新的替换旧的. */
    private const val FAILURE_NOTIFICATION_ID = 0xBF5

    private var subscription: AutoCloseable? = null

    /** `backups/settings/` under the working directory. zh-CN: 工作目录下的 `backups/settings/`. */
    @JvmStatic
    fun directory(): File = SettingsAutoBackup.directoryIn(File(WorkingDirectoryUtils.path))

    /**
     * Follows the switches and the period of the automatic backups from now on, and schedules the periodic and the
     * change backups as they are now; the app calls it once at its start, in the main process.
     * zh-CN: 从现在起跟随自动备份的开关与周期, 并按当前状态安排周期备份与变更后备份; 应用启动时在主进程调用一次.
     */
    @JvmStatic
    fun start(context: Context) = synchronized(this) {
        val app = context.applicationContext
        if (subscription == null) {
            val names = GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS.map { it.name }.toSet()
            subscription = SettingsStores.addChangeListener { store, name ->
                // A cleared store (name null) may have reset the switches too.
                if (store == StoreId.DEFAULT && (name == null || name in names)) follow(app)
            }
        }
        follow(app)
    }

    private fun follow(context: Context) {
        SettingsPeriodicBackupWorker.schedule(context)
        when (GeneralKeys.SETTINGS_AUTO_BACKUP_CHANGE.get()) {
            true -> AndroidSettingsChangeBackup.start(context)
            else -> AndroidSettingsChangeBackup.stop(context)
        }
    }

    /**
     * The operation backup (T20): call it once an import, a reset to defaults or an undo is done and before its result
     * page shows, off the main thread. It throws no I/O error: the result page tells [Outcome.Failed] without blocking.
     * zh-CN: 操作后备份 (T20): 在导入, 恢复默认或撤销完成之后, 结果页显示之前调用, 不在主线程. 不抛 I/O 错误: 结果页提示
     * [Outcome.Failed], 不阻断.
     */
    @JvmStatic
    fun afterOperation(context: Context): Outcome = write(context, Mechanism.OPERATION)

    /**
     * A backup of [mechanism] now, off the main thread, or [Outcome.Off] while its switch is off; the workers of the
     * periodic (P9.2) and change (P9.3) backups call it too. Throws no I/O error ([Outcome.Failed]).
     * zh-CN: 立即写一份 [mechanism] 的备份, 不在主线程调用, 其开关关闭时返回 [Outcome.Off]; 周期备份 (P9.2) 与变更后备份 (P9.3) 的任务
     * 也调用它. 不抛 I/O 错误 ([Outcome.Failed]).
     */
    @JvmStatic
    fun write(context: Context, mechanism: Mechanism): Outcome {
        if (!isOn(mechanism)) return Outcome.Off
        val outcome = try {
            val now = System.currentTimeMillis()
            val snapshot = SettingsSnapshot.capture(SettingsAutoBackup.CATEGORIES)
            val data = SettingsDataCapture.capture(context, SettingsAutoBackup.CATEGORIES, now)
            SettingsAutoBackup(directory(), clock = { now }).tryWrite(mechanism, snapshot, host(context), device(), data)
        } catch (e: SecurityException) {
            Outcome.Failed(IOException(e.message, e))
        }
        if (outcome is Outcome.Failed && mechanism != Mechanism.OPERATION) notifyFailure(context.applicationContext, outcome)
        return outcome
    }

    /** Whether the switch of [mechanism] is on. zh-CN: [mechanism] 的开关是否打开. */
    internal fun isOn(mechanism: Mechanism): Boolean = when (mechanism) {
        Mechanism.PERIODIC -> GeneralKeys.SETTINGS_AUTO_BACKUP_PERIODIC
        Mechanism.CHANGE -> GeneralKeys.SETTINGS_AUTO_BACKUP_CHANGE
        Mechanism.OPERATION -> GeneralKeys.SETTINGS_AUTO_BACKUP_OPERATION
    }.get()

    /**
     * Tells a failed periodic or change backup on the channel of the foreground service, the one channel of the app for
     * its own notices; the notification opens "Manage AutoJs6 settings".
     * zh-CN: 在前台服务的通知渠道 (应用自身提示所用的唯一渠道) 告知周期备份或变更后备份失败; 点按通知打开 "管理 AutoJs6 设置".
     */
    private fun notifyFailure(context: Context, outcome: Outcome.Failed) {
        Log.w(TAG, "Automatic backup failed: ${outcome.error}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
            val channelName = context.getString(R.string.foreground_notification_channel_name, label)
            ForegroundServiceUtils.createNotificationChannelIfNeeded(context, AppForegroundService::class.java, channelName, channelName)
            val intent = Intent(context, SettingsTransferActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val content = context.getString(R.string.settings_transfer_auto_backup_failed_content, directory().path)
            val notification = NotificationCompat.Builder(context, ForegroundServiceUtils.getChannelId(AppForegroundService::class.java))
                .setSmallIcon(R.drawable.autojs6_status_bar_icon)
                .setContentTitle(context.getString(R.string.settings_transfer_auto_backup_failed))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(FAILURE_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Cannot post the notification of a failed automatic backup: $it") }
    }

    private fun host(context: Context) = SettingsArchiveManifest.Host(context.packageName, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    private fun device() = SettingsArchiveManifest.Device(Build.MODEL.orEmpty(), Build.VERSION.SDK_INT)
}
