package org.autojs.autojs.storage.file

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Created by SuperMonster003 on Feb 8, 2026.
 */
object TmpScriptFilesCleanupScheduler {

    private const val UNIQUE_WORK_NAME = "tmp_script_files_cleanup_periodic"

    fun scheduleStartupCleanup(context: Context) {
        val appContext = context.applicationContext

        runCatching {
            val req = OneTimeWorkRequestBuilder<TmpScriptFilesCleanupWorker>()
                // Delay a bit to reduce cold-start I/O pressure.
                // zh-CN: 适当延迟以降低冷启动 I/O 压力.
                .setInitialDelay(10L, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    "startup-tmp-script-files-cleanup",
                    ExistingWorkPolicy.KEEP,
                    req,
                )
        }
    }

    /**
     * Schedule periodic cleanup (once per day).
     * zh-CN: 调度周期性清理 (每天一次).
     */
    fun schedulePeriodicCleanup(context: Context) {
        val appContext = context.applicationContext

        // No network is required; keep it light and battery-friendly.
        // zh-CN: 不需要网络; 尽量轻量/省电.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<TmpScriptFilesCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}