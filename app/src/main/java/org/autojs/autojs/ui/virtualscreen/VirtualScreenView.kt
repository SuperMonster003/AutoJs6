package org.autojs.autojs.ui.virtualscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper
import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper.Point
import org.autojs.autojs.core.virtualscreen.VirtualScreenSession
import kotlin.math.abs

/**
 * Draws the live frame of a virtual display, letterboxed, and turns touches on the preview into
 * logical taps / swipes / long presses on that display. Coordinate conversion lives in
 * [DisplayCoordinateMapper] so it can be unit tested without Android.
 *
 * zh-CN: 等比绘制虚拟屏实时画面, 并把预览上的触摸换算为该屏的点击/滑动/长按. 坐标换算抽到
 * DisplayCoordinateMapper 以便 JVM 单测.
 *
 * Created for the virtual screen viewer on Nov 8, 2026.
 */
class VirtualScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface GestureListener {
        fun onTap(point: Point)
        fun onSwipe(from: Point, to: Point, durationMs: Int)
        fun onLongPress(point: Point, durationMs: Int)
    }

    var gestureListener: GestureListener? = null

    /** When false the view only displays frames: touches are ignored (used while injecting). */
    var interactionEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) cancelPendingLongPress()
        }

    private var session: VirtualScreenSession? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 2f
    }
    private val backgroundPaint = Paint().apply { color = Color.rgb(24, 26, 30) }
    private val destination = RectF()

    private var geometry: DisplayCoordinateMapper.Geometry? = null
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var startTimeMillis = 0L
    private var tracking = false
    private var longPressFired = false

    private val longPressRunnable = Runnable {
        if (!tracking || longPressFired || !interactionEnabled) return@Runnable
        val g = geometry ?: return@Runnable
        val point = DisplayCoordinateMapper.toDisplay(g, startX, startY)
        longPressFired = true
        gestureListener?.onLongPress(point, LONG_PRESS_MILLIS)
    }

    fun attach(target: VirtualScreenSession?) {
        session = target
        geometry = null
        cancelPendingLongPress()
        invalidate()
    }

    fun renderFrame() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)
        val bitmap = session?.previewBitmap() ?: run {
            geometry = null
            return
        }
        val g = DisplayCoordinateMapper.fit(width, height, bitmap.width, bitmap.height)
        geometry = g
        destination.set(g.offsetX, g.offsetY, g.offsetX + g.drawnWidth, g.offsetY + g.drawnHeight)
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
        canvas.drawRect(destination, framePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled || session == null) return false
        val g = geometry ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount != 1 || !DisplayCoordinateMapper.isInside(g, event.x, event.y)) {
                    tracking = false
                    return true
                }
                startX = event.x
                startY = event.y
                lastX = event.x
                lastY = event.y
                startTimeMillis = System.currentTimeMillis()
                tracking = true
                longPressFired = false
                postDelayed(longPressRunnable, LONG_PRESS_DELAY_MILLIS)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                lastX = event.x
                lastY = event.y
                if (abs(lastX - startX) > TOUCH_SLOP || abs(lastY - startY) > TOUCH_SLOP) {
                    cancelPendingLongPress()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return true
                cancelPendingLongPress()
                tracking = false
                if (longPressFired) return true
                val from = DisplayCoordinateMapper.toDisplay(g, startX, startY)
                val to = DisplayCoordinateMapper.toDisplay(g, event.x, event.y)
                val moved = abs(event.x - startX) > TOUCH_SLOP || abs(event.y - startY) > TOUCH_SLOP
                val elapsed = (System.currentTimeMillis() - startTimeMillis).toInt()
                if (moved) {
                    gestureListener?.onSwipe(from, to, elapsed.coerceIn(MIN_SWIPE_MILLIS, MAX_SWIPE_MILLIS))
                } else {
                    gestureListener?.onTap(from)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                tracking = false
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        // Gestures are forwarded to the virtual display, but keep the accessibility contract.
        return super.performClick()
    }

    private fun cancelPendingLongPress() {
        removeCallbacks(longPressRunnable)
    }

    private companion object {
        const val TOUCH_SLOP = 12f
        const val LONG_PRESS_DELAY_MILLIS = 500L
        const val LONG_PRESS_MILLIS = 600
        const val MIN_SWIPE_MILLIS = 100
        const val MAX_SWIPE_MILLIS = 1_200
    }

}
