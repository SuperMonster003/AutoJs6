package org.autojs.autojs.ui.pager

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollControllableViewPagerInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun ignoresMoveEventWhenActivePointerIsMissing() {
        instrumentation.runOnMainSync {
            ScrollControllableViewPager.setPageScrollEnabled(true)
            val pager = ScrollControllableViewPager(instrumentation.targetContext)
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                100f,
                100f,
                0,
            )
            val moveEvent = obtainMoveEventWithPointerId(
                downTime = downTime,
                eventTime = downTime + 16,
                pointerId = 1,
            )

            try {
                val activePointerId = downEvent.getPointerId(0)
                assertEquals(-1, moveEvent.findPointerIndex(activePointerId))

                assertFalse(pager.onInterceptTouchEvent(downEvent))

                assertFalse(pager.onInterceptTouchEvent(moveEvent))
            } finally {
                downEvent.recycle()
                moveEvent.recycle()
                ScrollControllableViewPager.setPageScrollEnabled(true)
            }
        }
    }

    @Test
    fun cancelsDragWhenActivePointerIsMissing() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.context, ScrollControllableViewPagerTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ScrollControllableViewPagerTestActivity
        lateinit var pager: ScrollControllableViewPager

        try {
            instrumentation.runOnMainSync {
                ScrollControllableViewPager.setPageScrollEnabled(true)
                pager = ScrollControllableViewPager(activity).apply {
                    adapter = SinglePageAdapter()
                }
                activity.setContentView(pager)
            }
            instrumentation.waitForIdleSync()

            instrumentation.runOnMainSync {
                assertTrue(pager.isAttachedToWindow)
                assertTrue(pager.childCount > 0)
                var scrollState = ViewPager.SCROLL_STATE_IDLE
                pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageScrollStateChanged(state: Int) {
                        scrollState = state
                    }
                })
                val downTime = SystemClock.uptimeMillis()
                val downEvent = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    300f,
                    100f,
                    0,
                )
                val dragEvent = obtainMoveEventWithPointerId(
                    downTime = downTime,
                    eventTime = downTime + 16,
                    pointerId = 0,
                    x = 100f,
                )
                val inconsistentMoveEvent = obtainMoveEventWithPointerId(
                    downTime = downTime,
                    eventTime = downTime + 32,
                    pointerId = 1,
                    x = 80f,
                )
                val residualMoveEvent = obtainMoveEventWithPointerId(
                    downTime = downTime,
                    eventTime = downTime + 48,
                    pointerId = 1,
                    x = 80f,
                )
                val residualUpEvent = obtainPointerEvent(
                    downTime = downTime,
                    eventTime = downTime + 64,
                    action = MotionEvent.ACTION_UP,
                    pointerId = 1,
                    x = 80f,
                )
                val nextDownTime = downTime + 80
                val nextDownEvent = MotionEvent.obtain(
                    nextDownTime,
                    nextDownTime,
                    MotionEvent.ACTION_DOWN,
                    300f,
                    100f,
                    0,
                )
                val nextDragEvent = obtainMoveEventWithPointerId(
                    downTime = nextDownTime,
                    eventTime = nextDownTime + 16,
                    pointerId = 0,
                    x = 100f,
                )
                val nextCancelEvent = obtainPointerEvent(
                    downTime = nextDownTime,
                    eventTime = nextDownTime + 32,
                    action = MotionEvent.ACTION_CANCEL,
                    pointerId = 0,
                    x = 100f,
                )

                try {
                    assertTrue(pager.onTouchEvent(downEvent))
                    assertTrue(pager.onTouchEvent(dragEvent))
                    assertEquals(ViewPager.SCROLL_STATE_DRAGGING, scrollState)
                    assertEquals(-1, inconsistentMoveEvent.findPointerIndex(0))

                    assertTrue(pager.onTouchEvent(inconsistentMoveEvent))
                    assertTrue(pager.onTouchEvent(residualMoveEvent))
                    assertTrue(pager.onTouchEvent(residualUpEvent))
                    assertEquals(ViewPager.SCROLL_STATE_IDLE, scrollState)

                    assertTrue(pager.onTouchEvent(nextDownEvent))
                    assertTrue(pager.onTouchEvent(nextDragEvent))
                    assertEquals(ViewPager.SCROLL_STATE_DRAGGING, scrollState)
                    assertTrue(pager.onTouchEvent(nextCancelEvent))
                    assertEquals(ViewPager.SCROLL_STATE_IDLE, scrollState)
                } finally {
                    downEvent.recycle()
                    dragEvent.recycle()
                    inconsistentMoveEvent.recycle()
                    residualMoveEvent.recycle()
                    residualUpEvent.recycle()
                    nextDownEvent.recycle()
                    nextDragEvent.recycle()
                    nextCancelEvent.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                ScrollControllableViewPager.setPageScrollEnabled(true)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private fun obtainMoveEventWithPointerId(
        downTime: Long,
        eventTime: Long,
        pointerId: Int,
        x: Float = 120f,
    ) = obtainPointerEvent(
        downTime = downTime,
        eventTime = eventTime,
        action = MotionEvent.ACTION_MOVE,
        pointerId = pointerId,
        x = x,
    )

    private fun obtainPointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        pointerId: Int,
        x: Float,
    ): MotionEvent {
        val pointerProperties = MotionEvent.PointerProperties().apply {
            id = pointerId
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val pointerCoords = MotionEvent.PointerCoords().apply {
            this.x = x
            y = 100f
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private class SinglePageAdapter : PagerAdapter() {

        override fun getCount() = 1

        override fun isViewFromObject(view: View, objectValue: Any) = view === objectValue

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            return View(container.context).also { page -> container.addView(page) }
        }

        override fun destroyItem(container: ViewGroup, position: Int, objectValue: Any) {
            container.removeView(objectValue as View)
        }
    }

}
