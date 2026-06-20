package org.autojs.autojs.core.accessibility

import android.content.Intent
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
        private set
    var bridge: AccessibilityBridge? = null

    @Volatile
    private var isDisposed = false

    // eventType -> (ownerId -> callback)
    private val eventBox = HashMap<Int, MutableMap<String, AccessibilityEventCallback?>>()
    private val eventBoxLock = Any()

    private val gestureEventDispatcher = EventDispatcher<GestureListener>()

    private var mEventExecutor: ExecutorService? = null
    private val eventExecutor: ExecutorService
        get() = mEventExecutor ?: Executors.newSingleThreadExecutor().also { mEventExecutor = it }

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
        resetOperationalState()
        instance = this
        notifyConnected()
        LOCK.lock()
        try {
            ENABLED.signalAll()
        } finally {
            LOCK.unlock()
        }
        // FIXME by Stardust on Feb 12, 2017.
        //  ! 有时在无障碍中开启服务后这里不会调用服务也不会运行, 安卓的 BUG ???
        //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
        //  ! Sometimes, when accessibility service of AutoJs6 started,
        //  ! `onServiceConnected` won't be triggered here.
        //  ! Is this an Android bug?
    }

    private fun disposeService(reason: String) {
        if (isDisposed) return
        isDisposed = true
        Log.v(TAG, "$reason: $this")

        val wasCurrentInstance = instance === this
        if (wasCurrentInstance) {
            instance = null
        }
        bridge = null
        recycleFastRootInActiveWindow()
        synchronized(eventBoxLock) {
            eventBox.clear()
        }

        mEventExecutor?.shutdownNow()
        mEventExecutor = null
        if (wasCurrentInstance) {
            resetOperationalState()
            notifyDisconnected()
        }
    }

    private fun notifyConnected() {
        try {
            callback?.onConnected()
        } catch (e: Throwable) {
            Log.e(TAG, "Unhandled exception in accessibility connected callback", e)
        }
        postServiceStateChanged()
    }

    private fun notifyDisconnected() {
        try {
            callback?.onDisconnected()
        } catch (e: Throwable) {
            Log.e(TAG, "Unhandled exception in accessibility disconnected callback", e)
        }
        postServiceStateChanged()
    }

    private fun postServiceStateChanged() {
        try {
            EventBus.getDefault().post(object : AccessibilityServiceStateChangedEvent {})
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to post accessibility service state changed event", e)
        }
    }

    companion object {

        private const val TAG = "AccessibilityService"

        private val delegates = TreeMap<Int, AccessibilityDelegate>()
        private val delegatesLock = Any()
        private var containsAllEventTypes = false
        private val eventTypes = HashSet<Int>()

        private val LOCK = ReentrantLock()
        private val ENABLED = LOCK.newCondition()
        private val OPERATIONAL = LOCK.newCondition()

        @Volatile
        private var callback: AccessibilityServiceCallback? = null

        @Volatile
        var hasOperationalState = false

        @Volatile
        var instance: AccessibilityService? = null
            private set

        var bridge: AccessibilityBridge?
            get() = instance?.takeUnless { it.isDisposed }?.bridge
            set(value) {
                instance?.takeUnless { it.isDisposed }?.bridge = value
            }

        val stickOnKeyObserver = OnKeyListener.Observer()

        fun hasInstance() = instance?.isDisposed == false

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
                resetOperationalState()
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
