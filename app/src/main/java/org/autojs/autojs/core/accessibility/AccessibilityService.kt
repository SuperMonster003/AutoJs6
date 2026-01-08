package org.autojs.autojs.core.accessibility

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.accessibility.AccessibilityTool.Companion.DEFAULT_A11Y_SERVICE_START_TIMEOUT
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.event.EventDispatcher
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.AccessibilityServiceStateChangedEvent
import org.greenrobot.eventbus.EventBus
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Jan 7, 2026.
 */
open class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    val onKeyObserver = OnKeyListener.Observer()
    val keyInterrupterObserver = KeyInterceptor.Observer()
    var fastRootInActiveWindow: AccessibilityNodeInfo? = null
    var bridge: AccessibilityBridge? = null

    // eventType -> (ownerId -> callback)
    private val eventBox = HashMap<Int, MutableMap<String, AccessibilityEventCallback?>>()
    private val eventBoxLock = Any()

    private val gestureEventDispatcher = EventDispatcher<GestureListener>()

    private var mEventExecutor: ExecutorService? = null
    private val eventExecutor: ExecutorService
        get() = mEventExecutor ?: Executors.newSingleThreadExecutor().also { mEventExecutor = it }

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
        instance = this
        val type = event.eventType

        // Mark service as operational when we receive the first accessibility event.
        // zh-CN: 当收到首个无障碍事件时, 将服务标记为可工作状态.
        markOperationalStateIfNeeded()

        // Snapshot callbacks to avoid holding lock while invoking user code.
        // zh-CN: 为回调建立快照, 以避免在调用用户代码时持锁.
        val callbacks: List<AccessibilityEventCallback> = synchronized(eventBoxLock) {
            val bucket = eventBox[type] ?: return@synchronized emptyList()
            bucket.values.filterNotNull().toList()
        }

        if (callbacks.isNotEmpty()) {
            val wrapper = AccessibilityEventWrapper(event)
            callbacks.forEach { cb ->
                cb.onAccessibilityEvent(wrapper)
            }
        }

        if (containsAllEventTypes || eventTypes.contains(type)) {
            if (type == TYPE_WINDOW_STATE_CHANGED || type == TYPE_VIEW_FOCUSED) {
                rootInActiveWindow?.also { fastRootInActiveWindow = it }
            }
            for ((_, delegate) in delegates) {
                val types = delegate.eventTypes
                if (types == null || types.contains(type)) {
                    if (delegate.onAccessibilityEvent(this@AccessibilityService, event)) {
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
        eventExecutor.execute {
            stickOnKeyObserver.onKeyEvent(event.keyCode, event)
            onKeyObserver.onKeyEvent(event.keyCode, event)
        }
        return keyInterrupterObserver.onInterceptKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onGesture(gestureId: Int): Boolean {
        eventExecutor.execute {
            gestureEventDispatcher.dispatchEvent {
                onGesture(gestureId)
            }
        }
        return false
    }

    override fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return runCatching { super.getRootInActiveWindow() }.getOrNull()
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy: $instance")

        instance = null
        bridge = null

        resetOperationalState()

        mEventExecutor?.shutdownNow()
        callback?.onDisconnected()
        EventBus.getDefault().post(object : AccessibilityServiceStateChangedEvent {})

        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: $serviceInfo")
        instance = this
        callback?.onConnected()
        EventBus.getDefault().post(object : AccessibilityServiceStateChangedEvent {})
        LOCK.lock()
        ENABLED.signalAll()
        LOCK.unlock()
        // FIXME by Stardust on Feb 12, 2017.
        //  ! 有时在无障碍中开启服务后这里不会调用服务也不会运行, 安卓的 BUG ???
        //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
        //  ! Sometimes, when accessibility service of AutoJs6 started,
        //  ! `onServiceConnected` won't be triggered here.
        //  ! Is this an Android bug?
    }

    companion object {

        private const val TAG = "AccessibilityService"

        private val delegates = TreeMap<Int, AccessibilityDelegate>()
        private var containsAllEventTypes = false
        private val eventTypes = HashSet<Int>()

        private val LOCK = ReentrantLock()
        private val ENABLED = LOCK.newCondition()
        private val OPERATIONAL = LOCK.newCondition()

        private var callback: AccessibilityServiceCallback? = null

        @Volatile
        var hasOperationalState = false

        var instance: AccessibilityService? = null
            private set

        var bridge: AccessibilityBridge?
            get() = instance?.bridge
            set(value) {
                instance?.bridge = value
            }

        val stickOnKeyObserver = OnKeyListener.Observer()

        fun hasInstance() = instance != null

        private fun markOperationalStateIfNeeded() {
            if (hasOperationalState) return
            LOCK.lock()
            try {
                if (hasOperationalState) return
                hasOperationalState = true
                OPERATIONAL.signalAll()
            } finally {
                LOCK.unlock()
            }
        }

        private fun resetOperationalState() {
            LOCK.lock()
            try {
                hasOperationalState = false
            } finally {
                LOCK.unlock()
            }
        }

        fun addDelegate(uniquePriority: Int, delegate: AccessibilityDelegate) {
            // @Hint by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
            //  ! 用于记录 eventTypes 中的事件 id.
            //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
            //  ! To record the event id in eventTypes.
            delegates[uniquePriority] = delegate
            val set = delegate.eventTypes
            if (set == null) {
                containsAllEventTypes = true
            } else {
                eventTypes.addAll(set)
            }
        }

        fun stop() = runCatching {
            instance?.disableSelf()
            instance = null
        }.isSuccess

        fun waitForStarted(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT): Boolean {
            if (hasInstance()) {
                return true
            }
            LOCK.lock()
            try {
                if (hasInstance()) {
                    return true
                }
                if (timeout == -1L) {
                    ENABLED.await()
                    return true
                }
                return ENABLED.await(timeout, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return false
            } finally {
                LOCK.unlock()
            }
        }

        // Wait until the service becomes operational (not only connected).
        // zh-CN: 等待服务进入可工作状态 (不只是已连接).
        fun waitForOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT): Boolean {
            if (hasInstance() && hasOperationalState) {
                return true
            }
            LOCK.lock()
            try {
                if (hasInstance() && hasOperationalState) {
                    return true
                }
                if (timeout == -1L) {
                    OPERATIONAL.await()
                    return true
                }
                return OPERATIONAL.await(timeout, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return false
            } finally {
                LOCK.unlock()
            }
        }

        @JvmStatic
        fun clearAccessibilityEventCallback() {
            instance?.let { svc ->
                synchronized(svc.eventBoxLock) {
                    svc.eventBox.clear()
                }
            }
        }

        fun setCallback(listener: AccessibilityServiceCallback?) {
            callback = listener
        }

        interface GestureListener {
            fun onGesture(gestureId: Int)
        }

    }

}
