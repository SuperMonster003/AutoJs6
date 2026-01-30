package org.autojs.autojs.runtime.api

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.autojs.autojs.runtime.ScriptRuntime
import java.util.ArrayDeque

/**
 * Toast global queue manager.
 * All scripts share a single global queue because only one toast can be shown at a time.
 *
 * zh-CN:
 *
 * Toast 全局队列管理器.
 * 所有脚本共享同一个全局队列, 因为同一时间通常只能显示一个 Toast.
 *
 * Created by SuperMonster003 on Aug 3, 2023.
 * Transformed by SuperMonster003 on Oct 24, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 30, 2026.
 * Modified by SuperMonster003 as of Jan 30, 2026.
 */
object ScriptToast {

    private const val MAX_GLOBAL_QUEUE_SIZE = 17

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<ToastTask>(MAX_GLOBAL_QUEUE_SIZE)

    private var isShowing = false
    private var currentTask: ToastTask? = null
    private var currentToast: Toast? = null
    private var currentFinishRunnable: Runnable? = null

    private data class ToastTask(
        val message: String,
        val duration: Int,
        val ownerId: String,
    )

    @JvmStatic
    fun enqueueToast(context: Context, scriptRuntime: ScriptRuntime, message: String, isLong: Boolean = false) {
        val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        val ownerId = scriptRuntime.ownerId

        // Must be called on the main thread.
        // zh-CN: 必须在主线程调用.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { enqueueToast(context, scriptRuntime, message, isLong) }
            return
        }

        if (queue.size >= MAX_GLOBAL_QUEUE_SIZE) {
            // Discard newest task when the queue is full.
            // zh-CN: 当队列已满时丢弃最新任务.
            return
        }

        queue.addLast(ToastTask(message, duration, ownerId))

        tryShowNextLocked(context.applicationContext)
    }

    @JvmStatic
    fun dismissAll(scriptRuntime: ScriptRuntime) {
        val ownerId = scriptRuntime.ownerId
        mainHandler.post {
            queue.removeAll { it.ownerId == ownerId }

            if (currentTask?.ownerId == ownerId) {
                currentFinishRunnable?.let { mainHandler.removeCallbacks(it) }
                currentFinishRunnable = null

                currentToast?.cancel()
                currentToast = null
                currentTask = null
                isShowing = false

                tryShowNextLocked(scriptRuntime.uiHandler.applicationContext)
            }
        }
    }

    @JvmStatic
    fun clear(scriptRuntime: ScriptRuntime) {
        val ownerId = scriptRuntime.ownerId
        mainHandler.post {
            queue.removeAll { it.ownerId == ownerId }
        }
    }

    @JvmStatic
    fun destroy() {
        mainHandler.post {
            queue.clear()

            currentFinishRunnable?.let { mainHandler.removeCallbacks(it) }
            currentFinishRunnable = null

            currentToast?.cancel()
            currentToast = null
            currentTask = null
            isShowing = false
        }
    }

    private fun tryShowNextLocked(appCtx: Context) {
        if (isShowing) return

        val task = queue.removeFirstOrNull() ?: return

        isShowing = true
        currentTask = task

        val toast = Toast.makeText(appCtx, task.message, task.duration)
        currentToast = toast

        val finish = Runnable {
            currentFinishRunnable = null
            currentToast = null
            currentTask = null
            isShowing = false
            tryShowNextLocked(appCtx)
        }
        currentFinishRunnable = finish

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toast.addCallback(object : Toast.Callback() {
                override fun onToastHidden() {
                    super.onToastHidden()
                    finish.run()
                }
            })
        } else {
            val delay = if (task.duration == Toast.LENGTH_LONG) 3_500L else 2_000L
            mainHandler.postDelayed(finish, delay)
        }

        toast.show()
    }

    private fun <E> ArrayDeque<E>.removeFirstOrNull(): E? =
        if (isEmpty()) null else removeFirst()

    private inline fun <E> ArrayDeque<E>.removeAll(predicate: (E) -> Boolean) {
        if (isEmpty()) return
        val it = iterator()
        while (it.hasNext()) {
            if (predicate(it.next())) it.remove()
        }
    }

}
