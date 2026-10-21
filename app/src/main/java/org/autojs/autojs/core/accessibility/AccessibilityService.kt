package org.autojs.autojs.core.accessibility

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.accessibility.AccessibilityTool.Companion.DEFAULT_A11Y_SERVICE_START_TIMEOUT
import org.autojs.autojs.core.accessibility.AccessibilityTool.Companion.DEFAULT_A11Y_SERVICE_STOP_TIMEOUT
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
import org.autojs.autojs.core.accessibility.event.AccessibilityDelegate
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener
import org.autojs.autojs.core.accessibility.registry.ConnectionListeners
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.AccessibilityServiceStateChangedEvent
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
open class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    // Compatibility shells: both live in the companion (see there) and survive service restarts.
    // zh-CN: 兼容外壳: 两者均位于伴生对象 (见下), 在服务重启后依然存在.
    val onKeyObserver: OnKeyListener.Observer get() = keyObserver
    val keyInterrupterObserver: KeyInterceptor.Observer get() = keyInterceptors

    var fastRootInActiveWindow: AccessibilityNodeInfo? = null
        private set

    // Per-instance counters read by the service manager dialog; a restarted service starts over.
    // zh-CN: 供服务管理器对话框读取的实例级计数; 服务重启后从零开始.

    /** Number of events delivered to this instance. zh-CN: 本实例收到的事件数. */
    val eventCount = AtomicLong()

    /** Type of the most recent event, meaningful only when [eventCount] > 0. zh-CN: 最近一个事件的类型, 仅在 [eventCount] > 0 时有意义. */
    @Volatile
    var lastEventType = 0
        private set

    /** [SystemClock.elapsedRealtime] at which the most recent event arrived. zh-CN: 最近一个事件到达时的 [SystemClock.elapsedRealtime]. */
    @Volatile
    var lastEventElapsedRealtime = 0L
        private set

    /**
     * [SystemClock.elapsedRealtime] at which this instance was connected; set by `onServiceConnected`
     * and, as that callback is not always invoked, by the first event otherwise. 0 until then.
     * zh-CN: 本实例连接时的 [SystemClock.elapsedRealtime]; 由 `onServiceConnected` 设置, 因该回调并不总会触发, 否则由首个事件补设. 此前为 0.
     */
    @Volatile
    var boundAtElapsedRealtime = 0L
        private set

    /**
     * Whether `onServiceConnected` was invoked for this instance. When the callback never comes,
     * the registry adopts the instance from its first event instead (roadmap P5.5).
     * zh-CN: 本实例是否收到过 `onServiceConnected`. 该回调不来时, 注册表改由首个事件采纳实例 (Roadmap P5.5).
     */
    @Volatile
    var isConnectedCallbackSeen = false
        private set

    @Volatile
    private var adoptionReported = false

    /**
     * Whether this instance is the bound one and was adopted from its first event,
     * `onServiceConnected` never having been invoked (roadmap P5.5).
     * zh-CN: 本实例是否为当前绑定实例且由其首个事件采纳, `onServiceConnected` 从未被调用 (Roadmap P5.5).
     */
    val isAdoptedByEvent: Boolean get() = registry.adoptedByEvent && registry.instance === this

    @Volatile
    private var isDisposed = false

    private fun updateFastRootInActiveWindow(root: AccessibilityNodeInfo) {
        if (fastRootInActiveWindow === root) return
        recycleFastRootInActiveWindow()
        fastRootInActiveWindow = root
    }

    private fun recycleFastRootInActiveWindow() {
        NodeLifecycle.default.release(fastRootInActiveWindow)
        fastRootInActiveWindow = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (isDisposed) return
        val type = event.eventType

        // Adopts this instance and marks it operational on the first event,
        // which also covers the case where onServiceConnected was never invoked.
        // zh-CN: 采纳当前实例并在首个事件时标记为可工作状态, 同时覆盖 onServiceConnected 未被调用的情况.
        registry.onEvent(this)
        if (!isConnectedCallbackSeen && !adoptionReported) {
            adoptionReported = true
            Log.w(TAG, "onServiceConnected was not invoked for $this; adopted from its first event (type $type)")
            onAdoptedWithoutConnectedCallback()
        }

        val now = SystemClock.elapsedRealtime()
        eventCount.incrementAndGet()
        lastEventType = type
        lastEventElapsedRealtime = now
        if (boundAtElapsedRealtime == 0L) boundAtElapsedRealtime = now

        // Kept fresh regardless of who is subscribed (it used to depend on a delegate wanting the
        // event), and before dispatch so that callbacks in MODE_FAST already see the new window.
        // zh-CN: 无论是否有订阅者都保持更新 (原先取决于是否有 delegate 关心该事件), 且在分发之前, 使 MODE_FAST 的回调已能看到新窗口.
        if (type == TYPE_WINDOW_STATE_CHANGED || type == TYPE_VIEW_FOCUSED) {
            rootInActiveWindow?.let { updateFastRootInActiveWindow(it) }
        }

        if (!eventBus.hasSubscribers(type)) return
        val a11yEvent = A11yEvent(this, event)
        try {
            eventBus.dispatch(a11yEvent)
        } finally {
            // The framework may recycle the event from here on (API < 33).
            // zh-CN: 从此刻起框架可能回收该事件 (API < 33).
            a11yEvent.seal()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    /**
     * Observers (non-consuming) and interceptors (consuming) share [keyBus]; the return value is
     * whether an interceptor consumed the key. Handlers run on this thread and are expected to hand
     * the event off (the script side posts to its own looper).
     * zh-CN: 观察者 (非消费) 与拦截器 (消费) 共用 [keyBus]; 返回值为是否有拦截器消费了该按键.
     * 处理器在本线程运行, 应尽快转交事件 (脚本侧会 post 到自己的 looper).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isDisposed) return false
        return keyBus.dispatch(event)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onGesture(gestureId: Int): Boolean {
        if (!isDisposed) {
            gestureBus.dispatch(gestureId)
        }
        return false
    }

    override fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return runCatching { super.getRootInActiveWindow() }.getOrNull()
    }

    override fun onDestroy() {
        disposeService("onDestroy")
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disposeService("onUnbind")
        return super.onUnbind(intent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: $serviceInfo")
        isDisposed = false
        isConnectedCallbackSeen = true
        boundAtElapsedRealtime = SystemClock.elapsedRealtime()
        // @Hint by Stardust on Feb 12, 2017 (rephrased by SuperMonster003 on Sep 5, 2026).
        //  ! Sometimes this callback is never triggered although the service is started and events flow.
        //  ! The registry therefore also adopts the instance from onAccessibilityEvent.
        //  ! zh-CN: 有时服务已启动且事件照常到达, 但此回调不会被触发, 因此注册表也会在 onAccessibilityEvent 中采纳实例.
        registry.onConnected(this)
    }

    /**
     * Called once, from the first event, when the platform skipped `onServiceConnected` for this
     * instance (roadmap P5.5). The registry has adopted the instance by then; subclasses redo here
     * what they would have done in the callback (the service info preferences).
     * zh-CN: 平台跳过了本实例的 `onServiceConnected` 时, 由首个事件调用一次 (Roadmap P5.5). 此时注册表已采纳该实例; 子类在此补做
     * 本应在回调中做的事 (服务信息偏好).
     */
    protected open fun onAdoptedWithoutConnectedCallback() = Unit

    private fun disposeService(reason: String) {
        if (isDisposed) return
        isDisposed = true
        Log.v(TAG, "$reason: $this")

        recycleFastRootInActiveWindow()
        // Subscriptions (script callbacks, delegates, observers, key listeners) live in the companion
        // buses and deliberately outlive this instance.
        // zh-CN: 订阅 (脚本回调, delegate, 观察者, 按键监听) 位于伴生对象的总线中, 有意不随本实例消亡.

        // Listeners (state callbacks, drawer event) are notified by the registry when this was the bound instance.
        // zh-CN: 若本实例是当前绑定实例, 注册表会通知各监听者 (状态回调, 抽屉事件).
        registry.onDisposed(this)
    }

    companion object {

        private const val TAG = "AccessibilityService"

        private const val LEGACY_CALLBACK_OWNER = "AccessibilityService.callback"
        private const val STATE_CHANGED_EVENT_OWNER = "AccessibilityService.stateChangedEvent"

        /* Dispatch order on the accessibility event bus, lowest first. zh-CN: 无障碍事件总线上的分发顺序, 数值小者先. */

        /** Lightweight observers (event monitor). zh-CN: 轻量观察者 (事件监视器). */
        const val PRIORITY_OBSERVER = -100

        /** Script callbacks of `auto.registerEvent`, before the framework delegates as before. zh-CN: `auto.registerEvent` 的脚本回调, 一如既往先于框架内部 delegate. */
        const val PRIORITY_SCRIPT = 0

        /** Key observers run before key interceptors so that they also see intercepted keys. zh-CN: 按键观察者先于拦截器, 因此也能看到被拦截的按键. */
        const val PRIORITY_KEY_OBSERVER = 0
        const val PRIORITY_KEY_INTERCEPTOR = 100

        private val busLog: (String, Throwable?) -> Unit = { message, error -> Log.e(TAG, message, error) }

        // Debounced deliveries run on the main looper, the thread onAccessibilityEvent itself runs on.
        // zh-CN: 去抖投递在主 looper 上执行, 即 onAccessibilityEvent 自身所在的线程.
        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
        private val mainScheduler = A11yEventBus.DeferredScheduler { delayMillis, task ->
            mainHandler.postDelayed(task, delayMillis)
            A11yEventBus.DeferredScheduler.Cancellable { mainHandler.removeCallbacks(task) }
        }

        /**
         * Process-wide state of the service (bound instance, operational flag, state listeners).
         * The companion members below are compatibility shells over it.
         * zh-CN: 服务的进程级状态 (绑定实例, 可操作标志, 状态监听者). 下方伴生成员均为其兼容外壳.
         */
        val registry = A11yServiceRegistry<AccessibilityService> { message, error -> Log.e(TAG, message, error) }

        /**
         * Every accessibility event of the bound instance goes through here: observers
         * ([PRIORITY_OBSERVER]), script callbacks ([PRIORITY_SCRIPT]) and framework delegates (their
         * own priorities, consuming) are all subscriptions. Held here so that subscriptions survive
         * service restarts.
         * zh-CN: 绑定实例的每个无障碍事件都经由此处: 观察者 ([PRIORITY_OBSERVER]), 脚本回调 ([PRIORITY_SCRIPT]) 与框架内部
         * delegate (各自的优先级, 可消费) 都是订阅项. 放在此处使订阅在服务重启后依然存在.
         */
        val eventBus = A11yEventBus<A11yEvent>(
            typeOf = { it.type },
            scheduler = mainScheduler,
            retain = { it.copied() },
            log = busLog,
        )

        /** Key events, typed by key code; see [onKeyEvent]. zh-CN: 按键事件, 以 keyCode 为类型; 见 [onKeyEvent]. */
        val keyBus = A11yEventBus<KeyEvent>(typeOf = { it.keyCode }, log = busLog)

        /** Gesture ids of the deprecated `onGesture` callback. zh-CN: 已弃用的 `onGesture` 回调的手势 id. */
        val gestureBus = A11yEventBus<Int>(typeOf = { it }, log = busLog)

        val keyObserver = OnKeyListener.Observer(keyBus, PRIORITY_KEY_OBSERVER)

        val keyInterceptors = KeyInterceptor.Observer(keyBus, PRIORITY_KEY_INTERCEPTOR)

        /** Legacy name of [keyObserver]. zh-CN: [keyObserver] 的旧名称. */
        val stickOnKeyObserver: OnKeyListener.Observer get() = keyObserver

        init {
            registry.addListener(STATE_CHANGED_EVENT_OWNER, StateListener { previous, current ->
                if (ConnectionListeners.isBindingChanged(previous, current)) postServiceStateChanged()
            })
        }

        /** Number of registered framework delegates. zh-CN: 已注册的框架内部 delegate 数. */
        val delegateCount: Int
            get() = eventBus.count { it.owner is AccessibilityDelegate }

        /** Number of registered script event callbacks over all event types. zh-CN: 全部事件类型下已注册的脚本事件回调数. */
        val scriptCallbackCount: Int
            get() = eventBus.count { it.priority == PRIORITY_SCRIPT }

        val eventObserverCount: Int
            get() = eventBus.count { it.priority == PRIORITY_OBSERVER }

        /**
         * Subscriptions and state listeners on the process-wide buses, by bus, for the memory gate (docs/memory/ROADMAP.md P4.2).
         * zh-CN: 进程级各总线上的订阅项与状态监听者数, 按总线分列, 供内存门禁使用 (docs/memory/ROADMAP.md P4.2).
         */
        fun busCounts(): Map<String, Int> = linkedMapOf(
            "events" to eventBus.subscriptionCount,
            "keys" to keyBus.subscriptionCount,
            "gestures" to gestureBus.subscriptionCount,
            "stateListeners" to registry.listenerCount,
        )

        /**
         * Whether a subscription or listener [owner] belongs to the script runtime [ownerId]: the id itself (script event
         * callbacks), or a [ScriptOwned] object of that runtime (its events module, bridge, event pulse).
         * zh-CN: 订阅项或监听者的属主 [owner] 是否属于脚本运行时 [ownerId]: 即该 id 本身 (脚本事件回调), 或该运行时的 [ScriptOwned] 对象 (其 events 模块, bridge, 事件脉冲).
         */
        fun isOwnedBy(owner: Any, ownerId: String) = owner == ownerId || (owner as? ScriptOwned)?.scriptOwnerId == ownerId

        /** Subscriptions and listeners of the script runtime [ownerId] on every bus. zh-CN: 脚本运行时 [ownerId] 在各总线上的订阅项与监听者数. */
        fun countOwnedBy(ownerId: String): Int =
            eventBus.count { isOwnedBy(it.owner, ownerId) } + keyBus.count { isOwnedBy(it.owner, ownerId) } +
                gestureBus.count { isOwnedBy(it.owner, ownerId) } + registry.countListeners { isOwnedBy(it, ownerId) }

        /**
         * Removes every subscription and listener of the script runtime [ownerId] still on the buses; returns how many. Run when
         * the runtime exits, after its modules have removed their own (roadmap P4.2), so any count above 0 is a missed release.
         * zh-CN: 移除脚本运行时 [ownerId] 仍留在各总线上的全部订阅项与监听者, 返回数量. 在运行时结束, 各模块移除自己的登记之后执行 (ROADMAP P4.2),
         * 因此大于 0 即说明有遗漏的释放.
         */
        fun releaseOwner(ownerId: String): Int =
            eventBus.unsubscribeIf { isOwnedBy(it.owner, ownerId) } + keyBus.unsubscribeIf { isOwnedBy(it.owner, ownerId) } +
                gestureBus.unsubscribeIf { isOwnedBy(it.owner, ownerId) } + registry.removeListenersIf { isOwnedBy(it, ownerId) }

        /**
         * Registers [observer] for [owner], replacing the observer previously registered by the same owner.
         * Observers are invoked on the service's main thread for every delivered event, before script callbacks.
         * zh-CN: 为 [owner] 注册 [observer], 同一 owner 之前的观察者会被替换. 观察者在服务主线程上于每个事件到达时被调用, 先于脚本回调.
         */
        fun addEventObserver(owner: Any, observer: (A11yEventRecord) -> Unit) {
            eventBus.unsubscribeAll(owner)
            eventBus.subscribe(owner = owner, priority = PRIORITY_OBSERVER) { event ->
                observer(event.record)
                false
            }
        }

        fun removeEventObserver(owner: Any): Boolean = eventBus.unsubscribeAll(owner) > 0

        var hasOperationalState: Boolean
            get() = registry.isOperational
            set(value) = if (value) registry.markOperational() else registry.resetOperational()

        val instance: AccessibilityService?
            get() = registry.instance

        // The bridge used by the no-arg UiSelector constructor (e.g. `new UiSelector()` in scripts).
        // It is the bridge of the most recently created script runtime and no longer depends on
        // a service instance being alive, so it survives service restarts.
        // zh-CN: 供无参 UiSelector 构造函数 (如脚本中的 `new UiSelector()`) 使用的 bridge.
        // 它是最近创建的脚本运行时的 bridge, 不再依赖服务实例存活, 因此服务重启后依然可用.
        @Volatile
        private var defaultBridge: AccessibilityBridge? = null

        var bridge: AccessibilityBridge?
            get() = defaultBridge
            set(value) {
                defaultBridge = value
            }

        fun hasInstance() = registry.isBound

        /**
         * The `AccessibilityEvent.TYPE_*` constant named by [name] (case-insensitive, without the prefix),
         * e.g. `"window_state_changed"`.
         * zh-CN: 以 [name] 命名的 `AccessibilityEvent.TYPE_*` 常量 (不区分大小写, 不含前缀), 如 `"window_state_changed"`.
         */
        @JvmStatic
        fun eventTypeOf(name: String): Int {
            return try {
                AccessibilityEvent::class.java.getField(
                    "TYPE_${name.uppercase(Language.getPrefLanguage().locale)}"
                ).get(null) as Int
            } catch (_: NoSuchFieldException) {
                throw IllegalArgumentException("Unknown event: $name")
            }
        }

        /**
         * Framework delegate: a consuming subscription at [priority] (delegates return `true` to
         * stop the chain). Its [AccessibilityDelegate.eventTypes] is read once, here.
         * zh-CN: 框架内部 delegate: 优先级为 [priority] 的消费型订阅项 (delegate 返回 `true` 即终止链). 其 [AccessibilityDelegate.eventTypes] 在此读取一次.
         */
        fun addDelegate(priority: Int, delegate: AccessibilityDelegate) {
            eventBus.subscribe(owner = delegate, types = delegate.eventTypes, priority = priority, consumable = true) { event ->
                delegate.onAccessibilityEvent(event.service, event.raw)
            }
        }

        fun removeDelegate(delegate: AccessibilityDelegate): Boolean = eventBus.unsubscribeAll(delegate) > 0

        /**
         * Script callback slot: one per ([ownerId], event type), a new registration replaces the
         * previous one; a null [callback] only removes. [options] thins out repeated events.
         * zh-CN: 脚本回调槽: 每个 ([ownerId], 事件类型) 一个, 新注册替换旧的; [callback] 为 null 时仅移除. [options] 用于稀释重复事件.
         */
        @JvmOverloads
        fun addAccessibilityEventCallback(
            ownerId: String,
            name: String,
            callback: AccessibilityEventCallback?,
            options: A11yEventBus.Options<A11yEvent> = A11yEventBus.Options(),
        ) {
            val type = eventTypeOf(name)
            removeAccessibilityEventCallback(ownerId, type)
            callback ?: return
            eventBus.subscribe(owner = ownerId, types = setOf(type), priority = PRIORITY_SCRIPT, options = options) { event ->
                callback.onAccessibilityEvent(event.wrapper)
                false
            }
        }

        fun removeAccessibilityEventCallback(ownerId: String, name: String) {
            removeAccessibilityEventCallback(ownerId, eventTypeOf(name))
        }

        private fun removeAccessibilityEventCallback(ownerId: String, type: Int) {
            eventBus.subscriptionsOf(ownerId)
                .filter { it.priority == PRIORITY_SCRIPT && it.types == setOf(type) }
                .forEach { eventBus.unsubscribe(it) }
        }

        /** Removes every script callback of [ownerId]; returns how many. zh-CN: 移除 [ownerId] 的全部脚本回调, 返回数量. */
        fun removeAllAccessibilityEventCallbacks(ownerId: String): Int = eventBus.unsubscribeAll(ownerId)

        fun stop(): Boolean {
            val service = instance
            if (service == null) {
                registry.resetOperational()
                return true
            }
            val disabled = runCatching {
                service.disableSelf()
                true
            }.onFailure {
                Log.w(TAG, "Unable to disable accessibility service", it)
            }.getOrDefault(false)
            if (disabled) {
                service.disposeService("stop")
            }
            return disabled
        }

        // A timeout of -1 waits forever.
        // zh-CN: 超时为 -1 时无限等待.
        fun waitForStarted(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT): Boolean {
            return registry.await(State.CONNECTED, timeout)
        }

        // Wait until the service becomes operational (not only connected).
        // zh-CN: 等待服务进入可工作状态 (不只是已连接).
        fun waitForOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT): Boolean {
            return registry.await(State.OPERATIONAL, timeout)
        }

        // Wait until the bound instance has gone away (the system has unbound the service).
        // zh-CN: 等待绑定实例消失 (系统已解绑服务).
        fun waitForStopped(timeout: Long = DEFAULT_A11Y_SERVICE_STOP_TIMEOUT): Boolean {
            return registry.awaitUnbound(timeout)
        }

        /**
         * Legacy single-slot callback, kept for Kotlin callers.
         * Script runtimes register through [setCallback] with their bridge as owner (see [AccessibilityBridge.setAccessibilityListener]).
         * zh-CN: 旧的单槽回调, 供 Kotlin 调用方使用. 脚本运行时以其 bridge 为 owner 通过 [setCallback] 注册.
         */
        fun setCallback(listener: AccessibilityServiceCallback?) {
            setCallback(LEGACY_CALLBACK_OWNER, listener)
        }

        /**
         * Registers (or with a null [listener] removes) the connection callback of [owner].
         * Each owner holds at most one callback; owners do not affect each other.
         * zh-CN: 注册 (或以 null [listener] 移除) [owner] 的连接回调. 每个 owner 至多一个回调, owner 之间互不影响.
         */
        fun setCallback(owner: Any, listener: AccessibilityServiceCallback?) {
            if (listener == null) {
                registry.removeListener(owner)
            } else {
                registry.addListener(owner, ConnectionListeners.connectionCallback(listener))
            }
        }

        fun addDisconnectCallback(listener: AccessibilityServiceCallback) {
            registry.addListener(listener, ConnectionListeners.disconnectCallback(listener))
        }

        fun removeDisconnectCallback(listener: AccessibilityServiceCallback): Boolean {
            return registry.removeListener(listener)
        }

        private fun postServiceStateChanged() {
            try {
                EventBus.getDefault().post(object : AccessibilityServiceStateChangedEvent {})
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to post accessibility service state changed event", e)
            }
        }

        interface GestureListener {
            fun onGesture(gestureId: Int)
        }

    }

}
