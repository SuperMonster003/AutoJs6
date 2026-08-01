package org.autojs.autojs.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class ImageEditingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private enum class Mode { NORMAL, CROP, BRUSH, TEXT }

    private enum class CropHandle {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        MOVE,
    }

    private data class MutableStroke(
        val color: Int,
        val width: Float,
        val points: MutableList<ImageEditingEngine.Point>,
    )

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imageRect = RectF()
    private val cropRect = RectF()
    private val cropStartRect = RectF()
    private val strokes = mutableListOf<MutableStroke>()
    private var currentStroke: MutableStroke? = null
    private var bitmap: Bitmap? = null
    private var mode = Mode.NORMAL
    private var imageScale = 1f
    private var cropHandle: CropHandle? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var brushColor = Color.RED
    private var brushWidthOnScreen = dp(8f)
    private var textSpec: ImageEditingEngine.TextSpec? = null

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        clearToolState()
        updateImageRect()
        invalidate()
    }

    fun setPreviewColorMatrix(matrix: ColorMatrix?) {
        bitmapPaint.colorFilter = matrix?.let(::ColorMatrixColorFilter)
        invalidate()
    }

    fun beginCrop() {
        val source = bitmap ?: return
        clearToolState()
        mode = Mode.CROP
        cropRect.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        invalidate()
    }

    fun cropSelection(): Rect? = if (mode == Mode.CROP) {
        Rect(
            cropRect.left.toInt(),
            cropRect.top.toInt(),
            cropRect.right.toInt(),
            cropRect.bottom.toInt(),
        )
    } else {
        null
    }

    fun beginBrush(color: Int, widthOnScreen: Float) {
        clearToolState()
        mode = Mode.BRUSH
        brushColor = color
        brushWidthOnScreen = widthOnScreen
        invalidate()
    }

    fun updateBrush(color: Int = brushColor, widthOnScreen: Float = brushWidthOnScreen) {
        brushColor = color
        brushWidthOnScreen = widthOnScreen
    }

    fun brushStrokes(): List<ImageEditingEngine.BrushStroke> = strokes.map { stroke ->
        ImageEditingEngine.BrushStroke(stroke.color, stroke.width, stroke.points.toList())
    }

    fun beginText(spec: ImageEditingEngine.TextSpec) {
        clearToolState()
        mode = Mode.TEXT
        textSpec = spec
        invalidate()
    }

    fun textSpec(): ImageEditingEngine.TextSpec? = textSpec

    fun clearToolState() {
        mode = Mode.NORMAL
        bitmapPaint.colorFilter = null
        cropHandle = null
        strokes.clear()
        currentStroke = null
        textSpec = null
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateImageRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        if (imageRect.isEmpty) updateImageRect()
        canvas.drawBitmap(source, null, imageRect, bitmapPaint)
        when (mode) {
            Mode.CROP -> drawCropOverlay(canvas)
            Mode.BRUSH -> drawBrushStrokes(canvas)
            Mode.TEXT -> drawTextOverlay(canvas)
            Mode.NORMAL -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || mode == Mode.NORMAL) return false
        return when (mode) {
            Mode.CROP -> handleCropTouch(event)
            Mode.BRUSH -> handleBrushTouch(event)
            Mode.TEXT -> handleTextTouch(event)
            Mode.NORMAL -> false
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateImageRect() {
        val source = bitmap ?: run {
            imageRect.setEmpty()
            return
        }
        if (width <= 0 || height <= 0) return
        imageScale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawnWidth = source.width * imageScale
        val drawnHeight = source.height * imageScale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        imageRect.set(left, top, left + drawnWidth, top + drawnHeight)
    }

    private fun drawCropOverlay(canvas: Canvas) {
        val displayCrop = bitmapRectToView(cropRect)
        overlayPaint.color = CROP_SHADE_COLOR
        overlayPaint.style = Paint.Style.FILL
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, displayCrop.top, overlayPaint)
        canvas.drawRect(imageRect.left, displayCrop.bottom, imageRect.right, imageRect.bottom, overlayPaint)
        canvas.drawRect(imageRect.left, displayCrop.top, displayCrop.left, displayCrop.bottom, overlayPaint)
        canvas.drawRect(displayCrop.right, displayCrop.top, imageRect.right, displayCrop.bottom, overlayPaint)

        overlayPaint.color = Color.WHITE
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeWidth = dp(2f)
        canvas.drawRect(displayCrop, overlayPaint)
        val thirdWidth = displayCrop.width() / 3f
        val thirdHeight = displayCrop.height() / 3f
        overlayPaint.alpha = GRID_ALPHA
        canvas.drawLine(displayCrop.left + thirdWidth, displayCrop.top, displayCrop.left + thirdWidth, displayCrop.bottom, overlayPaint)
        canvas.drawLine(displayCrop.left + thirdWidth * 2f, displayCrop.top, displayCrop.left + thirdWidth * 2f, displayCrop.bottom, overlayPaint)
        canvas.drawLine(displayCrop.left, displayCrop.top + thirdHeight, displayCrop.right, displayCrop.top + thirdHeight, overlayPaint)
        canvas.drawLine(displayCrop.left, displayCrop.top + thirdHeight * 2f, displayCrop.right, displayCrop.top + thirdHeight * 2f, overlayPaint)
        overlayPaint.alpha = 255
        overlayPaint.style = Paint.Style.FILL
        listOf(
            displayCrop.left to displayCrop.top,
            displayCrop.right to displayCrop.top,
            displayCrop.left to displayCrop.bottom,
            displayCrop.right to displayCrop.bottom,
        ).forEach { (x, y) -> canvas.drawCircle(x, y, dp(5f), overlayPaint) }
    }

    private fun drawBrushStrokes(canvas: Canvas) {
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            overlayPaint.color = stroke.color
            overlayPaint.style = Paint.Style.STROKE
            overlayPaint.strokeCap = Paint.Cap.ROUND
            overlayPaint.strokeJoin = Paint.Join.ROUND
            overlayPaint.strokeWidth = stroke.width * imageScale
            if (stroke.points.size == 1) {
                val point = bitmapPointToView(stroke.points.first())
                canvas.drawCircle(point.x, point.y, overlayPaint.strokeWidth / 2f, overlayPaint.apply { style = Paint.Style.FILL })
            } else {
                val first = bitmapPointToView(stroke.points.first())
                val path = Path().apply {
                    moveTo(first.x, first.y)
                    for (index in 1 until stroke.points.size) {
                        val point = stroke.points[index]
                        val mapped = bitmapPointToView(point)
                        lineTo(mapped.x, mapped.y)
                    }
                }
                canvas.drawPath(path, overlayPaint)
            }
        }
    }

    private fun drawTextOverlay(canvas: Canvas) {
        val spec = textSpec ?: return
        overlayPaint.color = spec.color
        overlayPaint.style = Paint.Style.FILL
        overlayPaint.textSize = spec.size * imageScale
        overlayPaint.isAntiAlias = true
        val lines = spec.text.lines().ifEmpty { listOf(spec.text) }
        val metrics = overlayPaint.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent) * TEXT_LINE_SPACING
        val blockHeight = lineHeight * lines.size
        val center = bitmapPointToView(ImageEditingEngine.Point(spec.centerX, spec.centerY))
        val firstBaseline = center.y - blockHeight / 2f - metrics.ascent
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                center.x - overlayPaint.measureText(line) / 2f,
                firstBaseline + index * lineHeight,
                overlayPaint,
            )
        }
    }

    private fun handleCropTouch(event: MotionEvent): Boolean {
        val point = viewToBitmap(event.x, event.y, clamp = true) ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = point.x
                touchStartY = point.y
                cropStartRect.set(cropRect)
                cropHandle = resolveCropHandle(point.x, point.y)
                if (cropHandle == null) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateCrop(point.x - touchStartX, point.y - touchStartY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                cropHandle = null
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    private fun resolveCropHandle(x: Float, y: Float): CropHandle? {
        val tolerance = dp(CROP_HIT_SLOP_DP) / imageScale
        val withinHorizontalSpan = x in (cropRect.left - tolerance)..(cropRect.right + tolerance)
        val withinVerticalSpan = y in (cropRect.top - tolerance)..(cropRect.bottom + tolerance)
        val nearLeft = withinVerticalSpan && abs(x - cropRect.left) <= tolerance
        val nearRight = withinVerticalSpan && abs(x - cropRect.right) <= tolerance
        val nearTop = withinHorizontalSpan && abs(y - cropRect.top) <= tolerance
        val nearBottom = withinHorizontalSpan && abs(y - cropRect.bottom) <= tolerance
        return when {
            nearLeft && nearTop -> CropHandle.TOP_LEFT
            nearRight && nearTop -> CropHandle.TOP_RIGHT
            nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
            nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
            nearLeft -> CropHandle.LEFT
            nearRight -> CropHandle.RIGHT
            nearTop -> CropHandle.TOP
            nearBottom -> CropHandle.BOTTOM
            cropRect.contains(x, y) -> CropHandle.MOVE
            else -> null
        }
    }

    private fun updateCrop(deltaX: Float, deltaY: Float) {
        val source = bitmap ?: return
        val desiredMinSize = max(MIN_CROP_BITMAP_SIZE, dp(MIN_CROP_SCREEN_SIZE_DP) / imageScale)
        val minWidth = min(desiredMinSize, cropStartRect.width())
        val minHeight = min(desiredMinSize, cropStartRect.height())
        when (cropHandle) {
            CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT ->
                cropRect.left = (cropStartRect.left + deltaX).coerceIn(0f, cropStartRect.right - minWidth)
            CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT ->
                cropRect.right = (cropStartRect.right + deltaX).coerceIn(cropStartRect.left + minWidth, source.width.toFloat())
            else -> Unit
        }
        when (cropHandle) {
            CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT ->
                cropRect.top = (cropStartRect.top + deltaY).coerceIn(0f, cropStartRect.bottom - minHeight)
            CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT ->
                cropRect.bottom = (cropStartRect.bottom + deltaY).coerceIn(cropStartRect.top + minHeight, source.height.toFloat())
            else -> Unit
        }
        if (cropHandle == CropHandle.MOVE) {
            val movedLeft = (cropStartRect.left + deltaX).coerceIn(0f, source.width - cropStartRect.width())
            val movedTop = (cropStartRect.top + deltaY).coerceIn(0f, source.height - cropStartRect.height())
            cropRect.set(movedLeft, movedTop, movedLeft + cropStartRect.width(), movedTop + cropStartRect.height())
        }
    }

    private fun handleBrushTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = viewToBitmap(event.x, event.y) ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                val stroke = MutableStroke(
                    brushColor,
                    brushWidthOnScreen / imageScale,
                    mutableListOf(point),
                )
                strokes += stroke
                currentStroke = stroke
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = currentStroke ?: return false
                for (index in 0 until event.historySize) {
                    viewToBitmap(event.getHistoricalX(index), event.getHistoricalY(index))
                        ?.let { appendBrushPoint(stroke, it) }
                }
                viewToBitmap(event.x, event.y)?.let { appendBrushPoint(stroke, it) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentStroke?.let { stroke ->
                    viewToBitmap(event.x, event.y)?.let { appendBrushPoint(stroke, it, force = true) }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                currentStroke = null
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                currentStroke?.let(strokes::remove)
                currentStroke = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun appendBrushPoint(
        stroke: MutableStroke,
        point: ImageEditingEngine.Point,
        force: Boolean = false,
    ) {
        val last = stroke.points.lastOrNull()
        if (last == null) {
            stroke.points += point
            return
        }
        val deltaX = point.x - last.x
        val deltaY = point.y - last.y
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (distanceSquared == 0f) return
        val minimumDistance = dp(MIN_BRUSH_POINT_DISTANCE_DP) / imageScale
        if (force || distanceSquared >= minimumDistance * minimumDistance) stroke.points += point
    }

    private fun handleTextTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val point = viewToBitmap(event.x, event.y, clamp = true) ?: return false
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                textSpec = textSpec?.copy(centerX = point.x, centerY = point.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    private fun viewToBitmap(x: Float, y: Float, clamp: Boolean = false): ImageEditingEngine.Point? {
        if (!clamp && !imageRect.contains(x, y)) return null
        if (imageRect.isEmpty) return null
        return ImageEditingEngine.Point(
            ((x.coerceIn(imageRect.left, imageRect.right) - imageRect.left) / imageScale),
            ((y.coerceIn(imageRect.top, imageRect.bottom) - imageRect.top) / imageScale),
        )
    }

    private fun bitmapPointToView(point: ImageEditingEngine.Point) = ImageEditingEngine.Point(
        imageRect.left + point.x * imageScale,
        imageRect.top + point.y * imageScale,
    )

    private fun bitmapRectToView(rect: RectF) = RectF(
        imageRect.left + rect.left * imageScale,
        imageRect.top + rect.top * imageScale,
        imageRect.left + rect.right * imageScale,
        imageRect.top + rect.bottom * imageScale,
    )

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val CROP_HIT_SLOP_DP = 24f
        const val MIN_CROP_SCREEN_SIZE_DP = 48f
        const val MIN_CROP_BITMAP_SIZE = 8f
        const val MIN_BRUSH_POINT_DISTANCE_DP = 1.5f
        const val CROP_SHADE_COLOR = 0x99000000.toInt()
        const val GRID_ALPHA = 140
        const val TEXT_LINE_SPACING = 1.12f
    }
}
