package org.autojs.autojs.core.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.event.A11yEventPulse
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.automator.diagnostics.A11yStats
import org.autojs.autojs.permission.UsageStatsPermission
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs6.R

/**
 * Per-script-runtime view of the accessibility service: the query settings a script can change
 * (mode, flags, window filter) and the root queries built on them. The class name and its public
 * signatures are kept as they were in Java (scripts may reach it through `Packages`); the root
 * logic itself lives in [WindowRootProvider] / [WindowRootPolicy].
 *
 * Root queries differ in what they honour, and this is intentional (kept as documented behaviour):
 * - [windowRoots] and [getRootInCurrentWindow] honour the window filter; without one they resolve
 *   to the active window root of the current mode.
 * - [getRootInActiveWindow] ignores the window filter and always resolves to the active window root
 *   of the current mode.
 * - Neither is affected by the guard mode blacklist ([AccessibilityConfig]); that is applied by
 *   `UiSelector.findImpl` (roots of blacklisted packages are skipped) and by
 *   `SimpleActionAutomator.performAction` (no action while AutoJs6 itself is in front).
 *
 * zh-CN: 每个脚本运行时对无障碍服务的视图: 脚本可改变的查询设置 (模式, 标志, 窗口过滤器) 及建立在其上的根节点查询.
 * 类名与 public 签名与 Java 版一致 (脚本可能经 `Packages` 引用); 根节点逻辑本身在 [WindowRootProvider] / [WindowRootPolicy].
 * 三个根节点查询各自遵循的设置不同, 且是有意为之 (作为文档化行为保留):
 * [windowRoots] 与 [getRootInCurrentWindow] 遵循窗口过滤器, 无过滤器时解析为当前模式下的活动窗口根节点;
 * [getRootInActiveWindow] 忽略窗口过滤器, 总是解析为当前模式下的活动窗口根节点;
 * 两者都不受保护模式黑名单 ([AccessibilityConfig]) 影响, 黑名单由 `UiSelector.findImpl` (跳过黑名单包名的根节点)
 * 与 `SimpleActionAutomator.performAction` (AutoJs6 自身前置时不执行动作) 应用.
 *
 * Created by Stardust on Apr 2, 2017.
 * Transformed by SuperMonster003 on Sep 5, 2026.
 */
