package org.autojs.autojs.timing

import android.content.Context
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest

object JobTimedTaskScheduler : TimedTaskBackend {

    private const val LOG_TAG = "JobTimedTaskScheduler"

    private const val JOB_TAG_CHECK_TASKS = "checkTasks"

    override fun cancel(context: Context, task: TimedTask) {
        JobManager.instance().cancelAllForTag(task.id.toString())
    }

    override fun schedule(context: Context, task: TimedTask, triggerAtMillis: Long) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        JobRequest.Builder(task.id.toString())
            .setExact(delay)
            .build()
            .schedule()
    }

    override fun schedulePeriodicCheck(context: Context, intervalMillis: Long) {
        JobManager.create(context).addJobCreator { tag: String ->
            when (tag) {
                JOB_TAG_CHECK_TASKS -> CheckTasksJob(context)
                else -> TimedTaskJob(context)
            }
        }
        JobRequest.Builder(JOB_TAG_CHECK_TASKS)
            .setPeriodic(intervalMillis)
            .build()
            .scheduleAsync()
    }

    private class TimedTaskJob(private val context: Context) : Job() {
        override fun onRunJob(params: Params): Result {
            val id = params.tag.toLong()
            val task = TimedTaskManager.getTimedTask(id)
            println("$LOG_TAG: onRunJob: id = $id, task = $task")
            task ?: return Result.FAILURE
            TimedTaskScheduler.runTask(context, task)
            return Result.SUCCESS
        }
    }

    private class CheckTasksJob(private val context: Context) : Job() {
        override fun onRunJob(params: Params): Result {
            TimedTaskScheduler.checkTasks(context, false)
            return Result.SUCCESS
        }
    }

}