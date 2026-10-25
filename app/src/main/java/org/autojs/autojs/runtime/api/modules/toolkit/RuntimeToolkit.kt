package org.autojs.autojs.runtime.api.modules.toolkit

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.pick.PickResultType
import org.autojs.autojs.core.automator.toolkit.ActionPerformer
import org.autojs.autojs.core.automator.toolkit.ScrollDirection
import org.autojs.autojs.core.automator.toolkit.Toolkit
import org.autojs.autojs.core.automator.toolkit.ToolkitBindings
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.flow.FlowCondition
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.App
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The toolkit of one [ScriptRuntime] (roadmap P4.1, the thin Android side): a [Toolkit] over
 * `UiObject` with the uptime clock and the interruptible sleep of the script thread, plus the
 * single daemon worker thread (`toolkit-<engineId>`, started on first use, idle ones die) on
 * which the popup guard runs its passes. Created on first use and closed with the runtime
 * through its closeable manager, which stops the running popup guards and the worker. The
 * JavaScript surface (P4.2) is [api].
 * zh-CN: 一个 [ScriptRuntime] 的工具集 (Roadmap P4.1 的薄 Android 侧): 基于 `UiObject` 的 [Toolkit], 使用 uptime 时钟与脚本线程的可中断休眠,
 * 外加弹窗守护跑各轮所用的单条守护工作线程 (`toolkit-<engineId>`, 首次使用时启动, 空闲即消亡). 首次使用时创建, 随运行时经其 closeable
 * 管理器关闭 (停止运行中的弹窗守护与工作线程). JavaScript 表面 (P4.2) 为 [api].
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class RuntimeToolkit private constructor(val scriptRuntime: ScriptRuntime) : Closeable {

    val bindings = RuntimeToolkitBindings(scriptRuntime, this)

    val actions = RuntimeActionPerformer(scriptRuntime)

    val toolkit: Toolkit<UiObject> = Toolkit(
        bindings,
        actions,
        clock = { SystemClock.uptimeMillis() },
        sleep = ::sleepInterruptibly,
        fatal = { it is ScriptInterruptedException },
    )

    /** The JavaScript surface (roadmap P4.2). zh-CN: JavaScript 表面 (Roadmap P4.2). */
    val api: ToolkitApi by lazy { ToolkitApi(this) }

    private val closed = AtomicBoolean(false)

    /** The running popup guards, closed with the runtime. zh-CN: 运行中的弹窗守护, 随运行时关闭. */
    private val guards: MutableSet<AutoCloseable> = Collections.synchronizedSet(LinkedHashSet())

    private val executorLazy = lazy {
        ThreadPoolExecutor(0, 1, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
            Thread(r, "toolkit-${scriptRuntime.engines.myEngine().id}").apply { isDaemon = true }
        }
    }

    private val executor: ThreadPoolExecutor by executorLazy

    /** Whether the worker thread is alive right now, for diagnostics. zh-CN: 工作线程此刻是否存活, 供诊断. */
    val isWorkerAlive: Boolean get() = executorLazy.isInitialized() && executor.poolSize > 0

    /**
     * Runs [task] on the worker thread, inside the engine's Rhino context; dropped once closed.
     * zh-CN: 在工作线程上于引擎的 Rhino Context 内运行 [task]; 关闭后丢弃.
     */
    fun execute(task: Runnable) {
        if (closed.get()) return
        try {
            executor.execute { runInContext(task) }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Dropped a toolkit task after shutdown: ${e.message}")
        }
    }

    private fun runInContext(task: Runnable) {
        (scriptRuntime.engines.myEngine() as? RhinoJavaScriptEngine)?.enterContext() ?: Context.enter()
        try {
            task.run()
        } catch (e: Throwable) {
            Log.w(TAG, "A toolkit task failed", e)
        } finally {
            Context.exit()
        }
    }

    /** Tracks a guard so that [close] stops it; a guard registered after closing is stopped at once. zh-CN: 登记守护以便 [close] 停止它; 关闭后登记的守护立即停止. */
    fun register(guard: AutoCloseable) {
        guards += guard
        if (closed.get()) {
            guards -= guard
            guard.close()
        }
    }

    fun unregister(guard: AutoCloseable) {
        guards -= guard
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(guards) { guards.toList() }.forEach { guard -> runCatching { guard.close() } }
        guards.clear()
        if (executorLazy.isInitialized()) executor.shutdownNow()
    }

    companion object {

        private const val TAG = "RuntimeToolkit"

        private const val PROPERTY_KEY = "toolkit.runtime"

        private const val KEEP_ALIVE_SECONDS = 10L

        /** The toolkit of [scriptRuntime], created on first use and closed with the runtime. zh-CN: [scriptRuntime] 的工具集, 首次使用时创建, 随运行时关闭. */
        fun of(scriptRuntime: ScriptRuntime): RuntimeToolkit = synchronized(scriptRuntime) {
            scriptRuntime.getProperty(PROPERTY_KEY) as? RuntimeToolkit ?: RuntimeToolkit(scriptRuntime).also {
                scriptRuntime.putProperty(PROPERTY_KEY, it)
                scriptRuntime.closeableManager.add(it)
            }
        }

        /** The script thread's sleep: a stop of the script interrupts it. zh-CN: 脚本线程的休眠: 停止脚本即中断它. */
        fun sleepInterruptibly(millis: Long) {
            if (Thread.currentThread().isInterrupted) throw ScriptInterruptedException()
            try {
                Thread.sleep(millis)
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }

    }

}

/**
 * [ToolkitBindings] over a script: targets are `UiObject`s (found while they refresh), script
 * functions (called; their result is the node or the wait value) or selectors in any `pickup`
 * form (searched with `UiSelector.pickup` under the root); values are rendered with `detect`;
 * the current package comes from the runtime's activity info; window changes come from the
 * accessibility event bus (window state / content changed, debounced) and are handed to the
 * toolkit's worker thread. A service that cannot be started fails a search with
 * `A11Y_UNAVAILABLE`.
 * zh-CN: 脚本上的 [ToolkitBindings]: 目标为 `UiObject` (能刷新即算找到), 脚本函数 (调用之, 结果即节点或等待值) 或 `pickup` 任一形式的选择器
 * (在根节点下以 `UiSelector.pickup` 搜索); 值以 `detect` 呈现; 当前包名来自运行时的活动信息; 窗口变化来自无障碍事件总线 (窗口状态 / 内容
 * 变化, 去抖) 并交给工具集的工作线程. 无法启动的服务使搜索以 `A11Y_UNAVAILABLE` 失败.
 */
internal class RuntimeToolkitBindings(private val scriptRuntime: ScriptRuntime, private val owner: RuntimeToolkit) : ToolkitBindings<UiObject> {

    /**
     * [block] inside the engine's Rhino context and a top call: a toolkit worker thread has neither,
     * and wrapping a node collection into a script array (as a pickup does) needs both.
     * zh-CN: 在引擎的 Rhino 上下文与一次顶层调用内运行 [block]: 工具集的工作线程两者皆无, 而把节点集合包装成脚本数组 (拾取时会这样做) 两者都要.
     */
    fun <T> withTopCall(block: () -> T): T = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        when {
            RhinoScriptRuntime.hasTopCall(cx) -> block()
            else -> @Suppress("UNCHECKED_CAST") (RhinoScriptRuntime.doTopCall(Callable { _, _, _, _ -> block() }, cx, scriptRuntime.topLevelScope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray(), false) as T)
        }
    }

    override fun find(target: Any?, root: UiObject?): UiObject? = withTopCall { findNow(target, root) }

    private fun findNow(target: Any?, root: UiObject?): UiObject? = when (val t = RhinoUtils.unwrap(target)) {
        is UiObject -> t.takeIf { it.refresh() }
        is Callable -> nodeOf(callScript(t))
        is Function0<*> -> nodeOf(t())
        else -> {
            val selector = selectorOf(t)
            ensureService(STEP_FIND, describe(selector))
            nodeOf(UiSelector.pickup(scriptRuntime, root, selector, null, PickResultType.WIDGET, null))
        }
    }

    override fun findAll(target: Any?, root: UiObject?): List<UiObject> = withTopCall { findAllNow(target, root) }

    private fun findAllNow(target: Any?, root: UiObject?): List<UiObject> = when (val t = RhinoUtils.unwrap(target)) {
        is UiObject -> if (t.refresh()) listOf(t) else emptyList()
        is Callable -> nodesOf(callScript(t))
        is Function0<*> -> nodesOf(t())
        else -> {
            val selector = selectorOf(t)
            ensureService(STEP_FIND, describe(selector))
            nodesOf(UiSelector.pickup(scriptRuntime, root, selector, null, COLLECTION, null))
        }
    }

    override fun probe(cond: Any?, root: UiObject?): Any? = withTopCall { probeNow(cond, root) }

    private fun probeNow(cond: Any?, root: UiObject?): Any? = when (val c = RhinoUtils.unwrap(cond)) {
        is UiObject -> c.takeIf { it.refresh() }
        is Callable -> satisfied(callScript(c))
        is Function0<*> -> satisfied(c())
        else -> when {
            FlowCondition.isSelectorForm(c) -> {
                ensureService(STEP_FIND, describe(c))
                found(UiSelector.pickup(scriptRuntime, root, c, null, PickResultType.WIDGET, null))
            }
            else -> satisfied(c)
        }
    }

    override fun describe(target: Any?): String? = when (val t = RhinoUtils.unwrap(target)) {
        null, is Undefined -> null
        is UiObject -> "UiObject"
        is Callable, is Function0<*> -> "function"
        is App -> t.packageName
        else -> if (FlowCondition.isSelectorForm(t)) FlowCondition.describeSelector(t) else t.toString()
    }

    override fun activeRoot(): UiObject? = scriptRuntime.accessibilityBridge.getRootInActiveWindow()?.let {
        UiObject.createRoot(it, null, scriptRuntime.accessibilityBridge)
    }

    override fun refresh(node: UiObject): Boolean = node.refresh()

    override fun pick(node: UiObject, resultType: Any?): Any? = withTopCall { RhinoUtils.unwrap(UiObject.detect(scriptRuntime, node, null, resultType ?: PickResultType.WIDGET, null)) }

    /** The package in front per the activity info the service feeds, so the service is started first (a fresh install has it idle). zh-CN: 服务喂给活动信息的前台包名, 因此先启动服务 (新安装时服务尚未运行). */
    override fun currentPackage(): String? {
        ensureService(STEP_PACKAGE, null)
        return scriptRuntime.info.latestPackage.takeIf { it.isNotEmpty() }
    }

    override fun resolvePackage(app: Any?): String? = when (val a = RhinoUtils.unwrap(app)) {
        is App -> a.packageName
        is String -> App.getAppByAlias(a)?.packageName
            ?: a.takeIf { scriptRuntime.app.isInstalled(it) }
            ?: scriptRuntime.app.getPackageName(a)
        else -> null
    }

    override fun screenWidth(): Int = ScreenScale.Source.default.scale().width

    override fun screenHeight(): Int = ScreenScale.Source.default.scale().height

    override fun watchWindows(debounceMillis: Long, onChange: Runnable): ToolkitBindings.Watch {
        val bus = AccessibilityService.eventBus
        val options = when {
            debounceMillis > 0 -> A11yEventBus.Options.debounce<A11yEvent>(debounceMillis)
            else -> A11yEventBus.Options.none<A11yEvent>()
        }
        val subscription = bus.subscribe(owner = this, types = WINDOW_EVENT_TYPES, priority = AccessibilityService.PRIORITY_OBSERVER, options = options) { _ ->
            owner.execute(onChange)
            false
        }
        return object : ToolkitBindings.Watch {

            override fun stop() {
                bus.unsubscribe(subscription)
            }

            override fun trigger() {
                owner.execute(onChange)
            }

        }
    }

    // Targets and values. zh-CN: 目标与值.

    private fun selectorOf(value: Any?): Any {
        require(value != null && value !is Undefined) { "A target is required" }
        require(FlowCondition.isSelectorForm(value)) { "Not a target: $value" }
        return value
    }

    private fun nodeOf(value: Any?): UiObject? = RhinoUtils.unwrap(value) as? UiObject

    private fun nodesOf(value: Any?): List<UiObject> = when (val v = RhinoUtils.unwrap(value)) {
        is UiObject -> listOf(v)
        is UiObjectCollection -> v.nodes.filterNotNull()
        is List<*> -> v.mapNotNull { RhinoUtils.unwrap(it) as? UiObject }
        else -> emptyList()
    }

    /** The value a pickup found, or `null` for an empty result (nullish, `false`, `NaN`, an empty string, array or collection). zh-CN: 拾取到的值, 空结果 (空值, `false`, `NaN`, 空串, 空数组或空集合) 为 `null`. */
    private fun found(picked: Any?): Any? {
        val v = RhinoUtils.unwrap(picked)
        return when {
            v == null || v is Undefined -> null
            v == false -> null
            v is Double && v.isNaN() -> null
            v is CharSequence && v.isEmpty() -> null
            v is List<*> && v.isEmpty() -> null
            v is UiObjectCollection && v.isEmpty() -> null
            else -> v
        }
    }

    /** The value when it passes the truthiness rule of the synchronous `wait`, else `null`. zh-CN: 通过同步 `wait` 真值规则时的值, 否则 `null`. */
    private fun satisfied(checked: Any?): Any? = when {
        Global.isWaitConditionSatisfied(checked) -> RhinoUtils.unwrap(checked) ?: RhinoUtils.UNDEFINED
        else -> null
    }

    private fun ensureService(step: String, selector: String?) {
        try {
            scriptRuntime.accessibilityBridge.ensureServiceStarted()
        } catch (e: ScriptInterruptedException) {
            throw e
        } catch (e: ScriptException) {
            throw FlowError.A11yUnavailable(step, selector, e)
        }
        if (scriptRuntime.accessibilityBridge.service == null) throw FlowError.A11yUnavailable(step, selector)
    }

    /** A script call on the calling thread, inside its Rhino context; exceptions propagate as thrown. zh-CN: 在调用线程的 Rhino 上下文内的脚本调用; 异常原样传播. */
    fun callScript(fn: Callable, args: List<Any?> = emptyList()): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        val scope = scriptRuntime.topLevelScope
        val thisObj = Undefined.SCRIPTABLE_UNDEFINED
        val array = args.toTypedArray()
        when {
            RhinoScriptRuntime.hasTopCall(cx) -> fn.call(cx, scope, thisObj, array)
            else -> RhinoScriptRuntime.doTopCall(fn, cx, scope, thisObj, array, false)
        }
    }

    companion object {

        private const val STEP_FIND = "find"

        private const val STEP_PACKAGE = "currentPackage"

        private val COLLECTION = PickResultType.spellings(PickResultType.WidgetCollection).first()

        /**
         * A window's state or content, plus the window list itself (a window added, removed, or made
         * active / focused: an overlay that turns focusable after it appeared raises only this one on
         * API 24, and a guard pass before that finds nothing in it).
         * zh-CN: 窗口的状态或内容, 加上窗口列表本身 (窗口增删或成为活动 / 焦点窗口: 出现后才变为可聚焦的悬浮窗在 API 24 上只发出这一种事件,
         * 而在此之前的守护轮次在其中什么也找不到).
         */
        private val WINDOW_EVENT_TYPES = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)

    }

}

