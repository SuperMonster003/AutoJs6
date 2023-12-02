package org.autojs.autojs.timing

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.external.ScriptIntents.handleIntent
import java.util.concurrent.TimeUnit

/**
 * Created by Stardust on Nov 27, 2017.
 */
object TimedTaskScheduler {

    private const val LOG_TAG = "TimedTaskScheduler"
    private const val JOB_TAG_CHECK_TASKS = "checkTasks"

    private val SCHEDULE_TASK_MIN_TIME = TimeUnit.DAYS.toMillis(2)

    @SuppressLint("CheckResult")
    fun checkTasks(context: Context?, force: Boolean) {
        Log.d(LOG_TAG, "check tasks: force = $force")
        TimedTaskManager.allTasks
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { timedTask: TimedTask -> scheduleTaskIfNeeded(context, timedTask, force) }
    }

    @JvmStatic
    fun scheduleTaskIfNeeded(context: Context?, timedTask: TimedTask, force: Boolean) {
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
    private fun scheduleTask(context: Context?, timedTask: TimedTask, millis: Long, force: Boolean) {
        if (!force && timedTask.isScheduled) {
            return
        }
        val timeWindow = millis - System.currentTimeMillis()
        timedTask.isScheduled = true
        TimedTaskManager.updateTaskWithoutReScheduling(timedTask)
        if (timeWindow <= 0) {
            runTask(context, timedTask)
            return
        }
        cancel(timedTask)
        Log.d(LOG_TAG, "schedule task: task = $timedTask, millis = $millis, timeWindow = $timeWindow")
        JobRequest.Builder(timedTask.id.toString())
            .setExact(timeWindow)
            .build()
            .schedule()
    }

    @JvmStatic
    fun cancel(timedTask: TimedTask) {
        val cancelCount = JobManager.instance().cancelAllForTag(timedTask.id.toString())
        Log.d(LOG_TAG, "cancel task: task = $timedTask, cancel = $cancelCount")
    }

    fun init(context: Context) {
        JobManager.create(context).addJobCreator { tag: String ->
            if (tag == JOB_TAG_CHECK_TASKS) {
                return@addJobCreator CheckTasksJob(context)
            } else {
                return@addJobCreator TimedTaskJob(context)
            }
        }
        JobRequest.Builder(JOB_TAG_CHECK_TASKS)
            .setPeriodic(TimeUnit.MINUTES.toMillis(20))
            .build()
            .scheduleAsync()
        checkTasks(context, true)
    }

    private fun runTask(context: Context?, task: TimedTask) {
        Log.d(LOG_TAG, "run task: task = $task")
        val intent = task.createIntent()
        handleIntent(context, intent)
        TimedTaskManager.notifyTaskFinished(task.id)
    }

    private class TimedTaskJob(private val mContext: Context) : Job() {
        override fun onRunJob(params: Params): Result {
            val id = params.tag.toLong()
            val task = TimedTaskManager.getTimedTask(id)
            Log.d(LOG_TAG, "onRunJob: id = $id, task = $task")
            if (task == null) {
                return Result.FAILURE
            }
            runTask(mContext, task)
            return Result.SUCCESS
        }
    }

    private class CheckTasksJob(private val mContext: Context) : Job() {
        override fun onRunJob(params: Params): Result {
            checkTasks(mContext, false)
            return Result.SUCCESS
        }
    }
}