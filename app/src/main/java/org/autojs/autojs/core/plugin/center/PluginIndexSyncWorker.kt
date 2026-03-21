package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PluginIndexSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val repo = PluginIndexRepository()

    override suspend fun doWork(): Result =
        try {
            if (!PluginOnlineSettingsStore.isAutoCheckUpdatesEnabled()) {
                Log.i(TAG, "Index sync skipped: auto update check disabled.")
                return Result.success()
            }
            val enabledSources = PluginOnlineSettingsStore.selectedSources()
            if (enabledSources.isEmpty()) {
                Log.i(TAG, "Index sync skipped: no online source enabled.")
                return Result.success()
            }
            repo.fetchOnlineIndex(
                context = applicationContext,
                forceRefresh = true,
                enabledSources = enabledSources,
            )
            PluginOnlineSettingsStore.markCheckedNow()
            Log.i(TAG, "Index sync succeeded.")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Index sync failed: ${e.message}")
            Result.retry()
        }

    companion object {
        private const val TAG = "PluginIndexSyncWorker"
    }

}
