package org.autojs.autojs.theme

/**
 * Theme callbacks may create and register more views, including during an Activity's resume.
 * Keep registration and dead-reference cleanup synchronized, but invoke callbacks outside the
 * lock on a snapshot. Newly registered views receive the current color from their add method.
 *
 * zh-CN: 主题回调可能创建并注册新控件, 包括 Activity 恢复期间. 注册与失效引用清理加锁,
 * 回调在锁外遍历快照, 新注册的控件由各自的 add 方法立即应用当前颜色.
 */
internal class ThemeColorTargetRegistry<T : Any>(private val isAlive: (T) -> Boolean) {

    private val targets = ArrayList<T>()

    fun add(target: T) {
        synchronized(targets) { targets.add(target) }
    }

    fun forEach(action: (T) -> Unit) {
        val snapshot = synchronized(targets) {
            targets.removeAll { !isAlive(it) }
            targets.toList()
        }
        snapshot.forEach(action)
    }
}
