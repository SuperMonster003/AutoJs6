package org.autojs.autojs.core.automator

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Gestures of an automator built without a handler (the path of `UiObject.clickBounds()` for nodes
 * that did not come from a script's query, such as accessibility event sources) must leave the
 * calling thread's Looper alone: a script thread keeps running its timers and callbacks afterwards,
 * and a call on the main thread returns the result instead of failing to quit the main Looper.
 * zh-CN: 未指定 handler 的 automator 的手势 (即并非来自脚本查询的节点, 如无障碍事件 source, 调用
 * `UiObject.clickBounds()` 时的路径) 不得触碰调用线程的 Looper: 脚本线程之后仍执行其定时器与回调,
 * 在主线程调用时返回结果, 而不是因无法退出主 Looper 而失败.
 */
class GlobalActionAutomatorDeviceTest {

    /** Never connected; the dispatcher below stands in for the system. zh-CN: 从不连接; 由下面的派发器代替系统. */
    private val service = object : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

        override fun onInterrupt() = Unit
    }

    /** Completes every gesture through the handler it is given, 20 ms later. zh-CN: 20 ms 后经所给 handler 完成每个手势. */
    private val callbackLoopers = CopyOnWriteArrayList<Looper>()

    private val completing = GlobalActionAutomator.GestureDispatcher { _, gesture, callback, handler ->
        callbackLoopers += handler.looper
        handler.postDelayed({ callback.onCompleted(gesture) }, 20)
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aScriptThreadKeepsItsLooperAfterAGesture() {
        val automator = GlobalActionAutomator(context, null, completing) { service }
        val scriptThread = HandlerThread("script-like").apply { start() }
        try {
            val handler = Handler(scriptThread.looper)
            val results = CopyOnWriteArrayList<Result<Boolean>>()
            val done = CountDownLatch(2)
            repeat(2) {
                handler.post {
                    results += runCatching { automator.click(10, 10) }
                    done.countDown()
                }
            }
            assertTrue("both taps answered", done.await(10, TimeUnit.SECONDS))
            assertEquals(listOf(true, true), results.map { it.getOrThrow() })

            val later = CountDownLatch(1)
            assertTrue("the script thread's Looper still accepts messages", handler.post { later.countDown() })
            assertTrue("the script thread's Looper still runs messages", later.await(5, TimeUnit.SECONDS))
            callbackLoopers.forEach { assertNotSame("results must not arrive on the calling thread", scriptThread.looper, it) }
        } finally {
            scriptThread.quitSafely()
        }
    }

    @Test
    fun aTapOnTheMainThreadReturnsItsResult() {
        val automator = GlobalActionAutomator(context, null, completing) { service }
        val result = AtomicReference<Result<Boolean>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching { automator.click(10, 10) })
        }
        assertEquals(true, result.get().getOrThrow())
        callbackLoopers.forEach { assertNotSame(Looper.getMainLooper(), it) }
    }
}
