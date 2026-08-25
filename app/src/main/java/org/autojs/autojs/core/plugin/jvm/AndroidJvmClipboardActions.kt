package org.autojs.autojs.core.plugin.jvm

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.util.ClipboardUtils
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class AndroidJvmClipboardActions(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getText(): String = onMain {
        ClipboardUtils.getClipOrEmpty(appContext).toString()
    }

    fun setText(text: String) = onMain {
        ClipboardUtils.setClip(appContext, text)
    }

    private fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(Callable { action() })
        check(mainHandler.post(task)) { "AutoJs6 main dispatcher rejected a JVM host capability" }
        return try {
            task.get(CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            task.cancel(true)
            throw IllegalStateException("JVM host capability timed out", error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private companion object {
        const val CALL_TIMEOUT_MILLIS = 10_000L
    }
}
