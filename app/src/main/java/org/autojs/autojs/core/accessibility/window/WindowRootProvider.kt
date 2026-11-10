package org.autojs.autojs.core.accessibility.window

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.core.accessibility.AccessibilityService

/** [WindowRootPolicy] over the framework window type. zh-CN: 以框架窗口类型为参数的 [WindowRootPolicy]. */
typealias A11yWindowRootPolicy = WindowRootPolicy<AccessibilityWindowInfo>

/**
 * [WindowRootResolver] bound to an accessibility service looked up on every query through
 * [service] (never cached: the bound instance changes across service restarts). Roots returned on
 * API < 33 are fresh objects the caller owns and recycles, as with the framework getters.
 *
 * zh-CN: 绑定到无障碍服务的 [WindowRootResolver], 每次查询经 [service] 查找服务 (不缓存: 绑定实例会随服务重启而变化).
 * API < 33 上返回的根节点是调用方拥有并负责回收的新对象, 与框架的 getter 一致.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class WindowRootProvider(
    /**
     * When true the window list comes from `service.windowsOnAllDisplays` (all displays) instead
     * of `service.windows` (default display only). Used by runtimes bound to a virtual display.
     * zh-CN: 为 true 时窗口列表取自 `service.windowsOnAllDisplays` (所有屏幕), 而非只含默认屏的
     * `service.windows`; 供绑定虚拟屏的运行时使用.
     */
    private val allDisplays: Boolean = false,
    // Last on purpose: the existing `WindowRootProvider { service }` call sites keep working.
    // zh-CN: 刻意放在最后, 使原有 `WindowRootProvider { service }` 调用形式保持可用.
    private val service: () -> AccessibilityService?,
) {

    private val resolver = WindowRootResolver(ServiceSource()) { message, error -> Log.w(TAG, message, error) }

    /** See [WindowRootResolver.roots]. zh-CN: 见 [WindowRootResolver.roots]. */
    fun roots(policy: A11yWindowRootPolicy): List<AccessibilityNodeInfo> = resolver.roots(policy)

    /** See [WindowRootResolver.currentRoot]. zh-CN: 见 [WindowRootResolver.currentRoot]. */
    fun currentRoot(policy: A11yWindowRootPolicy): AccessibilityNodeInfo? = resolver.currentRoot(policy)

    /** See [WindowRootResolver.activeRoot]. zh-CN: 见 [WindowRootResolver.activeRoot]. */
    fun activeRoot(policy: A11yWindowRootPolicy): AccessibilityNodeInfo? = resolver.activeRoot(policy)

    /** `service.windows`, empty without a service or when the framework throws. zh-CN: `service.windows`, 无服务或框架抛出异常时为空. */
    fun windows(): List<AccessibilityWindowInfo> = resolver.windows()

    private inner class ServiceSource : WindowRootResolver.Source<AccessibilityWindowInfo, AccessibilityNodeInfo> {

        override fun windows(): List<AccessibilityWindowInfo>? = service()?.let {
            if (allDisplays) it.windowsOnAllDisplays else it.windows
        }

        override fun rootOf(window: AccessibilityWindowInfo): AccessibilityNodeInfo? = window.root

        override fun activeRoot(): AccessibilityNodeInfo? = service()?.rootInActiveWindow

        override fun fastRoot(): AccessibilityNodeInfo? = service()?.fastRootInActiveWindow

        override fun isApplication(window: AccessibilityWindowInfo) = window.type == AccessibilityWindowInfo.TYPE_APPLICATION

        override fun isFocused(window: AccessibilityWindowInfo) = window.isFocused

        override fun isActive(window: AccessibilityWindowInfo) = window.isActive

    }

    companion object {

        private const val TAG = "WindowRootProvider"

        /** Provider over whichever instance is bound right now. zh-CN: 针对当前绑定实例的 provider. */
        val default by lazy { WindowRootProvider { AccessibilityService.instance } }

    }

}
