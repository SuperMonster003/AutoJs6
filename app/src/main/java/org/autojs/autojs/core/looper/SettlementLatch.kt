package org.autojs.autojs.core.looper

import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs6.R
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The settlement of a Promise that one thread waits for, `Promise.prototype.wait` (docs/augment/ROADMAP.md Q26).
 *
 * promise.js runs the callbacks of a Promise through `setImmediate`, on the timer of the settling thread, or on the main
 * timer of the script when that thread has none (the binder thread of a TTS engine, for example). A waiting thread that
 * only blocked kept the callbacks queued on its own timer from running, so `wait()` never returned: the script thread
 * waiting for a Promise of a module (`tts`, `toJsPromise`), or any thread waiting for a Promise settled on itself
 * (`Promise.resolve(1).wait()`). While it waits, the thread therefore runs the due callbacks of its own timer ([queue])
 * in their order, as its looper would. The UI thread must not block: there [await] only runs the callbacks that are
 * already due, and throws a TypeError when the Promise is still pending. Runnables posted to the looper of the thread in
 * other ways (`Handler.post`) still wait until the thread returns to its looper.
 *
 * zh-CN: 一个线程等待的 Promise 结算, 即 `Promise.prototype.wait` (docs/augment/ROADMAP.md Q26).
 * promise.js 经 `setImmediate` 在结算线程的 timer 上运行 Promise 回调, 该线程没有 timer 时 (例如 TTS 引擎的 binder 线程) 放到脚本的主 timer.
 * 等待线程原先只是阻塞, 排在它自己 timer 上的回调无法运行, wait() 永不返回: 脚本线程等待模块的 Promise (tts, toJsPromise), 或任何线程等待
 * 在自身结算的 Promise (`Promise.resolve(1).wait()`). 因此线程在等待期间按顺序运行自己 timer ([queue]) 上已到期的回调, 如同它的 Looper 那样.
 * UI 线程不能阻塞: [await] 在那里只运行已到期的回调, Promise 仍未结算时抛出 TypeError. 以其他方式 (`Handler.post`) 投递到该线程 Looper 的
 * Runnable 仍要等线程回到 Looper 才运行.
 */
class SettlementLatch internal constructor(private val queue: CallbackQueue?, private val mayBlock: Boolean) {

    /**
     * The callbacks that the waiting thread runs: those of its own [Timer].
     *
     * zh-CN: 等待线程要运行的回调, 即它自己 [Timer] 上的回调.
     */
    internal interface CallbackQueue {

        /** Removes the earliest due callback and returns it, or returns null when none is due. */
        fun takeDueCallback(): Runnable?

        /** Milliseconds until the next callback is due, or -1 when none is queued. */
        fun millisUntilNextCallback(): Long

        /** The thread of the queue starts waiting: a callback queued from now on unparks it, as its looper is not polling. */
        fun beginWait()

        fun endWait()

    }

    private val owner = Thread.currentThread()

    @Volatile
    private var settled = false

    private var value: Any? = null

    /**
     * Settles the latch with [value], on any thread. Only the first settlement counts.
     *
     * zh-CN: 以 [value] 结算, 可在任意线程调用. 只有第一次结算有效.
     */
    fun settle(value: Any?) {
        synchronized(this) {
            if (settled) return
            this.value = value
            settled = true
        }
        // A callback run by the waiting thread itself finds the latch settled without a wake-up.
        if (Thread.currentThread() !== owner) LockSupport.unpark(owner)
    }

    /**
     * The value of the settlement, running the due callbacks of the queue until it comes (see the class documentation).
     * Only the thread that created the latch waits.
     *
     * zh-CN: 结算的值, 在结算之前运行队列中已到期的回调 (见类文档). 只能由创建该对象的线程等待.
     */
    fun await(): Any? {
        check(Thread.currentThread() === owner) { "A SettlementLatch is awaited on the thread that created it" }
        val queue = queue ?: return awaitSettled(null)
        queue.beginWait()
        try {
            return awaitSettled(queue)
        } finally {
            queue.endWait()
        }
    }

    private fun awaitSettled(queue: CallbackQueue?): Any? {
        while (!settled) {
            val callback = queue?.takeDueCallback()
            if (callback != null) {
                callback.run()
                continue
            }
            if (!mayBlock) {
                throw RhinoScriptRuntime.typeError(
                    ScriptMessages.resource(
                        R.string.error_promise_wait_blocks_ui_thread,
                        "Promise.prototype.wait() cannot block the UI thread while the Promise is pending. Use then(), await() in a coroutine, or call wait() in a sub-thread",
                    )
                )
            }
            when (val millis = queue?.millisUntilNextCallback() ?: -1L) {
                -1L -> LockSupport.park(this)
                else -> LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(millis))
            }
            if (Thread.interrupted()) throw ScriptInterruptedException()
        }
        return value
    }

}
