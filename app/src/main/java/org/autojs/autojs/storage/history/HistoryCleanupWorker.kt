package org.autojs.autojs.storage.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic cleanup worker for history/trash/drafts; the passes live in [HistoryCleanup].
 * zh-CN: 用于 history/trash/drafts 的周期性清理 Worker; 清理过程见 [HistoryCleanup].
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 8, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
class HistoryCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            HistoryCleanup.runAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
