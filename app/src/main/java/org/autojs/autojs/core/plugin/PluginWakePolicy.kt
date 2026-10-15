package org.autojs.autojs.core.plugin

/**
 * When [PluginAutoWake] may wake a plugin whose binding was refused, without Android types so JVM tests can drive it.
 * A wake that helped costs nothing; a wake after which the binding was still refused makes the package wait
 * [backoffMillis] before the next wake, so a plugin whose wake never helps is not started again on every call.
 * zh-CN: [PluginAutoWake] 何时可以唤醒绑定被拒的插件; 不含 Android 类型, 便于 JVM 测试. 有效的唤醒没有代价; 唤醒后绑定仍被拒时,
 * 该包在 [backoffMillis] 内不再唤醒, 因此唤醒无效的插件不会在每次调用时都被再次启动.
 */
class PluginWakePolicy(
    private val backoffMillis: Long = DEFAULT_BACKOFF_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failedAt = HashMap<String, Long>()

    /** False while a recent wake of [packageName] did not help. zh-CN: 最近一次唤醒无效时为 false. */
    fun mayWake(packageName: String): Boolean = synchronized(failedAt) {
        val failed = failedAt[packageName] ?: return true
        clock() - failed >= backoffMillis
    }

    /** The binding succeeded after the wake. zh-CN: 唤醒后绑定成功. */
    fun onWakeHelped(packageName: String): Unit = synchronized(failedAt) { failedAt.remove(packageName) }

    /** The wake could not start, or the binding was still refused after it. zh-CN: 无法唤醒, 或唤醒后绑定仍被拒. */
    fun onWakeFailed(packageName: String): Unit = synchronized(failedAt) { failedAt[packageName] = clock() }

    companion object {
        const val DEFAULT_BACKOFF_MILLIS = 60_000L

        /**
         * Pauses before each binding attempt after a wake: the plugin process usually answers after 300 ms; the
         * whole schedule waits 3 s at most.
         * zh-CN: 唤醒后每次绑定前的等待: 插件进程通常在 300 ms 后可用; 整个序列最多等待 3 s.
         */
        val RETRY_DELAYS_MS = longArrayOf(300L, 400L, 800L, 1500L)
    }
}
