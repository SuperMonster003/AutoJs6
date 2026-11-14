package org.autojs.autojs.core.accessibility

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.event.A11yEventPulse
import org.autojs.autojs.core.accessibility.window.A11yWindowFacts
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.DisplayWindowScope
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.automator.diagnostics.A11yStats
import org.autojs.autojs.core.automator.search.FastPath
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
 *   of the current mode, except while a display scope is bound (see below).
 * - A display scope bound by [setDisplayScope] (a task configured to run on a virtual screen)
 *   switches the window source to all displays and pins every query to that display. It cannot be
 *   removed by a script's `auto.setWindowFilter`; the script filter is applied on top of it.
 * - Neither is affected by the guard mode blacklist ([AccessibilityConfig]); that is applied by
 *   `UiSelector.findImpl` (roots of blacklisted packages are skipped) and by
 *   `SimpleActionAutomator.performAction` (no action while AutoJs6 itself is in front).
 *
 * zh-CN: 每个脚本运行时对无障碍服务的视图: 脚本可改变的查询设置 (模式, 标志, 窗口过滤器) 及建立在其上的根节点查询.
 * 类名与 public 签名与 Java 版一致 (脚本可能经 `Packages` 引用); 根节点逻辑本身在 [WindowRootProvider] / [WindowRootPolicy].
 * 三个根节点查询各自遵循的设置不同, 且是有意为之 (作为文档化行为保留):
 * [windowRoots] 与 [getRootInCurrentWindow] 遵循窗口过滤器, 无过滤器时解析为当前模式下的活动窗口根节点;
 * [getRootInActiveWindow] 忽略窗口过滤器, 总是解析为当前模式下的活动窗口根节点 (绑定 display 作用域时除外);
 * [setDisplayScope] 绑定的 display 作用域 (被配置为在虚拟屏运行的任务) 会把窗口来源切换到所有屏幕, 并把每次查询
 * 钉在该屏幕上; 脚本的 `auto.setWindowFilter` 无法解除它, 只能叠加在其之上.
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

    /** Whether the framework fast path of the polled queries is on ([FLAG_NO_FAST_PATH] not set). zh-CN: 轮询查询的框架快路径是否开启 (未设置 [FLAG_NO_FAST_PATH]). */
    internal val isFastPathEnabled: Boolean get() = flags and FLAG_NO_FAST_PATH == 0

    /** Whether a single result is refreshed before it is returned ([FLAG_NO_REFRESH_ON_HIT] not set). zh-CN: 单个结果返回前是否刷新 (未设置 [FLAG_NO_REFRESH_ON_HIT]). */
    internal val isRefreshOnHit: Boolean get() = flags and FLAG_NO_REFRESH_ON_HIT == 0

    /**
     * The packages where an empty lookup of the fast path turned out wrong for this script (docs/selector/ROADMAP.md P2.3).
     * zh-CN: 快路径的空查找对本脚本被证实有误的包 (P2.3).
     */
    internal val fastPathLedger = FastPath.Ledger()

    /** Drops the event subscription of [pulse] if it was ever made (script exit). zh-CN: 取消 [pulse] 的事件订阅 (若曾建立; 脚本退出时). */
    fun releasePulse() {
        if (pulseDelegate.isInitialized()) pulse.close()
    }

    private val usageStatsPermission = UsageStatsPermission(context)

    // Looks the service up on every query, so a bridge survives service restarts.
    // zh-CN: 每次查询时查找服务, 因此 bridge 在服务重启后依然可用.
    private val rootProvider = WindowRootProvider { service }

    // The default-display-only window list cannot contain a virtual display's windows. A scoped
    // runtime uses this provider instead, whose windows come from getWindowsOnAllDisplays().
    // zh-CN: 只含默认屏的窗口列表不可能包含虚拟屏窗口; 绑定了作用域的运行时改用这个 provider.
    private val allDisplayRootProvider = WindowRootProvider(allDisplays = true) { service }

    /** The display this task's accessibility queries are pinned to. zh-CN: 本任务无障碍查询被钉住的显示屏. */
    private val displayScope = DisplayWindowScope<AccessibilityWindowInfo> { window -> A11yWindowFacts.displayId(window) }

    private fun activeProvider(): WindowRootProvider =
        if (displayScope.isScoped) allDisplayRootProvider else rootProvider

    @Volatile
    private var lastEmptyScopeDiagnosticMillis = 0L

    /**
     * The current query settings as one immutable policy; rebuilt by [setMode], [setWindowFilter],
     * [setDisplayScope] and [flags].
     * zh-CN: 当前查询设置构成的单个不可变策略; 由 [setMode], [setWindowFilter], [setDisplayScope] 与 [flags] 重建.
     */
    @Volatile
    var windowRootPolicy: A11yWindowRootPolicy = WindowRootPolicy()
        private set

    var flags: Int = DEFAULT_FLAGS
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
    fun windowRoots(): List<AccessibilityNodeInfo> {
        val roots = activeProvider().roots(windowRootPolicy)
        // When a virtual-screen task finds nothing, make the distinction visible: is the system
        // reporting windows for that display at all, or did the selector simply not match?
        // zh-CN: 虚拟屏任务查不到节点时, 让日志能区分「系统根本没上报该屏窗口」与「选择器条件不匹配」.
        if (roots.isEmpty() && displayScope.isScoped) logEmptyScopeDiagnostic()
        return roots
    }

    /**
     * The service's windows in the framework's order, unfiltered by the script's window filter;
     * while a display scope is bound, only the windows of that display are returned.
     * zh-CN: 按框架顺序排列的服务窗口, 不受脚本窗口过滤器影响; 绑定 display 作用域时只返回该屏的窗口.
     */
    fun windows(): List<AccessibilityWindowInfo> {
        val windows = activeProvider().windows()
        val scope = displayScope.scopedDisplayId
        if (scope == DisplayWindowScope.NO_SCOPE) return windows
        return windows.filter { window ->
            runCatching { A11yWindowFacts.displayId(window) == scope }.getOrDefault(false)
        }
    }

    /**
     * The first of [windowRoots], resolved lazily (with a window filter, only the windows up to the
     * first one that has a root are queried).
     * zh-CN: [windowRoots] 的首个根节点, 惰性解析 (有窗口过滤器时只查询到首个有根节点的窗口为止).
     */
    fun getRootInCurrentWindow(): AccessibilityNodeInfo? = activeProvider().currentRoot(windowRootPolicy)

    /**
     * The active window root of the current mode, ignoring the window filter (unlike
     * [getRootInCurrentWindow]); [FLAG_APP_WINDOWS_FALLBACK] applies. While a display scope is
     * bound, the framework's active root is the *main* screen's, so the top root of the bound
     * display is returned instead.
     * zh-CN: 当前模式下的活动窗口根节点, 忽略窗口过滤器 (与 [getRootInCurrentWindow] 不同); [FLAG_APP_WINDOWS_FALLBACK] 生效.
     * 绑定 display 作用域时, 框架的活动窗口根节点属于**主屏**, 因此改返回绑定屏最上层窗口的根节点.
     */
    fun getRootInActiveWindow(): AccessibilityNodeInfo? =
        // `rootInActiveWindow` is the *default display's* active window and cannot be scoped.
        // While a task is bound to a virtual display, "the active window" must be the top window
        // of that display, i.e. the first root passing the scoped filter.
        // zh-CN: `rootInActiveWindow` 是**默认屏**的活动窗口, 无法限定 display; 任务绑定虚拟屏时,
        // 「活动窗口」应是该屏的最上层窗口, 即通过作用域过滤器的首个根节点.
        if (displayScope.isScoped) getRootInCurrentWindow()
        else activeProvider().activeRoot(windowRootPolicy)

    /**
     * Installs/replaces the script's own window filter. When a display scope is bound, the filter
     * is combined with (not substituted for) that scope, so a zero-change script cannot read the
     * physical screen by accident. zh-CN: 安装/替换脚本自己的窗口过滤器; 已绑定 display 作用域时是叠加而非替换,
     * 避免零改动脚本意外读到物理主屏.
     */
    fun setWindowFilter(windowFilter: WindowFilter?) {
        displayScope.setScriptFilter(windowFilter?.let { filter -> { info -> filter.filter(info) } })
        rebuildWindowRootFilter()
    }

    /**
     * Binds every accessibility query of this runtime to one display and switches to the
     * all-displays window source. Called from `ScriptRuntime.initPrologue()` before modules are
     * mounted, so existing JavaScript needs no changes. Pass [DisplayWindowScope.NO_SCOPE] to clear.
     *
     * zh-CN: 把本运行时的所有无障碍查询绑定到某个显示屏, 并切换到全屏窗口来源; 由
     * `ScriptRuntime.initPrologue()` 在模块挂载前调用, 已有 JS 无需修改. 传 [DisplayWindowScope.NO_SCOPE] 可清除.
     */
    fun setDisplayScope(id: Int) {
        displayScope.setDisplayScope(id)
        rebuildWindowRootFilter()
        if (id == DisplayWindowScope.NO_SCOPE) {
            Log.i(TAG, "Accessibility display scope cleared")
            return
        }
        Log.i(TAG, "Accessibility display scope = $id; windows by display: ${windowsByDisplaySummary()}")
    }

    private fun rebuildWindowRootFilter() {
        windowRootPolicy = windowRootPolicy.copy(filter = displayScope.effectiveFilter())
    }

    private fun windowsByDisplaySummary(): String {
        if (service == null) return "service not bound"
        return runCatching {
            allDisplayRootProvider.windows()
                .groupingBy { window -> runCatching { A11yWindowFacts.displayId(window) }.getOrDefault(-1) }
                .eachCount()
                .toString()
        }.getOrDefault("unavailable")
    }

    /** Throttled: selector polling asks for roots many times a second. zh-CN: 节流: 选择器轮询每秒会多次查询根节点. */
    private fun logEmptyScopeDiagnostic() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmptyScopeDiagnosticMillis < 5_000L) return
        lastEmptyScopeDiagnosticMillis = now
        Log.w(
            TAG,
            "No accessibility roots on display ${displayScope.scopedDisplayId}; " +
                "windows by display: ${windowsByDisplaySummary()}",
        )
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

        private const val TAG = "AccessibilityBridge"

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
         * Repeated selector queries, node waits and Flow waits sleep until an accessibility event arrives instead of a
         * fixed interval (roadmap P5.2), at least 50 ms after the attempt before and at most 200 ms on a quiet screen.
         * On by default since 6.8.0 (docs/selector/ROADMAP.md D6 (a), D9); `auto.clearFlags(["eventAssistedPolling"])`
         * or `auto.setFlags(["!eventAssistedPolling"])` restores the fixed interval, `auto.setFlags(["eventAssistedPolling"])`
         * keeps it on.
         * zh-CN: 重复的选择器查询, 节点等待与 Flow 等待休眠到无障碍事件到达为止, 而非固定间隔 (Roadmap P5.2), 距上一次尝试至少 50 ms,
         * 安静的屏幕上至多 200 ms. 自 6.8.0 起默认开启 (D6 (a), D9); `auto.clearFlags(["eventAssistedPolling"])` 或
         * `auto.setFlags(["!eventAssistedPolling"])` 恢复固定间隔, `auto.setFlags(["eventAssistedPolling"])` 保持开启.
         */
        const val FLAG_EVENT_ASSISTED_POLLING = 16

        /**
         * Turns the framework fast path of the polled selector queries off (`auto.setFlags(["noFastPath"])`; docs/selector/ROADMAP.md
         * P2.3, appendix C Q2): every attempt walks the tree, as before 6.8.0.
         * zh-CN: 关闭轮询选择器查询的框架快路径 (`auto.setFlags(["noFastPath"])`; P2.3, 附录 C Q2): 每次尝试都遍历整棵树, 同 6.8.0 之前.
         */
        const val FLAG_NO_FAST_PATH = 32

        /**
         * Turns the check of a single result off (`auto.setFlags(["noRefreshOnHit"])`; docs/selector/ROADMAP.md D6 (c),
         * P3.2, appendix C Q2): `findOnce()`, `findOne(timeout)`, `untilFindOne()` and the single result of `pickup` return
         * the node found without refreshing it, as before 6.8.0.
         * zh-CN: 关闭单个结果的校验 (`auto.setFlags(["noRefreshOnHit"])`; D6 (c), P3.2, 附录 C Q2): `findOnce()`, `findOne(timeout)`,
         * `untilFindOne()` 与 `pickup` 的单个结果不刷新即返回找到的节点, 同 6.8.0 之前.
         */
        const val FLAG_NO_REFRESH_ON_HIT = 64

        /**
         * The flags of a bridge before any `auto.setFlags`, and the base `auto.setFlags(names)` starts from (appendix C Q2):
         * the names it is given are set and the names prefixed with `!` cleared on top of these. [FLAG_EVENT_ASSISTED_POLLING]
         * since 6.8.0 (D6 (a)).
         * zh-CN: 未调用 `auto.setFlags` 时 bridge 的标志, 也是 `auto.setFlags(names)` 的起点 (附录 C Q2): 在其上设置给出的名字, 清除带 `!` 前缀的名字.
         * 自 6.8.0 起含 [FLAG_EVENT_ASSISTED_POLLING] (D6 (a)).
         */
        const val DEFAULT_FLAGS = FLAG_EVENT_ASSISTED_POLLING

        private fun fallbackOf(flags: Int) = when {
            flags and FLAG_APP_WINDOWS_FALLBACK != 0 -> WindowRootPolicy.Fallback.APPLICATION_WINDOWS
            else -> WindowRootPolicy.Fallback.NONE
        }

    }

}
