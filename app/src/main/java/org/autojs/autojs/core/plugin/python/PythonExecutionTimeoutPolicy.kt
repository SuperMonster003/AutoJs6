package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionMode

/** Host-side timeout defaults and bounds for one Python execution. */
internal object PythonExecutionTimeoutPolicy {

    const val DEFAULT_TIMEOUT_MILLIS = 5L * 60L * 1_000L
    const val MAX_CONFIGURED_TIMEOUT_MILLIS = 30L * 60L * 1_000L

    fun isValidConfiguredTimeout(timeoutMillis: Long): Boolean =
        timeoutMillis in 1L..MAX_CONFIGURED_TIMEOUT_MILLIS

    fun resolve(configuredTimeoutMillis: Long?, providerMaxTimeoutMillis: Long): Long {
        return resolve(PythonExecutionMode.BOUNDED, configuredTimeoutMillis, providerMaxTimeoutMillis)
    }

    fun resolve(
        executionMode: PythonExecutionMode,
        configuredTimeoutMillis: Long?,
        providerMaxTimeoutMillis: Long,
    ): Long {
        require(providerMaxTimeoutMillis > 0L) { "Python provider timeout limit must be positive" }
        if (executionMode == PythonExecutionMode.LONG_RUNNING) {
            require(configuredTimeoutMillis == null) {
                "Long-running Python execution cannot carry a bounded timeout"
            }
            return 0L
        }
        require(
            configuredTimeoutMillis == null || isValidConfiguredTimeout(configuredTimeoutMillis),
        ) { "Configured Python timeout is outside the host limit" }
        return minOf(
            configuredTimeoutMillis ?: DEFAULT_TIMEOUT_MILLIS,
            providerMaxTimeoutMillis,
        )
    }
}
