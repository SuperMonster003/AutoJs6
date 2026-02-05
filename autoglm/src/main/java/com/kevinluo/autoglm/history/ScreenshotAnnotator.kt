package com.kevinluo.autoglm.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Utility class for annotating screenshots with action visualizations.
 *
 * Provides methods to draw various types of action indicators on screenshots:
 * - Tap circles with crosshairs
 * - Swipe arrows with direction indicators
 * - Long press circles with duration labels
 * - Double tap indicators
 * - Text input badges
 * - Batch action sequences with numbered steps
 *
 * All coordinates are expected in relative units (0-1000) and are converted
 * to actual pixel positions based on screen dimensions.
 *
 */
object ScreenshotAnnotator {
    private const val CIRCLE_RADIUS_DP = 30f
    private const val STROKE_WIDTH_DP = 4f
    private const val ARROW_HEAD_LENGTH_DP = 20f
    private const val ARROW_HEAD_ANGLE = 30.0 // degrees

    // Annotation colors
    private const val TAP_COLOR = Color.RED
    private const val SWIPE_COLOR = Color.BLUE
    private const val LONG_PRESS_COLOR = Color.MAGENTA
    private const val DOUBLE_TAP_COLOR = Color.GREEN

    /**
     * Annotates a screenshot with the given action annotation.
     *
     * Creates a copy of the input bitmap and draws the appropriate annotation on it.
     * The original bitmap is not modified.
     *
     * @param bitmap The original screenshot bitmap
     * @param annotation The action annotation to draw
     * @param density Screen density for dp to px conversion (default 2.5f)
     * @return A new bitmap with the annotation drawn on it
     *
     */
    fun annotate(bitmap: Bitmap, annotation: ActionAnnotation, density: Float = 2.5f): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        when (annotation) {
            is ActionAnnotation.TapCircle -> {
                drawTapCircle(canvas, annotation, density)
            }

            is ActionAnnotation.SwipeArrow -> {
                drawSwipeArrow(canvas, annotation, density)
            }

            is ActionAnnotation.LongPressCircle -> {
                drawLongPressCircle(canvas, annotation, density)
            }

            is ActionAnnotation.DoubleTapCircle -> {
                drawDoubleTapCircle(canvas, annotation, density)
            }

            is ActionAnnotation.TypeText -> {
                drawTypeIndicator(canvas, annotation, density)
            }

            is ActionAnnotation.BatchSteps -> {
                drawBatchSteps(canvas, annotation, density)
            }

            is ActionAnnotation.None -> { /* No annotation */ }
        }

