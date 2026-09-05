package org.autojs.autojs.core.accessibility

import android.content.Intent
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
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener
import org.autojs.autojs.core.accessibility.registry.ConnectionListeners
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.event.EventDispatcher
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.AccessibilityServiceStateChangedEvent
import org.greenrobot.eventbus.EventBus
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
open class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    val onKeyObserver = OnKeyListener.Observer()
    val keyInterrupterObserver = KeyInterceptor.Observer()
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

    @Volatile
    private var isDisposed = false

    // eventType -> (ownerId -> callback)
    private val eventBox = HashMap<Int, MutableMap<String, AccessibilityEventCallback?>>()
    private val eventBoxLock = Any()

    /** Number of registered script event callbacks over all event types. zh-CN: 全部事件类型下已注册的脚本事件回调数. */
    val eventCallbackCount: Int
        get() = synchronized(eventBoxLock) { eventBox.values.sumOf { bucket -> bucket.values.count { it != null } } }

    private val gestureEventDispatcher = EventDispatcher<GestureListener>()

    private val eventExecutorLock = Any()
    private var mEventExecutor: ExecutorService? = null
    private val eventExecutor: ExecutorService
        get() = synchronized(eventExecutorLock) {
            mEventExecutor ?: Executors.newSingleThreadExecutor().also { mEventExecutor = it }
        }

    private fun updateFastRootInActiveWindow(root: AccessibilityNodeInfo) {
        if (fastRootInActiveWindow === root) return
        recycleFastRootInActiveWindow()
        fastRootInActiveWindow = root
    }

    @Suppress("DEPRECATION")
    private fun recycleFastRootInActiveWindow() {
        fastRootInActiveWindow?.let { root ->
            runCatching { root.recycle() }
        }
        fastRootInActiveWindow = null
    }

    private fun eventNameToType(event: String): Int {
        return try {
            AccessibilityEvent::class.java.getField(
                "TYPE_${event.uppercase(Language.getPrefLanguage().locale)}"
            ).get(null) as Int
        } catch (_: NoSuchFieldException) {
            throw IllegalArgumentException("Unknown event: $event")
        }
    }

    fun addAccessibilityEventCallback(ownerId: String, name: String, callback: AccessibilityEventCallback?) {
        val type = eventNameToType(name)
        synchronized(eventBoxLock) {
            val bucket = eventBox.getOrPut(type) { HashMap() }
            if (callback == null) {
                bucket.remove(ownerId)
                if (bucket.isEmpty()) eventBox.remove(type)
            } else {
                bucket[ownerId] = callback
            }
        }
    }

    fun removeAccessibilityEventCallback(ownerId: String, name: String) {
        val type = eventNameToType(name)
        synchronized(eventBoxLock) {
            val bucket = eventBox[type] ?: return
            bucket.remove(ownerId)
            if (bucket.isEmpty()) eventBox.remove(type)
        }
    }

    fun removeAllAccessibilityEventCallbacks(ownerId: String) {
        synchronized(eventBoxLock) {
            val it = eventBox.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                entry.value.remove(ownerId)
                if (entry.value.isEmpty()) it.remove()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (isDisposed) return
        val type = event.eventType

        // Adopts this instance and marks it operational on the first event,
        // which also covers the case where onServiceConnected was never invoked.
        // zh-CN: 采纳当前实例并在首个事件时标记为可工作状态, 同时覆盖 onServiceConnected 未被调用的情况.
        registry.onEvent(this)

        val now = SystemClock.elapsedRealtime()
        eventCount.incrementAndGet()
        lastEventType = type
        lastEventElapsedRealtime = now
        if (boundAtElapsedRealtime == 0L) boundAtElapsedRealtime = now

        // Lock-free read of the volatile observer snapshot; only field values are handed out.
        // zh-CN: 无锁读取 volatile 的观察者快照; 只向外传递字段值.
        val observers = eventObservers
        if (observers.isNotEmpty()) {
            val record = A11yEventRecord.of(event)
            observers.forEach { (owner, observer) ->
                try {
                    observer(record)
                } catch (e: Throwable) {
                    Log.e(TAG, "Unhandled exception in accessibility event observer: $owner", e)
                }
            }
        }

        // Snapshot callbacks to avoid holding lock while invoking user code.
        // zh-CN: 为回调建立快照, 以避免在调用用户代码时持锁.
        val callbacks: List<AccessibilityEventCallback> = synchronized(eventBoxLock) {
            val bucket = eventBox[type] ?: return@synchronized emptyList()
            bucket.values.filterNotNull().toList()
        }

        if (callbacks.isNotEmpty()) {
            val wrapper = AccessibilityEventWrapper(event)
            callbacks.forEach { cb ->
                try {
                    cb.onAccessibilityEvent(wrapper)
                } catch (e: Throwable) {
                    Log.e(TAG, "Unhandled exception in accessibility event callback", e)
                }
            }
        }

        val delegateSnapshot = synchronized(delegatesLock) {
            if (containsAllEventTypes || eventTypes.contains(type)) {
                delegates.values.toList()
            } else {
                emptyList()
            }
        }
        if (delegateSnapshot.isNotEmpty()) {
            if (type == TYPE_WINDOW_STATE_CHANGED || type == TYPE_VIEW_FOCUSED) {
                rootInActiveWindow?.let { updateFastRootInActiveWindow(it) }
            }
            for (delegate in delegateSnapshot) {
                val types = try {
                    delegate.eventTypes
                } catch (e: Throwable) {
                    Log.e(TAG, "Unhandled exception while reading accessibility delegate event types: $delegate", e)
                    continue
                }
                if (types == null || types.contains(type)) {
                    val consumed = try {
                        delegate.onAccessibilityEvent(this@AccessibilityService, event)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Unhandled exception in accessibility delegate: $delegate", e)
                        false
                    }
                    if (consumed) {
                        break
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isDisposed) {
            try {
                eventExecutor.execute {
                    try {
                        stickOnKeyObserver.onKeyEvent(event.keyCode, event)
                        onKeyObserver.onKeyEvent(event.keyCode, event)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Unhandled exception in accessibility key observer", e)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to dispatch accessibility key event", e)
            }
        }
        return try {
            keyInterrupterObserver.onInterceptKeyEvent(event)
        } catch (e: Throwable) {
            Log.e(TAG, "Unhandled exception in accessibility key interceptor", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onGesture(gestureId: Int): Boolean {
        if (!isDisposed) {
            try {
                eventExecutor.execute {
                    try {
                        gestureEventDispatcher.dispatchEvent {
                            onGesture(gestureId)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Unhandled exception in accessibility gesture observer", e)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to dispatch accessibility gesture", e)
            }
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
        boundAtElapsedRealtime = SystemClock.elapsedRealtime()
        // @Hint by Stardust on Feb 12, 2017 (rephrased by SuperMonster003 on Sep 5, 2026).
        //  ! Sometimes this callback is never triggered although the service is started and events flow.
        //  ! The registry therefore also adopts the instance from onAccessibilityEvent.
        //  ! zh-CN: 有时服务已启动且事件照常到达, 但此回调不会被触发, 因此注册表也会在 onAccessibilityEvent 中采纳实例.
        registry.onConnected(this)
    }

    private fun disposeService(reason: String) {
        if (isDisposed) return
        isDisposed = true
        Log.v(TAG, "$reason: $this")

        recycleFastRootInActiveWindow()
        synchronized(eventBoxLock) {
            eventBox.clear()
        }

        synchronized(eventExecutorLock) {
            mEventExecutor?.shutdownNow()
            mEventExecutor = null
        }
        // Listeners (state callbacks, drawer event) are notified by the registry when this was the bound instance.
        // zh-CN: 若本实例是当前绑定实例, 注册表会通知各监听者 (状态回调, 抽屉事件).
        registry.onDisposed(this)
    }

    companion object {

        private const val TAG = "AccessibilityService"

        private const val LEGACY_CALLBACK_OWNER = "AccessibilityService.callback"
        private const val STATE_CHANGED_EVENT_OWNER = "AccessibilityService.stateChangedEvent"

        /**
         * Process-wide state of the service (bound instance, operational flag, state listeners).
         * The companion members below are compatibility shells over it.
         * zh-CN: 服务的进程级状态 (绑定实例, 可操作标志, 状态监听者). 下方伴生成员均为其兼容外壳.
         */
        val registry = A11yServiceRegistry<AccessibilityService> { message, error -> Log.e(TAG, message, error) }

        init {
            registry.addListener(STATE_CHANGED_EVENT_OWNER, StateListener { previous, current ->
                if (ConnectionListeners.isBindingChanged(previous, current)) postServiceStateChanged()
            })
        }

        private val delegates = TreeMap<Int, AccessibilityDelegate>()
        private val delegatesLock = Any()
        private var containsAllEventTypes = false
        private val eventTypes = HashSet<Int>()

        /** Number of registered framework delegates. zh-CN: 已注册的框架内部 delegate 数. */
        val delegateCount: Int
            get() = synchronized(delegatesLock) { delegates.size }

        // Lightweight event observers (owner -> callback), kept in the companion so that they survive
        // service restarts. Copy-on-write: the map is replaced under the lock and read without it.
        // This is the seed of the Kotlin-side subscription API of the P1.3 event bus.
        // zh-CN: 轻量事件观察者 (owner -> 回调), 放在伴生对象中以跨服务重启存活. 写时复制: 持锁替换整个映射, 读取时无锁.
        // 这是 P1.3 事件总线 Kotlin 侧订阅 API 的雏形.
        @Volatile
        private var eventObservers: Map<Any, (A11yEventRecord) -> Unit> = emptyMap()
        private val eventObserversLock = Any()

        /**
         * Registers [observer] for [owner], replacing the observer previously registered by the same owner.
         * Observers are invoked on the service's main thread for every delivered event, before script callbacks.
         * zh-CN: 为 [owner] 注册 [observer], 同一 owner 之前的观察者会被替换. 观察者在服务主线程上于每个事件到达时被调用, 先于脚本回调.
         */
        fun addEventObserver(owner: Any, observer: (A11yEventRecord) -> Unit) {
            synchronized(eventObserversLock) {
                eventObservers = LinkedHashMap(eventObservers).apply { put(owner, observer) }
            }
        }

        fun removeEventObserver(owner: Any): Boolean {
            synchronized(eventObserversLock) {
                if (!eventObservers.containsKey(owner)) return false
                eventObservers = LinkedHashMap(eventObservers).apply { remove(owner) }
                return true
            }
        }

        val eventObserverCount: Int
            get() = eventObservers.size

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

        val stickOnKeyObserver = OnKeyListener.Observer()

        fun hasInstance() = registry.isBound

        fun addDelegate(uniquePriority: Int, delegate: AccessibilityDelegate) {
            // @Hint by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
            //  ! 用于记录 eventTypes 中的事件 id.
            //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
            //  ! To record the event id in eventTypes.
            synchronized(delegatesLock) {
                delegates[uniquePriority] = delegate
                val set = delegate.eventTypes
                if (set == null) {
                    containsAllEventTypes = true
                } else {
                    eventTypes.addAll(set)
                }
            }
        }

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

        @JvmStatic
        fun clearAccessibilityEventCallback() {
            instance?.let { svc ->
                synchronized(svc.eventBoxLock) {
                    svc.eventBox.clear()
                }
            }
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
