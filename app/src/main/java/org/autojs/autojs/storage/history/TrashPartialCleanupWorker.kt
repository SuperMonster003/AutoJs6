package org.autojs.autojs.storage.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * One-time startup cleanup worker for stale partial trash blobs.
 * zh-CN: 用于启动时一次性清理过期 partial 回收站 blob 的 Worker.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 8, 2026.
 */
class TrashPartialCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val maxAgeMs = PARTIAL_TRASH_BLOB_MAX_AGE_MS_STARTUP
            TrashBlobStore(applicationContext).cleanupStalePartialTrashBlobs(maxAgeMs)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        // Maximum age for partial trash blob files before deletion on startup.
        // zh-CN: 启动时用于删除 partial 回收站 blob 的最大保留时长.
        private const val PARTIAL_TRASH_BLOB_MAX_AGE_MS_STARTUP: Long = 30L * 60L * 1000L
    }
}