/**
 * [ActionPerformer] over `UiObject` and the script's gesture dispatcher: node actions are the
 * `UiObject` methods; a scroll uses the directional action when the node supports it, else the
 * page action, else scroll forward / backward; gestures and the back key go through the
 * script's automator (its servant Looper), launching through the app utilities.
 * zh-CN: 基于 `UiObject` 与脚本手势派发器的 [ActionPerformer]: 节点动作即 `UiObject` 方法; 滚动优先用节点支持的方向动作, 其次翻页动作,
 * 最后 scroll forward / backward; 手势与返回键经脚本的 automator (其 servant Looper), 启动应用经 app 工具类.
 */
internal class RuntimeActionPerformer(private val scriptRuntime: ScriptRuntime) : ActionPerformer<UiObject> {

    override fun click(node: UiObject): Boolean = node.click()

    override fun longClick(node: UiObject): Boolean = node.longClick()

    override fun focus(node: UiObject): Boolean = node.focus()

    override fun setText(node: UiObject, text: String): Boolean = node.setText(text)

    override fun imeEnter(node: UiObject): Boolean = node.imeEnter()

    override fun scroll(node: UiObject, direction: ScrollDirection): Boolean {
        val directional = "SCROLL_${direction.name}"
        if (node.hasAction(directional)) {
            return when (direction) {
                ScrollDirection.UP -> node.scrollUp()
                ScrollDirection.DOWN -> node.scrollDown()
                ScrollDirection.LEFT -> node.scrollLeft()
                ScrollDirection.RIGHT -> node.scrollRight()
            }
        }
        val page = "PAGE_${direction.name}"
        if (node.hasAction(page)) {
            return when (direction) {
                ScrollDirection.UP -> node.pageUp()
                ScrollDirection.DOWN -> node.pageDown()
                ScrollDirection.LEFT -> node.pageLeft()
                ScrollDirection.RIGHT -> node.pageRight()
            }
        }
        return if (direction.isForward) node.scrollForward() else node.scrollBackward()
    }

    override fun tap(x: Int, y: Int): Boolean = scriptRuntime.automator.click(x, y)

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Long): Boolean = scriptRuntime.automator.swipe(x1, y1, x2, y2, durationMillis.toInt())

    override fun back(): Boolean = scriptRuntime.automator.back()

    override fun launch(packageName: String): Boolean = scriptRuntime.app.launchPackage(packageName)

}
