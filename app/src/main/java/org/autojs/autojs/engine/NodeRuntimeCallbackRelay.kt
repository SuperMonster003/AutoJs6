package org.autojs.autojs.engine

import android.os.Bundle
import android.os.RemoteException
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * The only runtime callback exported to the plugin process for one execution.
 *
 * Binder keeps an exported stub reachable through a JNI global reference until the plugin process
 * collects its proxy, which may be long after the execution ended. The relay reaches the execution
 * (its output listener, the completion future and the result bundle) only until [clear], which the
 * host calls once the call returned, timed out or the plugin died (docs/memory-phase2/ROADMAP.md C30).
 * zh-CN: 一次执行中唯一导出给插件进程的运行时回调. 导出的存根经 JNI 全局引用保持可达, 直到插件进程回收其代理,
 * 可能远晚于执行结束. 中继只在 [clear] 之前指向本次执行 (输出监听, 完成 future 与结果 Bundle);
 * 宿主在调用返回, 超时或插件死亡后调用 [clear] (docs/memory-phase2/ROADMAP.md C30).
 *
 * @param completion completed by the terminal [NodeJsRuntimeContract.EVENT_FINISHED] event of an
 *  asynchronous execution; null for the blocking runScript contract.
 */
internal class NodeRuntimeCallbackRelay(
    executionId: String,
    completion: CompletableFuture<Bundle>?,
    callback: INodeJsRuntimeCallback?,
) : INodeJsRuntimeCallback.Stub() {

    private class Target(
        val executionId: String,
        val completion: CompletableFuture<Bundle>?,
        val callback: INodeJsRuntimeCallback?,
    )

    private val target = AtomicReference<Target?>(Target(executionId, completion, callback))

    val isCleared: Boolean
        get() = target.get() == null

    fun clear() {
        target.set(null)
    }

    override fun onEvent(event: Bundle?) {
        val current = target.get() ?: return
        event ?: return
        val finished = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE) == NodeJsRuntimeContract.EVENT_FINISHED
        if (finished) {
            current.completion?.let { completion ->
                val result = event.getBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT)
                if (event.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID) == current.executionId && result != null) {
                    completion.complete(result)
                } else {
                    completion.completeExceptionally(RemoteException("Invalid Node.js runtime completion."))
                }
            }
        }
        current.callback?.onEvent(event)
        // The terminal event is the last one the plugin sends for this execution.
        // zh-CN: 终止事件是插件为本次执行发送的最后一个事件.
        if (finished && current.completion != null) clear()
    }
}
