package org.autojs.autojs.core.plugin.explorer

internal object ExplorerActionExecution {

    fun withFallback(
        attempt: () -> Boolean,
        fallback: () -> Unit,
    ): Boolean {
        val succeeded = runCatching(attempt).getOrDefault(false)
        if (!succeeded) fallback()
        return succeeded
    }
}
