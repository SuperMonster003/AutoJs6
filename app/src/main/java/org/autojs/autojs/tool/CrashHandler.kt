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
 * Modified by Claude (Opus 5.5) as of Oct 24, 2026.
 *
 * [systemHandler] is the default handler from before [install] (the system's, which records the crash as am_crash and
 * in dropbox, then kills the process). Crashes that end the process go to it after the crash report, so the system
 * counts them: a process crashing twice within a minute is marked bad and no longer started for background broadcasts,
 * which ends startup crash loops (db-downgrade roadmap P1.5 / D10).
 *
 * zh-CN: [systemHandler] 是 [install] 之前的默认处理器 (系统的处理器, 把崩溃记为 am_crash 并写入 dropbox, 然后结束进程).
 * 会结束进程的崩溃在崩溃报告之后交给它, 由系统计数: 一分钟内第二次崩溃的进程被标为 bad, 不再为后台广播拉起, 启动期的崩溃循环
 * 因此停止 (db-downgrade 路线图 P1.5 / D10).
 */
class CrashHandler private constructor(
    private val errorReportClass: Class<*>,
    private val systemHandler: UncaughtExceptionHandler?,
) : UncaughtExceptionHandler {

    private var mCachedExceptionMessage: MutableList<WeakReference<Throwable>> = ArrayList()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "Uncaught Exception", ex)
        // ScriptRuntime.popException(ex.message ?: "Uncaught Exception")
        val latestMessage = ex.message ?: "[ No error message ]"
        if (mCachedExceptionMessage.size > 4) {
            if (mCachedExceptionMessage.all { it.get()?.message == latestMessage } || mCachedExceptionMessage.size > 20) {
                reportAndHandOver(thread, ex, latestMessage)
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
        reportAndHandOver(thread, ex, latestMessage)
    }

    private fun reportAndHandOver(thread: Thread, ex: Throwable, latestMessage: String): Nothing {
        // The hand-over below must happen even if the report fails.
        // zh-CN: 即使报告失败, 也必须执行后面的移交.
        runCatching {
            ScriptRuntime.popException(latestMessage)
            startCrashReportActivity("Uncaught Exception", ex.stackTraceToString())
        }.onFailure { Log.e(TAG, "Failed to report the crash", it) }
        // The system handler kills the process and does not return.
        // zh-CN: 系统处理器会结束进程, 不会返回.
        systemHandler?.uncaughtException(thread, ex)
        exitProcess(1)
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

        /**
         * Makes a [CrashHandler] the default handler, keeping the current default as its [systemHandler].
         * zh-CN: 把 [CrashHandler] 设为默认处理器, 并把当前的默认处理器留作其 [systemHandler].
         */
        @JvmStatic
        fun install(errorReportClass: Class<*>) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            val systemHandler = if (current is CrashHandler) current.systemHandler else current
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(errorReportClass, systemHandler))
        }
    }

}
