package org.autojs.autojs.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.max

internal object ImageEditingEngine {

    enum class Adjustment {
        BRIGHTNESS,
        CONTRAST,
        SATURATION,
        TEMPERATURE,
    }

    data class Point(val x: Float, val y: Float)

    data class BrushStroke(
        val color: Int,
        val width: Float,
        val points: List<Point>,
    )

    data class TextSpec(
        val text: String,
        val color: Int,
        val size: Float,
        val centerX: Float,
        val centerY: Float,
    )

    fun transform(
        source: Bitmap,
        rotation: Float = 0f,
        flipX: Boolean = false,
        flipY: Boolean = false,
    ): Bitmap {
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (flipX || flipY) {
                postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
            }
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun crop(source: Bitmap, selection: Rect): Bitmap {
        val safe = Rect(
            selection.left.coerceIn(0, source.width - 1),
            selection.top.coerceIn(0, source.height - 1),
            selection.right.coerceIn(1, source.width),
            selection.bottom.coerceIn(1, source.height),
        )
        if (safe.width() < MIN_CROP_SIZE || safe.height() < MIN_CROP_SIZE) {
            throw IllegalArgumentException("Crop selection is too small")
        }
        return Bitmap.createBitmap(source, safe.left, safe.top, safe.width(), safe.height())
    }

    fun colorMatrix(adjustment: Adjustment, value: Int): ColorMatrix {
        val normalized = value.coerceIn(-100, 100) / 100f
        return when (adjustment) {
            Adjustment.BRIGHTNESS -> {
                val offset = normalized * 255f
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, offset,
                        0f, 1f, 0f, 0f, offset,
                        0f, 0f, 1f, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                )
            }
            Adjustment.CONTRAST -> {
                val scale = max(0.05f, 1f + normalized)
                val offset = 128f * (1f - scale)
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, offset,
                        0f, scale, 0f, 0f, offset,
                        0f, 0f, scale, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                )
            }
            Adjustment.SATURATION -> ColorMatrix().apply {
                setSaturation((1f + normalized).coerceAtLeast(0f))
            }
            Adjustment.TEMPERATURE -> {
                val red = 1f + normalized * 0.35f
                val green = 1f + normalized * 0.08f
                val blue = 1f - normalized * 0.35f
                ColorMatrix().apply { setScale(red, green, blue, 1f) }
            }
        }
    }

    fun applyAdjustment(source: Bitmap, adjustment: Adjustment, value: Int): Bitmap {
        val target = blankBitmapLike(source, source.width, source.height)
        return try {
            Canvas(target).drawBitmap(
                source,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(colorMatrix(adjustment, value))
                },
            )
            target
        } catch (error: Throwable) {
            target.recycle()
            throw error
        }
    }

    fun applyBrush(source: Bitmap, strokes: List<BrushStroke>): Bitmap {
        val target = mutableCopy(source)
        return try {
            val canvas = Canvas(target)
            strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.width.coerceAtLeast(1f)
                }
                if (stroke.points.size == 1) {
                    val point = stroke.points.first()
                    canvas.drawCircle(point.x, point.y, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
                } else {
                    val path = Path().apply {
                        moveTo(stroke.points.first().x, stroke.points.first().y)
                        for (index in 1 until stroke.points.size) {
                            val point = stroke.points[index]
                            lineTo(point.x, point.y)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }
            target
        } catch (error: Throwable) {
            target.recycle()
            throw error
        }
    }

    fun addText(source: Bitmap, spec: TextSpec): Bitmap {
        val target = mutableCopy(source)
        return try {
            val canvas = Canvas(target)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = spec.color
                textSize = spec.size.coerceAtLeast(1f)
                style = Paint.Style.FILL
            }
            val lines = spec.text.lines().ifEmpty { listOf(spec.text) }
            val metrics = paint.fontMetrics
            val lineHeight = (metrics.descent - metrics.ascent) * TEXT_LINE_SPACING
            val blockHeight = lineHeight * lines.size
            val firstBaseline = spec.centerY - blockHeight / 2f - metrics.ascent
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    spec.centerX - paint.measureText(line) / 2f,
                    firstBaseline + index * lineHeight,
                    paint,
                )
            }
            target
        } catch (error: Throwable) {
            target.recycle()
            throw error
        }
    }

    private fun mutableCopy(source: Bitmap): Bitmap =
        source.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("Unable to allocate edited bitmap")

    private fun blankBitmapLike(source: Bitmap, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setHasAlpha(source.hasAlpha())
        }

    private const val MIN_CROP_SIZE = 1
    private const val TEXT_LINE_SPACING = 1.12f
}
