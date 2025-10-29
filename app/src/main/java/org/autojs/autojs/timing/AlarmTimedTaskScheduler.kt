package org.autojs.autojs.timing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import org.autojs.autojs.timing.TaskReceiver.EXTRA_TASK_ID

object AlarmTimedTaskScheduler : TimedTaskBackend {

    private const val LOG_TAG = "AlarmTimedTask"

    const val ACTION_RUN_ALARM_TIMED_TASK = "org.autojs.autojs.timing.ACTION_RUN_ALARM_TIMED_TASK"

    override fun cancel(context: Context, task: TimedTask) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context, task.id))
    }

    override fun schedule(context: Context, task: TimedTask, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, task.id)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (e: Exception) {
            // When reaching exceptions like "maximum 500 alarms per UID", fall back to WorkManager to avoid failure.
            // zh-CN: 达到 "每 UID 最多 500 个闹钟" 等异常时, 退回到 WorkManager, 避免失败.
            Log.e(LOG_TAG, "Failed to schedule exact alarm, fallback to WorkManager. taskId=${task.id}, cause=${e.message}")
            WorkTimedTaskScheduler.schedule(context, task, triggerAtMillis)
        }
    }

    override fun schedulePeriodicCheck(context: Context, intervalMillis: Long) {
        WorkTimedTaskScheduler.schedulePeriodicCheck(context, intervalMillis)
    }

    private fun buildPendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, TimedTaskAlarmReceiver::class.java)
            .setAction(ACTION_RUN_ALARM_TIMED_TASK)
            .putExtra(EXTRA_TASK_ID, taskId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }

}
