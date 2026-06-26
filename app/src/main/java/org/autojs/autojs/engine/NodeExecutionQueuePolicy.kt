package org.autojs.autojs.engine

object NodeExecutionQueuePolicy {

    const val MODE_REJECT_WHEN_BUSY = "reject_when_busy"
    const val MODE_ENQUEUE_WHEN_BUSY = "enqueue_when_busy"

    const val DEFAULT_MAX_QUEUED_EXECUTIONS = 16
    const val DEFAULT_QUEUE_TIMEOUT_MS = 300_000L
    const val MAX_QUEUE_TIMEOUT_MS = 300_000L
    const val CONTROL_TIMEOUT_MS = 3_000L

    fun normalizeMode(mode: String?): String {
        return when (mode) {
            MODE_ENQUEUE_WHEN_BUSY -> MODE_ENQUEUE_WHEN_BUSY
            else -> MODE_REJECT_WHEN_BUSY
        }
    }

    fun normalizedQueueTimeoutMs(mode: String, timeoutMs: Long): Long {
        if (normalizeMode(mode) != MODE_ENQUEUE_WHEN_BUSY) {
            return 0L
        }
        return when {
            timeoutMs <= 0L -> DEFAULT_QUEUE_TIMEOUT_MS
            else -> timeoutMs.coerceAtMost(MAX_QUEUE_TIMEOUT_MS)
        }
    }
}

data class NodeExecutionQueueStatus(
    val activeExecutionId: String?,
    val queuedExecutionIds: List<String>,
    val maxQueuedExecutions: Int,
) {

    val queuedCount: Int
        get() = queuedExecutionIds.size

    val idle: Boolean
        get() = activeExecutionId == null && queuedExecutionIds.isEmpty()
}
