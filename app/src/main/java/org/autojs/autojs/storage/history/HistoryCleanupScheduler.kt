package org.autojs.autojs.storage.history

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Scheduler for HistoryCleanupWorker.
 * zh-CN: HistoryCleanupWorker 的调度器.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
object HistoryCleanupScheduler {

    private const val UNIQUE_WORK_NAME = "history_cleanup_periodic"

    /**
     * Schedule periodic cleanup (once per day).
     * zh-CN: 调度周期性清理 (每天一次).
     */
    fun schedule(context: Context) {
        val appContext = context.applicationContext

        // No network is required; keep it light and battery-friendly.
        // zh-CN: 不需要网络; 尽量轻量/省电.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<HistoryCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}