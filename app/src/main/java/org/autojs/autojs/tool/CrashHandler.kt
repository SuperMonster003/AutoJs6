package org.autojs.autojs.tool

import android.content.Intent
import android.os.Looper
import android.util.Log
import com.tencent.bugly.crashreport.BuglyLog
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.bugly.crashreport.CrashReport.CrashHandleCallback
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs6.BuildConfig
import org.mozilla.javascript.RhinoException
import java.lang.Thread.UncaughtExceptionHandler
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

/**
 * Created by Stardust on Feb 2, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Nov 28, 2023.
 */
class CrashHandler(private val errorReportClass: Class<*>) : CrashHandleCallback(), UncaughtExceptionHandler {

    private var mBuglyHandler: UncaughtExceptionHandler? = null
    private val mSystemHandler: UncaughtExceptionHandler? by lazy {
        Thread.getDefaultUncaughtExceptionHandler()
    }

    private var mCachedExceptionMessage: MutableList<WeakReference<Throwable>> = ArrayList()

    @Synchronized
    override fun onCrashHandleStart(crashType: Int, errorType: String?, errorMessage: String?, errorStack: String?): MutableMap<String, String>? {
        Log.d(TAG, "onCrashHandleStart: crashType = $crashType, errorType = $errorType, msg = $errorMessage, stack = $errorStack")
        try {
            if (crashTooManyTimes()) {
                return super.onCrashHandleStart(crashType, errorType, errorMessage, errorStack)
            }
            startErrorReportActivity("$errorType: $errorMessage", errorStack ?: "[ No stack message ]")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return super.onCrashHandleStart(crashType, errorType, errorMessage, errorStack)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "Uncaught Exception", ex)
        ScriptRuntime.popException(ex.message ?: "Uncaught Exception")
        val latestMessage = ex.message ?: "[ No error message ]"
        if (mCachedExceptionMessage.size > 4) {
            if (mCachedExceptionMessage.all { it.get()?.message == latestMessage } || mCachedExceptionMessage.size > 20) {
                ScriptRuntime.popException(latestMessage)
                ClipboardUtils.setClip(GlobalAppContext.get(), ex.stackTraceToString())
                exitProcess(1)
            }
        }
        mCachedExceptionMessage.add(WeakReference(ex))
        if (thread != Looper.getMainLooper().thread) {
            if (ex !is RhinoException) {
                CrashReport.postCatchedException(ex, thread)
            }
            return
        }
        val service = AccessibilityService.instance
        if (service != null && AccessibilityService.stop()) {
            Log.d(TAG, "Service disabled: $service")
        } else {
            BuglyLog.d(TAG, "Failed to disable service: ${AccessibilityService::class.java.simpleName}")
        }
        if (BuildConfig.DEBUG) {
            mSystemHandler?.uncaughtException(thread, ex)
        } else {
            mBuglyHandler?.uncaughtException(thread, ex)
        }
    }

    fun setBuglyHandler(buglyHandler: UncaughtExceptionHandler?) {
        mBuglyHandler = buglyHandler
    }

    private fun startErrorReportActivity(msg: String, detail: String) {
        Intent(GlobalAppContext.get(), errorReportClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("message", msg)
            putExtra("error", detail)
        }.let { GlobalAppContext.get().startActivity(it) }
    }

    private fun crashTooManyTimes() = when {
        crashIntervalTooLong() -> false.also { resetCrashCount() }
        else -> ++crashCount >= 5
    }

    private fun resetCrashCount() {
        firstCrashMillis = System.currentTimeMillis()
        crashCount = 0
    }

    private fun crashIntervalTooLong() = System.currentTimeMillis() - firstCrashMillis > 3000

    companion object {
        private const val TAG = "CrashHandler"
        private var crashCount = 0
        private var firstCrashMillis = 0L
    }

}