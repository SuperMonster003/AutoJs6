package org.autojs.autojs.timing

import android.annotation.SuppressLint
import android.content.Context
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.external.ScriptIntents.handleIntent
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.util.concurrent.TimeUnit

/**
 * Created by Stardust on Nov 27, 2017.
 * Modified by SuperMonster003 as of Oct 27, 2025.
 */
object TimedTaskScheduler {

    private const val LOG_TAG = "TimedTaskScheduler"

    private val SCHEDULE_TASK_MIN_TIME = TimeUnit.DAYS.toMillis(2)
    private val SCHEDULE_PERIODIC_CHECK_TIME = TimeUnit.MINUTES.toMillis(20)

    private lateinit var backend: TimedTaskBackend

    // Alarm quota reserved for the current application (not exceeding 500).
    // zh-CN: 为当前应用保留的闹钟配额 (不超过 500).
    private const val MAX_ALARM_SLOTS = 450

    fun init(context: Context) {
        val backend = run initBackend@{
            val keyRes = R.string.key_timed_task_backend
            val defRes = R.string.default_key_timed_task_backend
            when (val prefValue = Pref.getString(keyRes, defRes)) {
                key(R.string.key_timed_task_backend_alarm) -> AlarmTimedTaskScheduler
                key(R.string.key_timed_task_backend_work) -> WorkTimedTaskScheduler
                key(R.string.key_timed_task_backend_job) -> JobTimedTaskScheduler
                else -> throw RuntimeException("Unknown backend: $prefValue")
            }
        }.also { this.backend = it }

        println("$LOG_TAG: init backend = ${TimedTaskScheduler.backend}")
        backend.init(context)
        backend.schedulePeriodicCheck(context, SCHEDULE_PERIODIC_CHECK_TIME)

        checkTasks(context, true)
    }

    @JvmStatic
    fun cancel(context: Context, timedTask: TimedTask) {
        backend.cancel(context, timedTask)
        println("$LOG_TAG: cancel task (${backend}): task = $timedTask")
    }

    fun runTask(context: Context, task: TimedTask) {
        println("$LOG_TAG: run task: task = $task")
        val intent = task.createIntent()
        handleIntent(context, intent)
        TimedTaskManager.notifyTaskFinished(task.id)
    }

    @SuppressLint("CheckResult")
    fun checkTasks(context: Context, force: Boolean) {
        println("$LOG_TAG: check tasks: force = $force")
        when (backend) {
            AlarmTimedTaskScheduler -> TimedTaskManager.allTasksAsList
                .sortedBy { it.getNextTime(context) }
                .take(MAX_ALARM_SLOTS)
                .forEach { timedTask -> scheduleTaskIfNeeded(context, timedTask, force) }
            else -> TimedTaskManager.allTasks
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { timedTask -> scheduleTaskIfNeeded(context, timedTask, force) }
        }
    }

    @JvmStatic
    fun scheduleTaskIfNeeded(context: Context, timedTask: TimedTask, force: Boolean) {
        val millis = timedTask.getNextTime(context)
        if (millis <= System.currentTimeMillis()) {
            runTask(context, timedTask)
            return
        }
        if (!force && timedTask.isScheduled || millis - System.currentTimeMillis() > SCHEDULE_TASK_MIN_TIME) {
            return
        }
        scheduleTask(context, timedTask, millis, force)
        TimedTaskManager.notifyTaskScheduled(timedTask)
    }

    @Synchronized
    private fun scheduleTask(context: Context, timedTask: TimedTask, millis: Long, force: Boolean) {
        if (!force && timedTask.isScheduled) {
            return
        }
        timedTask.isScheduled = true
        val timeWindow = millis - System.currentTimeMillis()
        TimedTaskManager.updateTaskWithoutReScheduling(timedTask)
        if (timeWindow <= 0) {
            runTask(context, timedTask)
            return
        }
        cancel(context, timedTask)
        println("$LOG_TAG: schedule task: task = $timedTask, millis = $millis, timeWindow = $timeWindow")

        backend.schedule(context, timedTask, millis)
    }

}