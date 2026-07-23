package org.autojs.autojs.apkbuilder.template

internal object RemoteBuildWaitPolicy {

    const val NO_PROGRESS_TIMEOUT_MILLIS = 12L * 60L * 1_000L
    const val TOTAL_TIMEOUT_MILLIS = 30L * 60L * 1_000L

    enum class Decision {
        CONTINUE,
        BINDER_LOST,
        NO_PROGRESS_TIMEOUT,
        TOTAL_TIMEOUT,
    }

    fun evaluate(
        nowElapsedMillis: Long,
        startedAtElapsedMillis: Long,
        lastActivityElapsedMillis: Long,
        binderAlive: Boolean,
        noProgressTimeoutMillis: Long = NO_PROGRESS_TIMEOUT_MILLIS,
        totalTimeoutMillis: Long = TOTAL_TIMEOUT_MILLIS,
    ): Decision {
        require(noProgressTimeoutMillis > 0L) { "No-progress timeout must be positive." }
        require(totalTimeoutMillis >= noProgressTimeoutMillis) {
            "Total timeout must not be shorter than no-progress timeout."
        }
        if (!binderAlive) {
            return Decision.BINDER_LOST
        }
        if (elapsed(nowElapsedMillis, startedAtElapsedMillis) >= totalTimeoutMillis) {
            return Decision.TOTAL_TIMEOUT
        }
        if (elapsed(nowElapsedMillis, lastActivityElapsedMillis) >= noProgressTimeoutMillis) {
            return Decision.NO_PROGRESS_TIMEOUT
        }
        return Decision.CONTINUE
    }

    private fun elapsed(nowElapsedMillis: Long, thenElapsedMillis: Long): Long {
        return (nowElapsedMillis - thenElapsedMillis).coerceAtLeast(0L)
    }
}
