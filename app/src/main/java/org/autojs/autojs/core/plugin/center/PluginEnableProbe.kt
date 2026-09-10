package org.autojs.autojs.core.plugin.center

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object PluginEnableProbe {

    private const val TIMEOUT_MILLIS = 5_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun run(
        packageName: String,
        timeoutMillis: Long = TIMEOUT_MILLIS,
        probe: suspend () -> Unit,
    ) {
        // Binder calls cannot always be interrupted. Keep the UI deadline independent of
        // the worker, while cancelling suspended binds when the user leaves or time runs out.
        // zh-CN: Binder 调用不一定可中断. UI 超时不等待工作线程, 但仍取消挂起的服务绑定.
        val completed = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val job = scope.launch {
                    try {
                        probe()
                        continuation.resume(Unit)
                    } catch (error: CancellationException) {
                        continuation.cancel(error)
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                }
                continuation.invokeOnCancellation { job.cancel() }
            }
            true
        }
        if (completed != true) {
            throw TimeoutException("Plugin enable handshake timed out after $timeoutMillis ms: $packageName")
        }
    }
}
