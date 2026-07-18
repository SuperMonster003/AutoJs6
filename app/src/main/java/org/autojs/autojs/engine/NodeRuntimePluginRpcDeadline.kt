package org.autojs.autojs.engine

/** Overflow-safe host response deadline layered on top of the remote execution timeout. */
internal object NodeRuntimePluginRpcDeadline {

    const val RESPONSE_GRACE_MS = 5_000L
    const val CANCEL_RPC_BUDGET_MS = 1_000L
    const val RUNTIME_INFO_RPC_BUDGET_MS = 2_000L

    fun responseWaitTimeoutMs(remoteTimeoutMs: Long): Long {
        if (remoteTimeoutMs <= 0L) return 0L
        return if (remoteTimeoutMs > Long.MAX_VALUE - RESPONSE_GRACE_MS) {
            Long.MAX_VALUE
        } else {
            remoteTimeoutMs + RESPONSE_GRACE_MS
        }
    }
}
