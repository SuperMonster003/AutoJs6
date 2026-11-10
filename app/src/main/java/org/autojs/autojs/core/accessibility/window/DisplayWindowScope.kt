package org.autojs.autojs.core.accessibility.window

/**
 * A task-level window scope: which display a script's accessibility queries may see, combined
 * with the window filter the script itself installed (`auto.setWindowFilter`).
 *
 * Both parts are kept separate on purpose. A script may freely set, replace or clear its own
 * filter, but it must not be able to escape the display its task was configured to run on;
 * otherwise a zero-change script running on a virtual screen would silently start reading the
 * windows of the physical screen. The effective filter is the **intersection** of the two.
 *
 * Android-free: [W] is opaque and the display of a window is read through [displayIdOf], so the
 * composition rules are unit tested on a plain JVM.
 *
 * zh-CN: 任务级窗口作用域: 脚本的无障碍查询被限定在哪个显示屏上, 并与脚本自己安装的窗口过滤器
 * (`auto.setWindowFilter`) 组合. 两者刻意分开保存: 脚本可以自由设置/替换/清除自己的过滤器, 但不能
 * 逃出任务被配置的显示屏, 否则零改动脚本在虚拟屏运行时会悄悄读到物理主屏的窗口. 生效的过滤器是两者的**交集**.
 * 本类不依赖 Android: [W] 为不透明类型, 窗口所属 display 经 [displayIdOf] 读取, 组合规则因此可在纯 JVM 上单测.
 */
class DisplayWindowScope<W : Any>(private val displayIdOf: (W) -> Int) {

    companion object {

        /** No display bound: the default-display behaviour of the framework. zh-CN: 未绑定 display, 沿用框架默认屏行为. */
        const val NO_SCOPE = -1

    }

    @Volatile
    private var displayId: Int = NO_SCOPE

    @Volatile
    private var scriptFilter: ((W) -> Boolean)? = null

    /** Whether this scope is bound to a particular (normally virtual) display. */
    val isScoped: Boolean get() = displayId != NO_SCOPE

    /** The bound display id, or [NO_SCOPE]. */
    val scopedDisplayId: Int get() = displayId

    /** Binds (or clears, with [NO_SCOPE]) the display of every query of this bridge. */
    fun setDisplayScope(id: Int) {
        displayId = id
    }

    /** Installs the script's own window filter; [null] removes it but keeps the display scope. */
    fun setScriptFilter(filter: ((W) -> Boolean)?) {
        scriptFilter = filter
    }

    /**
     * The filter `WindowRootPolicy` should use, or `null` when neither part applies (the framework's
     * original single-active-root behaviour). A throwing accessor or script filter rejects just that
     * window instead of failing the whole query.
     *
     * zh-CN: `WindowRootPolicy` 应使用的过滤器; 两部分都不存在时为 `null` (框架原本的单一活动窗口根节点行为).
     * 读取 display 或执行脚本过滤器抛异常时只拒绝该窗口, 不让整次查询失败.
     */
    fun effectiveFilter(): ((W) -> Boolean)? {
        val scope = displayId
        val script = scriptFilter
        if (scope == NO_SCOPE && script == null) return null
        return { window ->
            val inScope = if (scope == NO_SCOPE) true else try {
                displayIdOf(window) == scope
            } catch (_: Throwable) {
                false
            }
            val passesScript = if (script == null) true else try {
                script(window)
            } catch (_: Throwable) {
                false
            }
            inScope && passesScript
        }
    }

}
