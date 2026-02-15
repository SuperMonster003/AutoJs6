package org.autojs.autojs.storage.file

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Created by SuperMonster003 on Feb 15, 2026.
 */
class TmpScriptFilesCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val maxAgeMs = TMP_FILES_CLEANUP_THRESHOLD
            TmpScriptFiles.clearTmpDir(applicationContext, maxAgeMs)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val TMP_FILES_CLEANUP_THRESHOLD: Long = 7L * 24L * 60L * 60L * 1000L
    }
}
