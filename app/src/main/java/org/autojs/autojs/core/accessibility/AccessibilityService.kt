package org.autojs.autojs.core.accessibility

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.event.EventDispatcher
import java.util.TreeMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on 2017/5/2.
 */
open class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    val onKeyObserver = OnKeyListener.Observer()
    val keyInterrupterObserver = KeyInterceptor.Observer()
    var fastRootInActiveWindow: AccessibilityNodeInfo? = null

    var bridge: AccessibilityBridge? = null

    private val gestureEventDispatcher = EventDispatcher<GestureListener>()

    private val eventExecutor by lazy { Executors.newSingleThreadExecutor() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (instance != this) {
            instance = this
        }
        val type = event.eventType
        if (containsAllEventTypes || eventTypes.contains(type)) {
            if (type == TYPE_WINDOW_STATE_CHANGED || type == TYPE_VIEW_FOCUSED) {
                rootInActiveWindow?.also { fastRootInActiveWindow = it }
            }
            for ((_, delegate) in delegates) {
                val types = delegate.eventTypes
                if (types == null || types.contains(type)) {
                    // long start = System.currentTimeMillis();
                    if (delegate.onAccessibilityEvent(this@AccessibilityService, event)) {
                        break
                    }
                    // Log.v(TAG, "millis: " + (System.currentTimeMillis() - start) + " delegate: " + entry.getValue().getClass().getName());
                }
            }
        }
    }

    override fun onInterrupt() {}

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
        return try {
            super.getRootInActiveWindow()
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy: $instance")
        instance = null
        bridge = null
        eventExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onServiceConnected() {
        instance = this

        super.onServiceConnected()

        LOCK.lock()
        ENABLED.signalAll()
        LOCK.unlock()

        // FIXME: 2017/2/12 有时在无障碍中开启服务后这里不会调用服务也不会运行，安卓的BUG???
    }

    companion object {

        private const val TAG = "AccessibilityService"

        private val delegates = TreeMap<Int, AccessibilityDelegate>()
        private var containsAllEventTypes = false
        private val eventTypes = HashSet<Int>()

        private val LOCK = ReentrantLock()
        private val ENABLED = LOCK.newCondition()

        var instance: AccessibilityService? = null
            private set

        var bridge: AccessibilityBridge?
            get() = instance?.bridge
            set(value) {
                instance?.bridge = value
            }

        val stickOnKeyObserver = OnKeyListener.Observer()

        @JvmStatic
        fun isRunning() = instance != null

        @JvmStatic
        fun isNotRunning() = !isRunning()

        fun addDelegate(uniquePriority: Int, delegate: AccessibilityDelegate) {
            delegates[uniquePriority] = delegate
            val set = delegate.eventTypes
            if (set == null) {
                containsAllEventTypes = true
            } else {
                eventTypes.addAll(set)
            }
        }

        @JvmStatic
        fun disable() = try {
            true.also { instance?.disableSelf() }
        } catch (e: Exception) {
            false
        }

        fun waitForEnabled(timeout: Long): Boolean {
            if (isRunning()) {
                return true
            }
            LOCK.lock()
            try {
                if (isRunning()) {
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

        interface GestureListener {

            fun onGesture(gestureId: Int)

        }

    }

}
