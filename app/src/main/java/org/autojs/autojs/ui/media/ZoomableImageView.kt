package org.autojs.autojs.ui.media

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * An image surface with focal-point pinch zoom, panning and page swipes at 1x.
 *
 * The displayed image is always transformed from its real fit-center bounds. This keeps both
 * the scale amount and the pinch focus in lockstep with the fingers, including while shrinking
 * back to the original size.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var onPageSwipe: ((direction: Int) -> Unit)? = null

    private val baseMatrix = Matrix()
    private val displayMatrix = Matrix()
    private val drawableRect = RectF()
    private val mappedDrawableRect = RectF()

    private var zoom = MIN_ZOOM
    private var configuredDrawable: Drawable? = null
    private var configuredWidth = 0
    private var configuredHeight = 0

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f
    private var gestureHadMultiplePointers = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                ensureBaseMatrix()
                if (drawable == null) return false
                gestureHadMultiplePointers = true
                lastScaleFocusX = detector.focusX
                lastScaleFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val reportedFactor = detector.scaleFactor
                if (!reportedFactor.isFinite() || reportedFactor <= 0f) return false

                val targetZoom = (zoom * reportedFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                val appliedFactor = targetZoom / zoom

                // A pinch can translate as well as scale. Track the centroid explicitly so the
                // image follows two fingers even when they move together while changing span.
                displayMatrix.postTranslate(
                    detector.focusX - lastScaleFocusX,
                    detector.focusY - lastScaleFocusY,
                )
                displayMatrix.postScale(
                    appliedFactor,
                    appliedFactor,
                    detector.focusX,
                    detector.focusY,
                )
                zoom = targetZoom
                lastScaleFocusX = detector.focusX
                lastScaleFocusY = detector.focusY

                if (zoom <= MIN_ZOOM + ZOOM_EPSILON) {
                    zoom = MIN_ZOOM
                    displayMatrix.set(baseMatrix)
                    imageMatrix = displayMatrix
                } else {
                    constrainDisplayMatrix()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (zoom <= MIN_ZOOM + ZOOM_EPSILON) resetZoom()
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
                if (
                    zoom > MIN_ZOOM + ZOOM_EPSILON ||
                    gestureHadMultiplePointers ||
                    down == null
                ) {
                    return false
                }
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
        scaleType = ScaleType.MATRIX
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        ensureBaseMatrix()
        super.onDraw(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        configuredWidth = 0
        configuredHeight = 0
        ensureBaseMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureHadMultiplePointers = false
            activePointerId = event.getPointerId(0)
            lastTouchX = event.x
            lastTouchY = event.y
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (
            event.pointerCount > 1 ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_UP
        ) {
            gestureHadMultiplePointers = true
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && zoom > MIN_ZOOM) {
                    val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                    val touchX = event.getX(pointerIndex)
                    val touchY = event.getY(pointerIndex)
                    displayMatrix.postTranslate(touchX - lastTouchX, touchY - lastTouchY)
                    constrainDisplayMatrix()
                    lastTouchX = touchX
                    lastTouchY = touchY
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedPointerId = event.getPointerId(event.actionIndex)
                if (liftedPointerId == activePointerId) {
                    val replacementIndex = if (event.actionIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(replacementIndex)
                    lastTouchX = event.getX(replacementIndex)
                    lastTouchY = event.getY(replacementIndex)
                } else {
                    val activeIndex = event.findPointerIndex(activePointerId)
                    if (activeIndex >= 0) {
                        lastTouchX = event.getX(activeIndex)
                        lastTouchY = event.getY(activeIndex)
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resetZoom() {
        ensureBaseMatrix()
        zoom = MIN_ZOOM
        displayMatrix.set(baseMatrix)
        imageMatrix = displayMatrix
    }

    private fun ensureBaseMatrix() {
        val currentDrawable = drawable ?: return
        if (
            currentDrawable === configuredDrawable &&
            width == configuredWidth &&
            height == configuredHeight
        ) {
            return
        }
        val drawableWidth = currentDrawable.intrinsicWidth
        val drawableHeight = currentDrawable.intrinsicHeight
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (drawableWidth <= 0 || drawableHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) return

        val fitScale = min(
            contentWidth.toFloat() / drawableWidth,
            contentHeight.toFloat() / drawableHeight,
        )
        val fittedWidth = drawableWidth * fitScale
        val fittedHeight = drawableHeight * fitScale
        val offsetX = paddingLeft + (contentWidth - fittedWidth) / 2f
        val offsetY = paddingTop + (contentHeight - fittedHeight) / 2f

        drawableRect.set(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat())
        baseMatrix.reset()
        baseMatrix.setScale(fitScale, fitScale)
        baseMatrix.postTranslate(offsetX, offsetY)
        displayMatrix.set(baseMatrix)
        imageMatrix = displayMatrix
        zoom = MIN_ZOOM
        configuredDrawable = currentDrawable
        configuredWidth = width
        configuredHeight = height
    }

    private fun constrainDisplayMatrix() {
        val currentDrawable = drawable ?: return
        if (currentDrawable !== configuredDrawable) {
            ensureBaseMatrix()
            return
        }
        mappedDrawableRect.set(drawableRect)
        displayMatrix.mapRect(mappedDrawableRect)

        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentRight = (width - paddingRight).toFloat()
        val contentBottom = (height - paddingBottom).toFloat()
        val contentWidth = contentRight - contentLeft
        val contentHeight = contentBottom - contentTop

        val correctionX = when {
            mappedDrawableRect.width() <= contentWidth ->
                contentLeft + (contentWidth - mappedDrawableRect.width()) / 2f - mappedDrawableRect.left
            mappedDrawableRect.left > contentLeft -> contentLeft - mappedDrawableRect.left
            mappedDrawableRect.right < contentRight -> contentRight - mappedDrawableRect.right
            else -> 0f
        }
        val correctionY = when {
            mappedDrawableRect.height() <= contentHeight ->
                contentTop + (contentHeight - mappedDrawableRect.height()) / 2f - mappedDrawableRect.top
            mappedDrawableRect.top > contentTop -> contentTop - mappedDrawableRect.top
            mappedDrawableRect.bottom < contentBottom -> contentBottom - mappedDrawableRect.bottom
            else -> 0f
        }
        displayMatrix.postTranslate(correctionX, correctionY)
        imageMatrix = displayMatrix
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
        private const val ZOOM_EPSILON = 0.001f
        private const val SWIPE_DISTANCE_DP = 72f
        private const val SWIPE_VELOCITY_DP = 160f
    }
}
