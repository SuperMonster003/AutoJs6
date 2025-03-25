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
import org.autojs.autojs.event.EventDispatcher
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.AccessibilityServiceStateChangedEvent
import org.greenrobot.eventbus.EventBus
import java.util.TreeMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 */
open class AccessibilityService : android.accessibilityservice.AccessibilityService() {

    val onKeyObserver = OnKeyListener.Observer()
    val keyInterrupterObserver = KeyInterceptor.Observer()
    var fastRootInActiveWindow: AccessibilityNodeInfo? = null
    var bridge: AccessibilityBridge? = null

    private val eventBox = TreeMap<Int, AccessibilityEventCallback?>()

    private val gestureEventDispatcher = EventDispatcher<GestureListener>()

    private var mEventExecutor: ExecutorService? = null
    private val eventExecutor: ExecutorService
        get() = mEventExecutor ?: Executors.newSingleThreadExecutor().also { mEventExecutor = it }

    private fun eventNameToType(event: String): Int {
        return try {
            AccessibilityEvent::class.java.getField(
                "TYPE_${event.uppercase(Language.getPrefLanguage().locale)}"
            ).get(null) as Int
        } catch (unused: NoSuchFieldException) {
            throw IllegalArgumentException("Unknown event: $event")
        }
    }

    fun addAccessibilityEventCallback(name: String, callback: AccessibilityEventCallback?) {
        eventBox[eventNameToType(name)] = callback
    }

    fun removeAccessibilityEventCallback(name: String) {
        eventBox.remove(eventNameToType(name))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        instance = this
        val type = event.eventType
        eventBox[type]?.onAccessibilityEvent(AccessibilityEventWrapper(event))
        if (containsAllEventTypes || eventTypes.contains(type)) {
            if (type == TYPE_WINDOW_STATE_CHANGED || type == TYPE_VIEW_FOCUSED) {
                rootInActiveWindow?.also { fastRootInActiveWindow = it }
            }
            for ((_, delegate) in delegates) {
                val types = delegate.eventTypes
                if (types == null || types.contains(type)) {
                    // val start = System.currentTimeMillis()
                    if (delegate.onAccessibilityEvent(this@AccessibilityService, event)) {
                        break
                    }
                    // Log.v(TAG, "millis: " + (System.currentTimeMillis() - start) + " delegate: " + delegate::class.java.name)
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
        private var callback: AccessibilityServiceCallback? = null

        var instance: AccessibilityService? = null
            private set

        var bridge: AccessibilityBridge?
            get() = instance?.bridge
            set(value) {
                instance?.bridge = value
            }

        val stickOnKeyObserver = OnKeyListener.Observer()

        fun hasInstance() = instance != null

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

        fun stop() = try {
            instance?.disableSelf()
            instance = null
            true
        } catch (e: Exception) {
            false
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

        @JvmStatic
        fun clearAccessibilityEventCallback() {
            instance?.eventBox?.clear()
        }

        fun setCallback(listener: AccessibilityServiceCallback?) {
            callback = listener
        }

        interface GestureListener {
            fun onGesture(gestureId: Int)
        }

    }

}
