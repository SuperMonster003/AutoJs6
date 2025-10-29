package org.autojs.autojs.timing

import android.content.Context

interface TimedTaskBackend {

    fun init(context: Context) {
        /* Nothing to do by default. */
    }

    fun cancel(context: Context, task: TimedTask)

    fun schedule(context: Context, task: TimedTask, triggerAtMillis: Long)

    fun schedulePeriodicCheck(context: Context, intervalMillis: Long)

}