package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.autojs.autojs.core.image.ColorFinder
import org.autojs.autojs.core.image.TemplateMatching
import org.autojs.autojs.core.opencv.Mat
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Bitmap operations run on the provider's image executor; inputs remain owned by their handles. */
internal class NodeImageOperations(private val context: Context) {
    fun transform(method: String, bitmap: Bitmap, options: JSONObject): Bitmap {
        if (method == "clip") {
            val rect = rectangle(bitmap, options.getInt("x"), options.getInt("y"), options.getInt("width"), options.getInt("height"))
            val clipped = Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
            // Android may return the input when the region covers the whole image.
            return if (clipped === bitmap) checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else clipped
        }
        return withMat(bitmap) { source ->
            val output = Mat()
            val gray = Mat()
            try {
                when (method) {
                    "resize" -> {
                        val width = options.getInt("width")
                        val height = options.getInt("height")
                        require(width in 1..8192 && height in 1..8192) { "Image dimensions must be in 1..8192." }
                        Imgproc.resize(source, output, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
                    }
                    "grayscale" -> Imgproc.cvtColor(source, output, Imgproc.COLOR_RGBA2GRAY)
                    "threshold" -> {
                        val value = options.optDouble("threshold", 127.0)
                        val maximum = options.optDouble("maxValue", 255.0)
                        require(value.isFinite() && value in 0.0..255.0 && maximum.isFinite() && maximum in 0.0..255.0) {
                            "Threshold and maxValue must be in 0..255."
                        }
                        val type = when (options.optString("type", "binary")) {
                            "binary" -> Imgproc.THRESH_BINARY
                            "binary_inv" -> Imgproc.THRESH_BINARY_INV
                            "trunc" -> Imgproc.THRESH_TRUNC
                            "tozero" -> Imgproc.THRESH_TOZERO
                            "tozero_inv" -> Imgproc.THRESH_TOZERO_INV
                            else -> throw IllegalArgumentException("Unknown threshold type.")
                        }
                        Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.threshold(gray, output, value, maximum, type)
                    }
                    else -> throw IllegalArgumentException("Unknown image transform: $method")
                }
                toBitmap(output)
            } finally {
                gray.release()
                output.release()
            }
        }
    }

    fun match(bitmap: Bitmap, template: Bitmap, options: JSONObject, multiple: Boolean): String = withMat(bitmap) { source ->
        withMat(template) { target ->
            val rect = region(bitmap, options)
            require(template.width <= rect.width && template.height <= rect.height) { "Template must fit inside the search region." }
            val threshold = options.optDouble("threshold", 0.9)
            require(threshold.isFinite() && threshold in 0.0..1.0) { "Template threshold must be in 0..1." }
            val limit = if (multiple) options.optInt("limit", options.optInt("max", 5)) else 1
            require(limit in 1..1000) { "Template match limit must be in 1..1000." }
            val cropped = Mat(source, rect)
            try {
                val matchOptions = TemplateMatching.Options(
                    TemplateMatching.MATCHING_METHOD_NONE, minOf(0.7, threshold).toFloat(), threshold.toFloat(),
                    TemplateMatching.MAX_LEVEL_AUTO, false, limit,
                )
                if (multiple) {
                    JSONArray().apply {
                        TemplateMatching.fastTemplateMatching(cropped, target, matchOptions).forEach { match ->
                            put(point(match.point, rect).put("score", match.similarity))
                        }
                    }.toString()
                } else {
                    TemplateMatching.singleTemplateMatching(cropped, target, matchOptions)?.let { point(it, rect).toString() } ?: "null"
                }
            } finally { cropped.release() }
        }
    }

    fun findColors(bitmap: Bitmap, color: Any, points: JSONArray, options: JSONObject): String = withMat(bitmap) { source ->
        val threshold = options.optInt("threshold", 4)
        require(threshold in 0..255) { "Color threshold must be in 0..255." }
        val offsets = IntArray(points.length() * 3)
        for (index in 0 until points.length()) {
            val tuple = points.optJSONArray(index)
            val item = points.optJSONObject(index)
            offsets[index * 3] = tuple?.getInt(0) ?: requireNotNull(item).getInt("x")
            offsets[index * 3 + 1] = tuple?.getInt(1) ?: requireNotNull(item).getInt("y")
            offsets[index * 3 + 2] = parseColor(tuple?.get(2) ?: requireNotNull(item).get("color"))
        }
        ColorFinder.findPointByColors(bitmap, source, parseColor(color), threshold, region(bitmap, options), offsets)
            ?.let { point(it).toString() } ?: "null"
    }

    private fun parseColor(value: Any): Int = when (value) {
        is Number -> value.toLong().toInt()
        is String -> Color.parseColor(if (value.matches(Regex("#[0-9a-fA-F]{3}")))
            "#" + value.drop(1).map { "$it$it" }.joinToString("") else value)
        else -> throw IllegalArgumentException("Color must be an ARGB number or a color string.")
    }

    private fun point(point: Point, offset: Rect? = null) = JSONObject()
        .put("x", point.x + (offset?.x ?: 0)).put("y", point.y + (offset?.y ?: 0))

    private fun region(bitmap: Bitmap, options: JSONObject): Rect {
        if (!options.has("region")) return Rect(0, 0, bitmap.width, bitmap.height)
        val region = options.optJSONArray("region")
        require(region != null && region.length() == 4) { "Image region must be [x, y, width, height]." }
        return rectangle(bitmap, region.getInt(0), region.getInt(1), region.getInt(2), region.getInt(3))
    }

    private fun rectangle(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Rect {
        require(x >= 0 && y >= 0 && width > 0 && height > 0 && x.toLong() + width <= bitmap.width && y.toLong() + height <= bitmap.height) {
            "Image region must fit inside ${bitmap.width}x${bitmap.height}."
        }
        return Rect(x, y, width, height)
    }

    private inline fun <T> withMat(bitmap: Bitmap, action: (Mat) -> T): T {
        OpenCVHelper.initIfNeeded(context) { }
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)
            return action(mat)
        } finally { mat.release() }
    }

    private fun toBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(mat, bitmap)
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
}
