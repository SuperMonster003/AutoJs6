package org.autojs.autojs.tool

import android.content.Intent
import android.os.Looper
import android.util.Log
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.IntentUtils.startSafely
import java.lang.Thread.UncaughtExceptionHandler
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

/**
 * Created by Stardust on Feb 2, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Nov 28, 2023.
 */
class CrashHandler(private val errorReportClass: Class<*>) : UncaughtExceptionHandler {

    private val mSystemHandler: UncaughtExceptionHandler? by lazy {
        Thread.getDefaultUncaughtExceptionHandler()
    }

    private var mCachedExceptionMessage: MutableList<WeakReference<Throwable>> = ArrayList()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "Uncaught Exception", ex)
        // ScriptRuntime.popException(ex.message ?: "Uncaught Exception")
        val latestMessage = ex.message ?: "[ No error message ]"
        if (mCachedExceptionMessage.size > 4) {
            if (mCachedExceptionMessage.all { it.get()?.message == latestMessage } || mCachedExceptionMessage.size > 20) {
                ScriptRuntime.popException(latestMessage)
                startCrashReportActivity("Uncaught Exception", ex.stackTraceToString())
                exitProcess(1)
            }
        }
        mCachedExceptionMessage.add(WeakReference(ex))
        if (thread != Looper.getMainLooper().thread) {
            return
        }
        val service = AccessibilityService.instance
        if (service != null && AccessibilityService.stop()) {
            Log.d(TAG, "Service disabled: $service")
        } else {
            Log.d(TAG, "Failed to disable service: ${AccessibilityService::class.java.simpleName}")
        }
        mSystemHandler?.uncaughtException(thread, ex)
    }

    private fun startCrashReportActivity(msg: String, detail: String) {
        val context = GlobalAppContext.get()
        Intent(context, errorReportClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("message", msg)
            putExtra("error", detail)
        }.startSafely(context)
    }

    companion object {
        private const val TAG = "CrashHandler"
    }

}