        return result
    }

    /**
     * Draws a tap circle annotation.
     *
     * Renders a circle with crosshairs at the tap location.
     *
     * @param canvas Canvas to draw on
     * @param annotation Tap circle annotation data
     * @param density Screen density for sizing
     */
    private fun drawTapCircle(canvas: Canvas, annotation: ActionAnnotation.TapCircle, density: Float) {
        val paint = createStrokePaint(TAP_COLOR, density)
        val fillPaint = createFillPaint(TAP_COLOR)

        // Convert relative coordinates (0-1000) to actual pixels
        val x = (annotation.x / 1000f) * annotation.screenWidth
        val y = (annotation.y / 1000f) * annotation.screenHeight
        val radius = CIRCLE_RADIUS_DP * density

        // Draw outer circle
        canvas.drawCircle(x, y, radius, paint)

        // Draw inner filled circle
        canvas.drawCircle(x, y, radius * 0.3f, fillPaint)

        // Draw crosshair
        val crossSize = radius * 0.5f
        canvas.drawLine(x - crossSize, y, x + crossSize, y, paint)
        canvas.drawLine(x, y - crossSize, x, y + crossSize, paint)
    }

    /**
     * Draws a swipe arrow annotation.
     *
     * Renders a line with an arrow head from start to end position.
     *
     * @param canvas Canvas to draw on
     * @param annotation Swipe arrow annotation data
     * @param density Screen density for sizing
     */
    private fun drawSwipeArrow(canvas: Canvas, annotation: ActionAnnotation.SwipeArrow, density: Float) {
        val paint = createStrokePaint(SWIPE_COLOR, density)
        val fillPaint = createFillPaint(SWIPE_COLOR)

        // Convert relative coordinates to actual pixels
        val startX = (annotation.startX / 1000f) * annotation.screenWidth
        val startY = (annotation.startY / 1000f) * annotation.screenHeight
        val endX = (annotation.endX / 1000f) * annotation.screenWidth
        val endY = (annotation.endY / 1000f) * annotation.screenHeight

        // Draw start point circle
        val startRadius = CIRCLE_RADIUS_DP * density * 0.5f
        canvas.drawCircle(startX, startY, startRadius, fillPaint)

        // Draw line
        canvas.drawLine(startX, startY, endX, endY, paint)

        // Draw arrow head
        drawArrowHead(canvas, startX, startY, endX, endY, paint, density)
    }

    /**
     * Draws an arrow head at the end of a line.
     *
     * @param canvas Canvas to draw on
     * @param startX Line start X coordinate
     * @param startY Line start Y coordinate
     * @param endX Line end X coordinate (arrow tip)
     * @param endY Line end Y coordinate (arrow tip)
     * @param paint Paint to use for drawing
     * @param density Screen density for sizing
     */
    private fun drawArrowHead(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        paint: Paint,
        density: Float,
    ) {
        val arrowLength = ARROW_HEAD_LENGTH_DP * density
        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val angleRad = Math.toRadians(ARROW_HEAD_ANGLE)

        val path = Path()
        path.moveTo(endX, endY)

        // Left side of arrow head
        val leftX = endX - arrowLength * cos(angle - angleRad).toFloat()
        val leftY = endY - arrowLength * sin(angle - angleRad).toFloat()
        path.lineTo(leftX, leftY)

        // Right side of arrow head
        val rightX = endX - arrowLength * cos(angle + angleRad).toFloat()
        val rightY = endY - arrowLength * sin(angle + angleRad).toFloat()
        path.lineTo(rightX, rightY)

        path.close()

        val fillPaint =
            Paint(paint).apply {
                style = Paint.Style.FILL
            }
        canvas.drawPath(path, fillPaint)
    }

    /**
     * Draws a long press circle annotation with duration indicator.
     *
     * Renders concentric circles with a duration label below.
     *
     * @param canvas Canvas to draw on
     * @param annotation Long press annotation data
     * @param density Screen density for sizing
     */
    private fun drawLongPressCircle(canvas: Canvas, annotation: ActionAnnotation.LongPressCircle, density: Float) {
        val paint = createStrokePaint(LONG_PRESS_COLOR, density)
        val fillPaint = createFillPaint(LONG_PRESS_COLOR)

        val x = (annotation.x / 1000f) * annotation.screenWidth
        val y = (annotation.y / 1000f) * annotation.screenHeight
        val radius = CIRCLE_RADIUS_DP * density

        // Draw outer circle
        canvas.drawCircle(x, y, radius, paint)

        // Draw inner circle
        canvas.drawCircle(x, y, radius * 0.6f, paint)

        // Draw center dot
        canvas.drawCircle(x, y, radius * 0.2f, fillPaint)

        // Draw duration text
        val textPaint =
            Paint().apply {
                color = LONG_PRESS_COLOR
                textSize = 12f * density
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
        val durationText = "${annotation.durationMs / 1000f}s"
        canvas.drawText(durationText, x, y + radius + textPaint.textSize, textPaint)
    }

    /**
     * Draws a double tap annotation (two concentric circles).
     *
     * Renders concentric circles with an "x2" label below.
     *
     * @param canvas Canvas to draw on
     * @param annotation Double tap annotation data
     * @param density Screen density for sizing
     */
    private fun drawDoubleTapCircle(canvas: Canvas, annotation: ActionAnnotation.DoubleTapCircle, density: Float) {
        val paint = createStrokePaint(DOUBLE_TAP_COLOR, density)
        val fillPaint = createFillPaint(DOUBLE_TAP_COLOR)

        val x = (annotation.x / 1000f) * annotation.screenWidth
        val y = (annotation.y / 1000f) * annotation.screenHeight
        val radius = CIRCLE_RADIUS_DP * density

        // Draw two concentric circles to indicate double tap
        canvas.drawCircle(x, y, radius, paint)
        canvas.drawCircle(x, y, radius * 0.6f, paint)

        // Draw center dot
        canvas.drawCircle(x, y, radius * 0.15f, fillPaint)

        // Draw "x2" indicator
        val textPaint =
            Paint().apply {
                color = DOUBLE_TAP_COLOR
                textSize = 14f * density
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
        canvas.drawText("x2", x, y + radius + textPaint.textSize, textPaint)
    }

    /**
     * Draws a type indicator (keyboard icon or text badge).
     *
     * Renders a badge at the bottom of the screen showing the typed text.
     *
     * @param canvas Canvas to draw on
     * @param annotation Type text annotation data
     * @param density Screen density for sizing
     */
    private fun drawTypeIndicator(canvas: Canvas, annotation: ActionAnnotation.TypeText, density: Float) {
        val paint =
            Paint().apply {
                color = Color.parseColor("#FF9800") // Orange
                textSize = 14f * density
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }

        val bgPaint =
            Paint().apply {
                color = Color.parseColor("#80000000") // Semi-transparent black
                style = Paint.Style.FILL
            }

        val text = "⌨ ${annotation.text.take(20)}${if (annotation.text.length > 20) "..." else ""}"
        val padding = 8f * density
        val textWidth = paint.measureText(text)

        // Draw background
        canvas.drawRoundRect(
            padding,
            canvas.height - padding - paint.textSize - padding * 2,
            padding * 2 + textWidth + padding,
            canvas.height - padding,
            8f * density,
            8f * density,
            bgPaint,
        )

        // Draw text
        canvas.drawText(
            text,
            padding * 2,
            canvas.height - padding * 2,
            paint,
        )
    }

    /**
     * Draws batch steps with numbered annotations.
     *
     * Each step is drawn with a number indicating the execution order.
     * Steps are connected with dashed lines to show the sequence.
     *
     * @param canvas Canvas to draw on
     * @param annotation Batch steps annotation data
     * @param density Screen density for sizing
     */
    private fun drawBatchSteps(canvas: Canvas, annotation: ActionAnnotation.BatchSteps, density: Float) {
        val numberPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = 16f * density
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }

        val numberBgPaint =
            Paint().apply {
                color = Color.parseColor("#E91E63") // Pink for batch steps
                style = Paint.Style.FILL
                isAntiAlias = true
            }

        val numberRadius = 14f * density

        for ((index, step) in annotation.steps.withIndex()) {
            val stepNumber = index + 1

            // Get the position for this step's number badge
            val (badgeX, badgeY) =
                when (step) {
                    is ActionAnnotation.TapCircle -> {
                        val x = (step.x / 1000f) * step.screenWidth
                        val y = (step.y / 1000f) * step.screenHeight
                        // Draw the tap circle first
                        drawTapCircleWithColor(canvas, step, density, BATCH_STEP_COLORS[index % BATCH_STEP_COLORS.size])
                        // Position number badge at top-right of the circle
                        Pair(x + CIRCLE_RADIUS_DP * density * 0.7f, y - CIRCLE_RADIUS_DP * density * 0.7f)
                    }

                    is ActionAnnotation.SwipeArrow -> {
                        val startX = (step.startX / 1000f) * step.screenWidth
                        val startY = (step.startY / 1000f) * step.screenHeight
                        // Draw the swipe arrow
                        drawSwipeArrowWithColor(
                            canvas,
                            step,
                            density,
                            BATCH_STEP_COLORS[index % BATCH_STEP_COLORS.size],
                        )
                        // Position number badge at start point
                        Pair(startX + CIRCLE_RADIUS_DP * density * 0.5f, startY - CIRCLE_RADIUS_DP * density * 0.5f)
                    }

                    is ActionAnnotation.LongPressCircle -> {
                        val x = (step.x / 1000f) * step.screenWidth
                        val y = (step.y / 1000f) * step.screenHeight
                        drawLongPressCircle(canvas, step, density)
                        Pair(x + CIRCLE_RADIUS_DP * density * 0.7f, y - CIRCLE_RADIUS_DP * density * 0.7f)
                    }

                    is ActionAnnotation.DoubleTapCircle -> {
                        val x = (step.x / 1000f) * step.screenWidth
                        val y = (step.y / 1000f) * step.screenHeight
                        drawDoubleTapCircle(canvas, step, density)
                        Pair(x + CIRCLE_RADIUS_DP * density * 0.7f, y - CIRCLE_RADIUS_DP * density * 0.7f)
                    }

                    else -> {
                        continue
                    }
                }

            // Draw number badge background
            canvas.drawCircle(badgeX, badgeY, numberRadius, numberBgPaint)

            // Draw number
            val textY = badgeY + numberPaint.textSize / 3
            canvas.drawText(stepNumber.toString(), badgeX, textY, numberPaint)
        }

        // Draw connecting lines between consecutive tap points
        drawConnectionLines(canvas, annotation.steps, density)
    }

    /**
     * Draws a tap circle with a specific color.
     *
     * @param canvas Canvas to draw on
     * @param annotation Tap circle annotation data
     * @param density Screen density for sizing
     * @param color Color to use for the annotation
     */
    private fun drawTapCircleWithColor(
        canvas: Canvas,
        annotation: ActionAnnotation.TapCircle,
        density: Float,
        color: Int,
    ) {
        val paint = createStrokePaint(color, density)
        val fillPaint = createFillPaint(color)

        val x = (annotation.x / 1000f) * annotation.screenWidth
        val y = (annotation.y / 1000f) * annotation.screenHeight
        val radius = CIRCLE_RADIUS_DP * density

        // Draw outer circle
        canvas.drawCircle(x, y, radius, paint)

        // Draw inner filled circle
        canvas.drawCircle(x, y, radius * 0.3f, fillPaint)

        // Draw crosshair
        val crossSize = radius * 0.5f
        canvas.drawLine(x - crossSize, y, x + crossSize, y, paint)
        canvas.drawLine(x, y - crossSize, x, y + crossSize, paint)
    }

    /**
     * Draws a swipe arrow with a specific color.
     *
     * @param canvas Canvas to draw on
     * @param annotation Swipe arrow annotation data
     * @param density Screen density for sizing
     * @param color Color to use for the annotation
     */
    private fun drawSwipeArrowWithColor(
        canvas: Canvas,
        annotation: ActionAnnotation.SwipeArrow,
        density: Float,
        color: Int,
    ) {
        val paint = createStrokePaint(color, density)
        val fillPaint = createFillPaint(color)

        val startX = (annotation.startX / 1000f) * annotation.screenWidth
        val startY = (annotation.startY / 1000f) * annotation.screenHeight
        val endX = (annotation.endX / 1000f) * annotation.screenWidth
        val endY = (annotation.endY / 1000f) * annotation.screenHeight

        // Draw start point circle
        val startRadius = CIRCLE_RADIUS_DP * density * 0.5f
        canvas.drawCircle(startX, startY, startRadius, fillPaint)

        // Draw line
        canvas.drawLine(startX, startY, endX, endY, paint)

        // Draw arrow head
        drawArrowHead(canvas, startX, startY, endX, endY, paint, density)
    }

    /**
     * Draws dashed connection lines between consecutive steps.
     *
     * @param canvas Canvas to draw on
     * @param steps List of step annotations
     * @param density Screen density for sizing
     */
    private fun drawConnectionLines(canvas: Canvas, steps: List<ActionAnnotation>, density: Float) {
        if (steps.size < 2) return

        val linePaint =
            Paint().apply {
                color = Color.parseColor("#80E91E63") // Semi-transparent pink
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                isAntiAlias = true
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * density, 5f * density), 0f)
            }

        for (i in 0 until steps.size - 1) {
            val current = steps[i]
            val next = steps[i + 1]

            val (currentX, currentY) = getStepCenter(current)
            val (nextX, nextY) = getStepCenter(next)

            if (currentX != null && currentY != null && nextX != null && nextY != null) {
                canvas.drawLine(currentX, currentY, nextX, nextY, linePaint)
            }
        }
    }

    /**
     * Gets the center point of a step annotation.
     *
     * @param step Step annotation to get center for
     * @return Pair of (x, y) coordinates, or (null, null) if not applicable
     */
    private fun getStepCenter(step: ActionAnnotation): Pair<Float?, Float?> = when (step) {
        is ActionAnnotation.TapCircle -> {
            val x = (step.x / 1000f) * step.screenWidth
            val y = (step.y / 1000f) * step.screenHeight
            Pair(x, y)
        }

        is ActionAnnotation.SwipeArrow -> {
            // Use start point for swipe
            val x = (step.startX / 1000f) * step.screenWidth
            val y = (step.startY / 1000f) * step.screenHeight
            Pair(x, y)
        }

        is ActionAnnotation.LongPressCircle -> {
            val x = (step.x / 1000f) * step.screenWidth
            val y = (step.y / 1000f) * step.screenHeight
            Pair(x, y)
        }

        is ActionAnnotation.DoubleTapCircle -> {
            val x = (step.x / 1000f) * step.screenWidth
            val y = (step.y / 1000f) * step.screenHeight
            Pair(x, y)
        }

        else -> {
            Pair(null, null)
        }
    }

    // Colors for batch steps (cycle through these)
    private val BATCH_STEP_COLORS =
        listOf(
            // Red
            Color.parseColor("#F44336"),
            // Orange
            Color.parseColor("#FF9800"),
            // Yellow
            Color.parseColor("#FFEB3B"),
            // Green
            Color.parseColor("#4CAF50"),
            // Blue
            Color.parseColor("#2196F3"),
            // Purple
            Color.parseColor("#9C27B0"),
        )

    /**
     * Creates a stroke paint for drawing outlines.
     *
     * @param color Color for the paint
     * @param density Screen density for stroke width calculation
     * @return Configured Paint object
     */
    private fun createStrokePaint(color: Int, density: Float): Paint = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_DP * density
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Creates a fill paint with semi-transparency.
     *
     * @param color Base color (alpha will be set to 128)
     * @return Configured Paint object
     */
    private fun createFillPaint(color: Int): Paint = Paint().apply {
        this.color = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * Creates an ActionAnnotation from an AgentAction.
     *
     * Converts agent actions to their corresponding visual annotation types.
     *
     * @param action Agent action to convert
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Appropriate ActionAnnotation for the action type
     *
     */
    fun createAnnotation(
        action: com.kevinluo.autoglm.action.AgentAction,
        screenWidth: Int,
        screenHeight: Int,
    ): ActionAnnotation = when (action) {
        is com.kevinluo.autoglm.action.AgentAction.Tap -> {
            ActionAnnotation.TapCircle(
                x = action.x,
                y = action.y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        }

        is com.kevinluo.autoglm.action.AgentAction.Swipe -> {
            ActionAnnotation.SwipeArrow(
                startX = action.startX,
                startY = action.startY,
                endX = action.endX,
                endY = action.endY,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        }

        is com.kevinluo.autoglm.action.AgentAction.LongPress -> {
            ActionAnnotation.LongPressCircle(
                x = action.x,
                y = action.y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                durationMs = action.durationMs,
            )
        }

        is com.kevinluo.autoglm.action.AgentAction.DoubleTap -> {
            ActionAnnotation.DoubleTapCircle(
                x = action.x,
                y = action.y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        }

        is com.kevinluo.autoglm.action.AgentAction.Type -> {
            ActionAnnotation.TypeText(action.text)
        }

        is com.kevinluo.autoglm.action.AgentAction.TypeName -> {
            ActionAnnotation.TypeText(action.text)
        }

        is com.kevinluo.autoglm.action.AgentAction.Batch -> {
            // Convert each step in the batch to an annotation
            val stepAnnotations =
                action.steps.mapNotNull { step ->
                    val annotation = createAnnotation(step, screenWidth, screenHeight)
                    // Only include visual annotations (not None or TypeText for batch display)
                    if (annotation is ActionAnnotation.TapCircle ||
                        annotation is ActionAnnotation.SwipeArrow ||
                        annotation is ActionAnnotation.LongPressCircle ||
                        annotation is ActionAnnotation.DoubleTapCircle
                    ) {
                        annotation
                    } else {
                        null
                    }
                }
            if (stepAnnotations.isNotEmpty()) {
                ActionAnnotation.BatchSteps(
                    steps = stepAnnotations,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                )
            } else {
                ActionAnnotation.None
            }
        }

        else -> {
            ActionAnnotation.None
        }
    }
}
