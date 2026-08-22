package org.autojs.autojs.core.plugin.python

/** Host-side timeout defaults and bounds for one Python execution. */
internal object PythonExecutionTimeoutPolicy {

    const val DEFAULT_TIMEOUT_MILLIS = 5L * 60L * 1_000L
    const val MAX_CONFIGURED_TIMEOUT_MILLIS = 30L * 60L * 1_000L

    fun isValidConfiguredTimeout(timeoutMillis: Long): Boolean =
        timeoutMillis in 1L..MAX_CONFIGURED_TIMEOUT_MILLIS

    fun resolve(configuredTimeoutMillis: Long?, providerMaxTimeoutMillis: Long): Long {
        require(providerMaxTimeoutMillis > 0L) { "Python provider timeout limit must be positive" }
        require(
            configuredTimeoutMillis == null || isValidConfiguredTimeout(configuredTimeoutMillis),
        ) { "Configured Python timeout is outside the host limit" }
        return minOf(
            configuredTimeoutMillis ?: DEFAULT_TIMEOUT_MILLIS,
            providerMaxTimeoutMillis,
        )
    }
}
