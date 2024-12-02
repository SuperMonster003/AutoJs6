package org.autojs.autojs.timing

import android.annotation.SuppressLint
import android.text.TextUtils
import io.reactivex.Flowable
import io.reactivex.Observable
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.storage.database.IntentTaskDatabase
import org.autojs.autojs.storage.database.ModelChange
import org.autojs.autojs.storage.database.TimedTaskDatabase
import org.autojs.autojs.timing.TimedTaskScheduler.cancel
import org.autojs.autojs.timing.TimedTaskScheduler.scheduleTaskIfNeeded
import org.autojs.autojs.util.Observers

/**
 * Created by Stardust on Nov 27, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Apr 2, 2023.
 */
object TimedTaskManager {

    private val globalAppContext = GlobalAppContext.get()
    private val mTimedTaskDatabase = TimedTaskDatabase(globalAppContext)
    private val mIntentTaskDatabase = IntentTaskDatabase(globalAppContext)

    @JvmStatic
    val allTasks: Flowable<TimedTask>
        get() = mTimedTaskDatabase.queryAllAsFlowable()

    @JvmStatic
    val allIntentTasks: Flowable<IntentTask>
        get() = mIntentTaskDatabase.queryAllAsFlowable()

    @JvmStatic
    val allTasksAsList: List<TimedTask>
        get() = mTimedTaskDatabase.queryAll()

    @JvmStatic
    val allIntentTasksAsList: List<IntentTask>
        get() = mIntentTaskDatabase.queryAll()

    @JvmStatic
    val timeTaskChanges: Observable<ModelChange<TimedTask>>
        get() = mTimedTaskDatabase.modelChange

    @JvmStatic
    val intentTaskChanges: Observable<ModelChange<IntentTask>>
        get() = mIntentTaskDatabase.modelChange

    @JvmStatic
    fun getTimedTask(taskId: Long): TimedTask? {
        return mTimedTaskDatabase.queryById(taskId)
    }

    @JvmStatic
    fun getIntentTask(intentTaskId: Long): IntentTask? = mIntentTaskDatabase.queryById(intentTaskId)

    @JvmStatic
    fun getIntentTaskOfAction(action: String?): Flowable<IntentTask> = mIntentTaskDatabase.query("action = ?", action)

    @JvmStatic
    @SuppressLint("CheckResult")
    fun addTask(timedTask: TimedTask) {
        mTimedTaskDatabase.insert(timedTask)
            .subscribe({ id: Long? ->
                timedTask.id = id!!
                scheduleTaskIfNeeded(globalAppContext, timedTask, false)
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun addTask(intentTask: IntentTask) {
        mIntentTaskDatabase.insert(intentTask)
            .subscribe({
                if (!TextUtils.isEmpty(intentTask.action)) {
                    app.dynamicBroadcastReceivers
                        .registerIntent(intentTask)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    fun addTaskSync(timedTask: TimedTask) {
        timedTask.id = mTimedTaskDatabase.insertSync(timedTask)
        scheduleTaskIfNeeded(globalAppContext, timedTask, false)
    }

    @JvmStatic
    fun addTaskSync(intentTask: IntentTask) {
        intentTask.id = mIntentTaskDatabase.insertSync(intentTask)
        app.dynamicBroadcastReceivers.registerIntent(intentTask)
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun removeTask(timedTask: TimedTask) {
        cancel(timedTask)
        mTimedTaskDatabase.delete(timedTask)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun removeTask(intentTask: IntentTask) {
        mIntentTaskDatabase.delete(intentTask)
            .subscribe({
                if (!TextUtils.isEmpty(intentTask.action)) {
                    app.dynamicBroadcastReceivers
                        .unregister(intentTask.action)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    fun removeTaskSync(timedTask: TimedTask): Boolean {
        cancel(timedTask)
        return mTimedTaskDatabase.deleteSync(timedTask) > 0
    }

    @JvmStatic
    fun removeTaskSync(intentTask: IntentTask): Boolean {
        return mIntentTaskDatabase.deleteSync(intentTask) > 0
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTask(task: TimedTask) {
        mTimedTaskDatabase.update(task)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        cancel(task)
        scheduleTaskIfNeeded(globalAppContext, task, false)
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTaskSync(task: TimedTask): Boolean {
        val id = mTimedTaskDatabase.updateSync(task)
        cancel(task)
        scheduleTaskIfNeeded(globalAppContext, task, false)
        return id > 0
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTask(task: IntentTask) {
        mIntentTaskDatabase.update(task)
            .subscribe({ i: Int ->
                if (i > 0 && !TextUtils.isEmpty(task.action)) {
                    app.dynamicBroadcastReceivers
                        .registerIntent(task)
                }
            }) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun updateTaskSync(task: IntentTask): Boolean {
        val id = mIntentTaskDatabase.updateSync(task)
        if (id > 0 && !TextUtils.isEmpty(task.action)) {
            app.dynamicBroadcastReceivers
                .registerIntent(task)
        }
        return id > 0
    }

    @SuppressLint("CheckResult")
    fun updateTaskWithoutReScheduling(task: TimedTask) {
        mTimedTaskDatabase.update(task)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @SuppressLint("CheckResult")
    fun notifyTaskScheduled(timedTask: TimedTask) {
        timedTask.isScheduled = true
        mTimedTaskDatabase.update(timedTask)
            .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
    }

    @JvmStatic
    @SuppressLint("CheckResult")
    fun notifyTaskFinished(id: Long) {
        val task = getTimedTask(id) ?: return
        if (task.isDisposable) {
            mTimedTaskDatabase.delete(task)
                .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        } else {
            task.isScheduled = false
            mTimedTaskDatabase.update(task)
                .subscribe(Observers.emptyConsumer()) { obj: Throwable -> obj.printStackTrace() }
        }
    }

    fun countTasks() = mTimedTaskDatabase.count()

}