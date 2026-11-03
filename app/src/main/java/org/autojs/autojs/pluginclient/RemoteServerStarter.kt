package org.autojs.autojs.pluginclient

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Shares an in-flight start while leaving the accept loop owned by the application. The loop's
 * observer releases the one-shot reply after startup, so it cannot retain an MCP request.
 * zh-CN: 合并并发启动, accept 循环归应用所有. 启动后清空单次回复, 避免常驻观察者保留 MCP 请求.
 */
internal class RemoteServerStarter<T : Any>(
    private val isRunning: () -> Boolean,
    private val source: () -> Observable<T>,
    private val onStarted: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val lock = Any()
    private var pending: Single<Boolean>? = null

    /** Returns whether the server was already listening. Does not disconnect existing clients. */
    fun start(): Single<Boolean> = synchronized(lock) {
        if (isRunning()) {
            onStarted()
            return@synchronized Single.just(true)
        }
        pending ?: Single.create<Boolean> { emitter ->
            val reply = AtomicReference<SingleEmitter<Boolean>?>(emitter)
            fun ready() {
                val target = reply.getAndSet(null) ?: return
                if (isRunning()) {
                    onStarted()
                    target.onSuccess(false)
                } else {
                    target.tryOnError(IOException("AutoJs6 remote server did not open its listening socket; retry after startup or shutdown completes."))
                }
            }
            // Intentionally keep this subscription until the application stops the server.
            // zh-CN: 此订阅有意保留到应用停止服务端, 不随 MCP 请求结束而释放.
            source().subscribe({ ready() }, { error ->
                onFailure(error)
                reply.getAndSet(null)?.tryOnError(error)
            }, { ready() })
        }.doFinally {
            synchronized(lock) { pending = null }
        }.cache().also { pending = it }
    }
}
