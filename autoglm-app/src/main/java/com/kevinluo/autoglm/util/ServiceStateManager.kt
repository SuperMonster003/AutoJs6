package com.kevinluo.autoglm.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Service state persistence manager.
 *
 * Saves and restores expected service states so services can be
 * automatically restored after app restart.
 */
object ServiceStateManager {
    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Sets whether floating window service should be running.
     *
     * @param context Application context
     * @param enabled Whether the service should be enabled
     */
    fun setFloatingWindowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_FLOATING_WINDOW_ENABLED, enabled)
        }
    }

    /**
     * Gets whether floating window service should be running.
     *
     * @param context Application context
     * @return true if the service should be running
     */
    fun isFloatingWindowEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FLOATING_WINDOW_ENABLED, false)

    /**
     * Records the last task execution time.
     *
     * @param context Application context
     */
    fun recordTaskExecution(context: Context) {
        getPrefs(context).edit {
            putLong(KEY_LAST_TASK_TIME, System.currentTimeMillis())
        }
    }

    /**
     * Gets the last task execution time.
     *
     * @param context Application context
     * @return Timestamp of last task execution, or 0 if never executed
     */
    fun getLastTaskTime(context: Context): Long = getPrefs(context).getLong(KEY_LAST_TASK_TIME, 0)

    /**
     * Checks if there was a recent task execution (within 1 hour).
     *
     * @param context Application context
     * @return true if a task was executed within the last hour
     */
    fun hasRecentTask(context: Context): Boolean {
        val lastTime = getLastTaskTime(context)
        return System.currentTimeMillis() - lastTime < 60 * 60 * 1000
    }

    // Constants - placed at the bottom following code style guidelines
    private const val PREFS_NAME = "autoglm_service_state"
    private const val KEY_FLOATING_WINDOW_ENABLED = "floating_window_enabled"
    private const val KEY_LAST_TASK_TIME = "last_task_time"
}
