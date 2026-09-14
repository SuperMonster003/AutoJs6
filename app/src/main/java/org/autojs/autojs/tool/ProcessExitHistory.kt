package org.autojs.autojs.tool

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** A bounded, on-demand snapshot. Reading history never marks an exit as a new crash. */
object ProcessExitHistory {

    const val MAX_RECORDS = 20
    const val MAX_AGE_DAYS = 7
    const val MAX_AGE_MILLIS = MAX_AGE_DAYS * 24 * 60 * 60 * 1000L

    data class Record(
        val timestamp: Long,
        val processName: String,
        val pid: Int,
        val reason: Int,
        val status: Int,
        val description: String?,
        val pss: Long,
        val rss: Long,
    ) {
        val category: Category
            get() = when {
                reason == ApplicationExitInfo.REASON_OTHER && description?.contains("MemoryLimiter:AnonSwap") == true -> Category.MEMORY_LIMITER
                reason == ApplicationExitInfo.REASON_CRASH || reason == ApplicationExitInfo.REASON_CRASH_NATIVE -> Category.CRASH
                reason == ApplicationExitInfo.REASON_ANR -> Category.ANR
                reason == ApplicationExitInfo.REASON_LOW_MEMORY -> Category.LOW_MEMORY
                reason == ApplicationExitInfo.REASON_EXIT_SELF -> Category.SELF_EXIT
                reason == ApplicationExitInfo.REASON_USER_REQUESTED || reason == ApplicationExitInfo.REASON_USER_STOPPED -> Category.USER_STOP
                reason in SYSTEM_REASONS -> Category.SYSTEM
                else -> Category.UNKNOWN
            }

        val reasonName: String
            get() = REASON_NAMES[reason] ?: "REASON_UNKNOWN_$reason"
    }

    enum class Category { MEMORY_LIMITER, CRASH, ANR, LOW_MEMORY, SELF_EXIT, USER_STOP, SYSTEM, UNKNOWN }

    sealed interface Result {
        data class Available(val records: List<Record>) : Result
        data object Unsupported : Result
        data object Failed : Result
    }

    fun read(context: Context): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Result.Unsupported
        return try {
            readSupported(context)
        } catch (_: RuntimeException) {
            // System services can reject or fail a query. Do not turn that into an empty history.
            Result.Failed
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            Result.Failed
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readSupported(context: Context): Result {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return Result.Failed
        @Suppress("DEPRECATION")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        val records = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS).map {
            Record(it.timestamp, it.processName, it.pid, it.reason, it.status, it.description, it.pss, it.rss)
        }
        return Result.Available(select(records, System.currentTimeMillis(), installedAt))
    }

    /** PID alone is not an identity: it can be reused, and multiple app processes may exit together. */
    internal fun select(records: List<Record>, now: Long, installedAt: Long): List<Record> {
        val oldest = maxOf(installedAt, now - MAX_AGE_MILLIS, 1L)
        return records.asSequence()
            .filter { it.timestamp in oldest..now }
            .sortedByDescending { it.timestamp }
            .distinctBy { Triple(it.timestamp, it.processName, it.pid) }
            .take(MAX_RECORDS)
            .toList()
    }

    private val SYSTEM_REASONS = setOf(
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_OTHER,
        ApplicationExitInfo.REASON_FREEZER,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED,
    )

    private val REASON_NAMES = mapOf(
        ApplicationExitInfo.REASON_UNKNOWN to "REASON_UNKNOWN",
        ApplicationExitInfo.REASON_EXIT_SELF to "REASON_EXIT_SELF",
        ApplicationExitInfo.REASON_SIGNALED to "REASON_SIGNALED",
        ApplicationExitInfo.REASON_LOW_MEMORY to "REASON_LOW_MEMORY",
        ApplicationExitInfo.REASON_CRASH to "REASON_CRASH",
        ApplicationExitInfo.REASON_CRASH_NATIVE to "REASON_CRASH_NATIVE",
        ApplicationExitInfo.REASON_ANR to "REASON_ANR",
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "REASON_INITIALIZATION_FAILURE",
        ApplicationExitInfo.REASON_PERMISSION_CHANGE to "REASON_PERMISSION_CHANGE",
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to "REASON_EXCESSIVE_RESOURCE_USAGE",
        ApplicationExitInfo.REASON_USER_REQUESTED to "REASON_USER_REQUESTED",
        ApplicationExitInfo.REASON_USER_STOPPED to "REASON_USER_STOPPED",
        ApplicationExitInfo.REASON_DEPENDENCY_DIED to "REASON_DEPENDENCY_DIED",
        ApplicationExitInfo.REASON_OTHER to "REASON_OTHER",
        ApplicationExitInfo.REASON_FREEZER to "REASON_FREEZER",
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE to "REASON_PACKAGE_STATE_CHANGE",
        ApplicationExitInfo.REASON_PACKAGE_UPDATED to "REASON_PACKAGE_UPDATED",
    )
}
