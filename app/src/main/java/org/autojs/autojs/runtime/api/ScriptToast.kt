package org.autojs.autojs.runtime.api

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.autojs.autojs.runtime.ScriptRuntime
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private const val MAX_GLOBAL_QUEUE_SIZE = 32
    private const val ENQUEUE_SYNC_WAIT_MS = 1_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<ToastTask>(MAX_GLOBAL_QUEUE_SIZE)

    private var isShowing = false
    private var currentTask: ToastTask? = null
    private var currentToast: Toast? = null
    private var currentFinishRunnable: Runnable? = null

    // Fallback timeout runnable for API 30+ when Toast.Callback is not reliably invoked.
    // zh-CN: 用于 API 30+ 场景的超时兜底 Runnable, 防止 Toast.Callback 不回调导致队列卡死.
    private var currentFallbackTimeoutRunnable: Runnable? = null

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
            val appContext = context.applicationContext
            val latch = CountDownLatch(1)
            mainHandler.postAtFrontOfQueue {
                try {
                    enqueueToast(appContext, scriptRuntime, message, isLong)
                } finally {
                    latch.countDown()
                }
            }
            runCatching {
                latch.await(ENQUEUE_SYNC_WAIT_MS, TimeUnit.MILLISECONDS)
            }
            return
        }

        val task = ToastTask(message, duration, ownerId)

        if (queue.size >= MAX_GLOBAL_QUEUE_SIZE) {
            // Keep newest: discard oldest when queue is full.
            // zh-CN: 保留最新: 队列满时丢弃最旧任务.
            queue.removeFirstOrNull()
        }

        queue.addLast(task)

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

                currentFallbackTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                currentFallbackTimeoutRunnable = null

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

            currentFallbackTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            currentFallbackTimeoutRunnable = null

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
            // Ensure idempotency.
            // zh-CN: 确保 finish 具备幂等性.
            currentFinishRunnable?.let { mainHandler.removeCallbacks(it) }
            currentFinishRunnable = null

            currentFallbackTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            currentFallbackTimeoutRunnable = null

            currentToast = null
            currentTask = null
            isShowing = false
            tryShowNextLocked(appCtx)
        }
        currentFinishRunnable = finish

        val delay = if (task.duration == Toast.LENGTH_LONG) 3_500L else 2_000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Fallback timeout:
            // Some ROMs may not invoke Toast.Callback reliably, which would freeze the queue forever.
            //
            // zh-CN:
            // 超时兜底:
            // 某些 ROM 可能不会可靠触发 Toast.Callback, 否则队列会永久卡死.
            val fallback = Runnable { finish.run() }
            currentFallbackTimeoutRunnable = fallback
            mainHandler.postDelayed(fallback, delay + 1_000L)

            toast.addCallback(object : Toast.Callback() {
                override fun onToastHidden() {
                    super.onToastHidden()

                    // Cancel fallback and finish immediately.
                    // zh-CN: 取消兜底计时并立即 finish.
                    currentFallbackTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    currentFallbackTimeoutRunnable = null

                    finish.run()
                }
            })
        } else {
            mainHandler.postDelayed(finish, delay)
        }

        runCatching {
            toast.show()
        }.onFailure {
            // If show() throws, release the queue immediately.
            // zh-CN: 若 show() 抛异常, 立即释放队列避免卡死.
            finish.run()
        }
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
