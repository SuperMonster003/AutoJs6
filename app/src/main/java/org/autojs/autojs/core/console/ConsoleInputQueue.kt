package org.autojs.autojs.core.console

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * One blocking `console.rawInput()` call. The script thread waits on [await]; the console view
 * that receives a line completes it with [complete]; stopping the script cancels it.
 *
 * zh-CN: 一次阻塞的 `console.rawInput()` 调用. 脚本线程在 [await] 上等待, 收到输入行的控制台视图通过 [complete] 完成它, 停止脚本时取消它.
 */
class ConsoleInputRequest internal constructor(val id: Int, val owner: Any?) {

    private val mFuture = CompletableFuture<String>()

    val isDone: Boolean get() = mFuture.isDone

    fun complete(text: String): Boolean = mFuture.complete(text)

    fun cancel(): Boolean = mFuture.cancel(true)

    /** @throws InterruptedException when the waiting thread is interrupted; [CancellationException] when cancelled. */
    @Throws(InterruptedException::class, CancellationException::class)
    fun await(): String = try {
        mFuture.get()
    } catch (e: ExecutionException) {
        throw CancellationException(e.message)
    }

}

/**
 * Pending input requests of one console, oldest first. A runtime console registers its requests
 * with itself and with the global console it forwards to, so a line typed into any view bound to
 * either console serves the oldest waiting script. Android-free so the policy is unit-testable.
 *
 * zh-CN: 一个控制台的待处理输入请求, 最早的排在最前. 运行时控制台会把请求同时登记到自身及其转发目标 (全局控制台), 因此在任一绑定视图中输入的行都会交给等待最久的脚本. 不依赖 Android, 便于单元测试.
 */
class ConsoleInputQueue {

    private val mRequests = CopyOnWriteArrayList<ConsoleInputRequest>()

    val pendingCount: Int get() = mRequests.count { !it.isDone }

    val hasPending: Boolean get() = mRequests.any { !it.isDone }

    fun add(request: ConsoleInputRequest) {
        mRequests.addIfAbsent(request)
    }

    fun remove(request: ConsoleInputRequest) {
        mRequests.remove(request)
    }

    /** Completes the oldest pending request with [text]; null when nothing is waiting. zh-CN: 用 [text] 完成最早的待处理请求; 无等待请求时返回 null. */
    fun submit(text: String): ConsoleInputRequest? {
        while (true) {
            val request = mRequests.firstOrNull { !it.isDone } ?: return null
            if (request.complete(text)) return request
        }
    }

    /** Cancels and removes the requests owned by [owner] (all when null). zh-CN: 取消并移除 [owner] 拥有的请求 (为 null 时移除全部). */
    fun cancelAll(owner: Any? = null) {
        mRequests.filter { owner == null || it.owner === owner }.forEach { request ->
            request.cancel()
            mRequests.remove(request)
        }
    }

    companion object {

        private val sIdCounter = AtomicInteger(0)

        @JvmStatic
        fun newRequest(owner: Any?) = ConsoleInputRequest(sIdCounter.getAndIncrement(), owner)

    }

}
