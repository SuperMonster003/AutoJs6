package org.autojs.autojs.engine

import org.autojs.autojs.timing.TimedTask
import org.json.JSONArray
import org.json.JSONObject

object NodeTimedTaskSnapshots {

    const val SCHEMA = "autojs6-node-timed-task-snapshot-v1"

    fun snapshot(task: TimedTask): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("id", task.id)
        .putNullable("scriptPath", task.scriptPath)
        .put("repeatMode", task.repeatMode)
        .put("repeatModeName", repeatModeName(task.repeatMode))
        .put("timeFlag", task.timeFlag)
        .put("millis", task.millis)
        .put("scheduled", task.isScheduled)
        .put("delay", task.delay)
        .put("interval", task.interval)
        .put("loopTimes", task.loopTimes)
        .put("endMode", task.endMode)
        .put("endModeName", endModeName(task.endMode))
        .put("endValue", task.endValue)
        .put("finishedTimes", task.finishedTimes)
        .put("dayOfMonth", task.dayOfMonth)
        .put("monthOfYear", task.monthOfYear)
        .put("disposable", task.isDisposable)
        .put("daily", task.isDaily)
        .put("weekly", task.isWeekly)
        .put("monthly", task.isMonthly)
        .put("yearly", task.isYearly)
        .put("countdown", task.isCountdown)
        .put("legacyOwner", "rhino_timed_task_database")
        .put("nodeProviderOwned", false)
        .put("rawObjectPolicy", rawObjectPolicy())

    fun querySnapshots(
        tasks: Iterable<TimedTask>,
        scriptPath: String? = null,
        repeatMode: Int? = null,
    ): JSONArray = JSONArray().apply {
        tasks
            .filter { task -> scriptPath == null || task.scriptPath == scriptPath }
            .filter { task -> repeatMode == null || task.repeatMode == repeatMode }
            .sortedBy { task -> task.id }
            .forEach { task -> put(snapshot(task)) }
    }

    fun getSnapshot(tasks: Iterable<TimedTask>, id: Long): JSONObject? =
        tasks.firstOrNull { task -> task.id == id }?.let(::snapshot)

    private fun repeatModeName(mode: Int): String = when (mode) {
        TimedTask.REPEAT_MODE_DISPOSABLE -> "disposable"
        TimedTask.REPEAT_MODE_DAILY -> "daily"
        TimedTask.REPEAT_MODE_WEEKLY -> "weekly"
        TimedTask.REPEAT_MODE_MONTHLY -> "monthly"
        TimedTask.REPEAT_MODE_YEARLY -> "yearly"
        TimedTask.REPEAT_MODE_COUNTDOWN -> "countdown"
        else -> "unknown"
    }

    private fun endModeName(mode: Int): String = when (mode) {
        TimedTask.END_MODE_FOREVER -> "forever"
        TimedTask.END_MODE_UNTIL_DATE -> "until_date"
        TimedTask.END_MODE_COUNT -> "count"
        else -> "unknown"
    }

    private fun rawObjectPolicy(): JSONObject = JSONObject()
        .put("rawTimedTask", "denied")
        .put("rawExecutionConfig", "denied")
        .put("rawPendingIntent", "denied")
        .put("rawAlarmManager", "denied")
        .put("rawJobScheduler", "denied")
        .put("rawWorkRequest", "denied")
        .put("databaseCursor", "denied")
        .put("mutableRowHandle", "denied")

    private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}