abstract class AccessibilityBridge(
    private val context: Context,
    val config: AccessibilityConfig,
    private val uiHandler: UiHandler,
) : ScriptOwned {

    /**
     * The owner id of the script runtime this bridge belongs to (one bridge per runtime), set by the runtime (roadmap P4.2).
     * zh-CN: 本 bridge 所属脚本运行时的 owner id (每个运行时一个 bridge), 由运行时设置 (ROADMAP P4.2).
     */
    @Volatile
    override var scriptOwnerId: String? = null

    fun interface WindowFilter {
        fun filter(info: AccessibilityWindowInfo): Boolean
    }

    /** A tap gesture at device pixels. zh-CN: 设备像素坐标上的点按手势. */
    fun interface Tap {
        fun tap(x: Int, y: Int): Boolean
    }

    /**
     * The owning script's gesture dispatcher, installed by `SimpleActionAutomator`; the nodes a
     * query of this bridge finds use it for `UiObject.clickBounds()`, so that the tap goes through
     * the script's servant Looper; other nodes use a standalone automator.
     * zh-CN: 所属脚本的手势派发器, 由 `SimpleActionAutomator` 安装; 本 bridge 的查询找到的节点以它执行
     * `UiObject.clickBounds()`, 使点按经脚本的 servant Looper 派发; 其它节点使用独立的 automator.
     */
    @Volatile
    var tap: Tap? = null

    /**
     * The tallies behind `auto.stats`: every search and polled query of the selectors bound to
     * this bridge (one bridge per script runtime) and the script's Flow steps.
     * zh-CN: `auto.stats` 背后的计数: 绑定到本 bridge (每个脚本运行时一个) 的选择器的每次搜索与轮询查询, 以及脚本的 Flow 步骤.
     */
    val stats: A11yStats = A11yStats()

    private val pulseDelegate = lazy {
        A11yEventPulse(AccessibilityService.eventBus, AccessibilityService.registry).also { it.scriptOwnerId = scriptOwnerId }
    }

    /**
     * The script's view of the accessibility event stream (roadmap P5.2): `waitForIdle` /
     * `waitForEvent` and event-assisted polling; subscribed on first use, released by [releasePulse].
     * zh-CN: 脚本眼中的无障碍事件流 (Roadmap P5.2): `waitForIdle` / `waitForEvent` 与事件辅助轮询; 首次使用时订阅, 由 [releasePulse] 释放.
     */
    val pulse: A11yEventPulse by pulseDelegate

    /** Whether [FLAG_EVENT_ASSISTED_POLLING] is set. zh-CN: 是否设置了 [FLAG_EVENT_ASSISTED_POLLING]. */
    val isEventAssistedPolling: Boolean get() = flags and FLAG_EVENT_ASSISTED_POLLING != 0

    /** Drops the event subscription of [pulse] if it was ever made (script exit). zh-CN: 取消 [pulse] 的事件订阅 (若曾建立; 脚本退出时). */
    fun releasePulse() {
        if (pulseDelegate.isInitialized()) pulse.close()
    }

    private val usageStatsPermission = UsageStatsPermission(context)

    // Looks the service up on every query, so a bridge survives service restarts.
    // zh-CN: 每次查询时查找服务, 因此 bridge 在服务重启后依然可用.
    private val rootProvider = WindowRootProvider { service }

    /**
     * The current query settings as one immutable policy; rebuilt by [setMode], [setWindowFilter]
     * and [flags].
     * zh-CN: 当前查询设置构成的单个不可变策略; 由 [setMode], [setWindowFilter] 与 [flags] 重建.
     */
    @Volatile
    var windowRootPolicy: A11yWindowRootPolicy = WindowRootPolicy()
        private set

    var flags: Int = 0
        set(value) {
            field = value
            windowRootPolicy = windowRootPolicy.copy(fallback = fallbackOf(value))
            if (value and FLAG_USE_USAGE_STATS != 0) {
                if (!usageStatsPermission.has()) {
                    usageStatsPermission.config()
                    throw SecurityException(context.getString(R.string.error_no_usage_stats_permission))
                }
            }
            infoProvider.useUsageStats = value and FLAG_USE_USAGE_STATS != 0
            infoProvider.useShell = value and FLAG_USE_SHELL != 0
        }

    init {
        config.seal()
    }

    abstract fun ensureServiceStarted(isForcibleRestart: Boolean)

    /**
     * Waits up to [timeoutMillis] for the service to be bound again without asking the user
     * (roadmap P5.5, the Flow `recoverService` option): see `A11yServiceController.awaitAvailable`.
     * zh-CN: 至多等待 [timeoutMillis] 毫秒让服务重新绑定, 不询问用户 (Roadmap P5.5, Flow 的 `recoverService` 选项): 见
     * `A11yServiceController.awaitAvailable`.
     */
    abstract fun awaitServiceAvailable(timeoutMillis: Long): Boolean

    fun ensureServiceStarted() {
        ensureServiceStarted(false)
    }

    fun post(r: Runnable) {
        uiHandler.post(r)
    }

    abstract val service: AccessibilityService?

    /**
     * The roots a query runs against, never containing `null`: the roots of the windows passing
     * the window filter, otherwise the active window root of the current mode (or, when the system
     * does not provide one, the roots [FLAG_APP_WINDOWS_FALLBACK] stands in), otherwise nothing.
     * zh-CN: 查询所针对的根节点, 不含 `null`: 通过窗口过滤器的窗口的根节点; 否则当前模式下的活动窗口根节点
     * (系统不提供时, 由 [FLAG_APP_WINDOWS_FALLBACK] 决定的代替根节点); 否则为空.
     */
    fun windowRoots(): List<AccessibilityNodeInfo> = rootProvider.roots(windowRootPolicy)

    /**
     * The service's windows, unfiltered, in the framework's order; empty without a service.
     * zh-CN: 服务的全部窗口, 未过滤, 按框架的顺序; 无服务时为空.
     */
    fun windows(): List<AccessibilityWindowInfo> = rootProvider.windows()

    /**
     * The first of [windowRoots], resolved lazily (with a window filter, only the windows up to the
     * first one that has a root are queried).
     * zh-CN: [windowRoots] 的首个根节点, 惰性解析 (有窗口过滤器时只查询到首个有根节点的窗口为止).
     */
    fun getRootInCurrentWindow(): AccessibilityNodeInfo? = rootProvider.currentRoot(windowRootPolicy)

    /**
     * The active window root of the current mode, ignoring the window filter (unlike
     * [getRootInCurrentWindow]); [FLAG_APP_WINDOWS_FALLBACK] applies.
     * zh-CN: 当前模式下的活动窗口根节点, 忽略窗口过滤器 (与 [getRootInCurrentWindow] 不同); [FLAG_APP_WINDOWS_FALLBACK] 生效.
     */
    fun getRootInActiveWindow(): AccessibilityNodeInfo? = rootProvider.activeRoot(windowRootPolicy)

    fun setWindowFilter(windowFilter: WindowFilter?) {
        windowRootPolicy = windowRootPolicy.copy(filter = windowFilter?.let { { info -> it.filter(info) } })
    }

    abstract val infoProvider: ActivityInfoProvider

    fun setMode(mode: Int) {
        windowRootPolicy = windowRootPolicy.copy(mode = WindowRootPolicy.modeOf(mode))
    }

    abstract val notificationObserver: AccessibilityNotificationObserver

    /**
     * Registers the connection callback of the script runtime owning this bridge (null removes it).
     * Each runtime keeps its own callback, so scripts no longer overwrite each other's listener.
     * zh-CN: 注册持有此 bridge 的脚本运行时的连接回调 (null 表示移除). 每个运行时各自持有回调, 脚本之间不再互相覆盖.
     */
    @ScriptInterface
    fun setAccessibilityListener(listener: AccessibilityServiceCallback?) {
        AccessibilityService.setCallback(this, listener)
    }

    companion object {

        const val MODE_NORMAL = 0
        const val MODE_FAST = 1

        const val FLAG_FIND_ON_UI_THREAD = 1
        const val FLAG_USE_USAGE_STATS = 2
        const val FLAG_USE_SHELL = 4

        /**
         * While the system does not provide the active window root (right after the service has
         * been bound; on API 26 possibly until the next window change), the application windows of
         * the window list stand in, focused first. Off by default: the queries then report nothing,
         * as they always did.
         * zh-CN: 系统不提供活动窗口根节点时 (服务刚绑定后; API 26 上可能持续到下一次窗口变化), 以窗口列表中的应用窗口代替,
         * 焦点窗口优先. 默认关闭: 此时查询一如既往地返回空.
         */
        const val FLAG_APP_WINDOWS_FALLBACK = 8

        /**
         * Repeated selector queries and Flow waits sleep until an accessibility event arrives
         * instead of a fixed interval (`auto.setFlags(["eventAssistedPolling"])`; roadmap P5.2).
         * zh-CN: 重复的选择器查询与 Flow 等待休眠到无障碍事件到达为止, 而非固定间隔 (`auto.setFlags(["eventAssistedPolling"])`; Roadmap P5.2).
         */
        const val FLAG_EVENT_ASSISTED_POLLING = 16

        private fun fallbackOf(flags: Int) = when {
            flags and FLAG_APP_WINDOWS_FALLBACK != 0 -> WindowRootPolicy.Fallback.APPLICATION_WINDOWS
            else -> WindowRootPolicy.Fallback.NONE
        }

    }

}
