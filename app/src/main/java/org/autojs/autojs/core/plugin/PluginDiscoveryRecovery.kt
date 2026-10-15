package org.autojs.autojs.core.plugin

/**
 * Which plugins a discovery should wake and how the second discovery replaces the first, without Android types so JVM
 * tests can drive it. Some systems (ColorOS) refuse to bind the service of a plugin that never ran; waking the plugin
 * through its wake activity, as the Plugin Center "Activate" action does, lets the next bind succeed.
 *
 * Only failed services of packages [shouldWake] accepts (enabled and authorized ones) are woken, each package at most
 * once per [throttleMillis], so a plugin whose wake never helps is not started again on every refresh.
 *
 * zh-CN: 发现过程应唤醒哪些插件, 以及第二次发现如何替换第一次的结果; 不含 Android 类型, 便于 JVM 测试. 部分系统 (ColorOS)
 * 拒绝绑定从未运行过的插件的服务; 像插件中心的 "激活" 那样经唤醒 Activity 唤醒插件后, 下一次绑定即可成功. 只唤醒 [shouldWake]
 * 接受的包 (已启用且已授权) 中失败的服务, 每个包在 [throttleMillis] 内至多唤醒一次, 因此唤醒无效的插件不会在每次刷新时都被再次启动.
 */
class PluginDiscoveryRecovery(
    private val throttleMillis: Long = DEFAULT_THROTTLE_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastWake = HashMap<String, Long>()

    /**
     * The packages to wake among [items]: those with a failed service that [shouldWake] accepts and that were not
     * woken within the throttle window; they are recorded as woken now.
     * zh-CN: [items] 中应唤醒的包: 有服务失败, [shouldWake] 接受, 且不在节流窗口内; 这些包记为此刻已唤醒.
     */
    fun <T> packagesToWake(
        items: List<T>,
        packageOf: (T) -> String,
        failed: (T) -> Boolean,
        shouldWake: (String) -> Boolean,
    ): List<String> = synchronized(lastWake) {
        val now = clock()
        items.filter(failed).map(packageOf).distinct()
            .filter(shouldWake)
            .filter { packageName -> lastWake[packageName]?.let { now - it >= throttleMillis } ?: true }
            .onEach { packageName -> lastWake[packageName] = now }
    }

    companion object {
        /** A plugin whose wake did not help is not woken again within this time. zh-CN: 唤醒无效的插件在此时间内不再唤醒. */
        const val DEFAULT_THROTTLE_MILLIS = 60_000L

        /**
         * [first] with every failed item replaced by the item of the same key in [second], in the order of [first].
         * zh-CN: 把 [first] 中失败的条目替换为 [second] 中键相同的条目, 保持 [first] 的顺序.
         */
        fun <T, K> merge(first: List<T>, second: List<T>, keyOf: (T) -> K, failed: (T) -> Boolean): List<T> {
            val replacements = second.associateBy(keyOf)
            return first.map { item -> if (failed(item)) replacements[keyOf(item)] ?: item else item }
        }
    }
}
