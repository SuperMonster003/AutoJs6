package org.autojs.autojs.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "System boot completed, re-initializing timed tasks")
            TimedTaskScheduler.init(context.applicationContext)
        }
    }

}
