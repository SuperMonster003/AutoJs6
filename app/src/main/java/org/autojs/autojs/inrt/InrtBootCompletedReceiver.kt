package org.autojs.autojs.inrt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.autojs.autojs.AbstractAutoJs
import org.autojs.autojs.inrt.launch.GlobalProjectLauncher

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 9, 2026.
 */
class InrtBootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!AbstractAutoJs.isInrt) {
            return
        }
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_USER_UNLOCKED) {
            return
        }
        val pendingResult = goAsync()
        Thread {
            try {
                Pref.syncLaunchConfigWithBuild()
                if (!Pref.shouldRunOnBoot()) {
                    return@Thread
                }
                GlobalProjectLauncher.launchOnBoot()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to launch script on boot action=$action", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "InrtBootCompletedRcvr"
    }
}
