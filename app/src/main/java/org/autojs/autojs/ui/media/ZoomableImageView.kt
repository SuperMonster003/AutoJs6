package org.autojs.autojs.ui.media

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * A small image surface that supports pinch zoom, panning and page swipes.
 *
 * Page swipes are deliberately disabled while zoomed so a pan cannot
 * accidentally replace the image being inspected.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var onPageSwipe: ((direction: Int) -> Unit)? = null

    private var zoom = MIN_ZOOM
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                pivotX = width / 2f
                pivotY = height / 2f
                scaleX = zoom
                scaleY = zoom
                constrainTranslation()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                resetZoom()
                return true
            }

            override fun onFling(
                down: MotionEvent?,
                up: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (zoom > MIN_ZOOM + ZOOM_EPSILON || down == null) return false
                val distanceX = up.x - down.x
                if (
                    abs(distanceX) < swipeDistanceThreshold ||
                    abs(velocityX) < swipeVelocityThreshold ||
                    abs(distanceX) <= abs(up.y - down.y)
                ) {
                    return false
                }
                onPageSwipe?.invoke(if (distanceX < 0f) DIRECTION_NEXT else DIRECTION_PREVIOUS)
                return true
            }
        },
    )

    init {
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && zoom > MIN_ZOOM) {
                    translationX += event.x - lastTouchX
                    translationY += event.y - lastTouchY
                    constrainTranslation()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resetZoom() {
        zoom = MIN_ZOOM
        scaleX = MIN_ZOOM
        scaleY = MIN_ZOOM
        translationX = 0f
        translationY = 0f
    }

    private fun constrainTranslation() {
        if (zoom <= MIN_ZOOM) {
            translationX = 0f
            translationY = 0f
            return
        }
        val maxX = width * (zoom - MIN_ZOOM) / 2f
        val maxY = height * (zoom - MIN_ZOOM) / 2f
        translationX = translationX.coerceIn(-maxX, maxX)
        translationY = translationY.coerceIn(-maxY, maxY)
    }

    private val swipeDistanceThreshold: Float
        get() = SWIPE_DISTANCE_DP * resources.displayMetrics.density

    private val swipeVelocityThreshold: Float
        get() = SWIPE_VELOCITY_DP * resources.displayMetrics.density

    companion object {
        const val DIRECTION_PREVIOUS = -1
        const val DIRECTION_NEXT = 1

        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 5f
        private const val ZOOM_EPSILON = 0.01f
        private const val SWIPE_DISTANCE_DP = 72f
        private const val SWIPE_VELOCITY_DP = 160f
    }
}
