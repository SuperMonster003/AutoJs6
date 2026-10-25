package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import org.autojs.autojs.core.image.ColorFinder
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.TemplateMatching
import org.autojs.autojs.core.opencv.Mat
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.autojs.autojs.runtime.api.modules.images.OpenCvImageArgumentPolicy
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc

/** Bitmap operations run on the provider's image executor; inputs remain owned by their handles. */
internal class NodeImageOperations(private val context: Context) {
    fun transform(method: String, bitmap: Bitmap, options: JSONObject): Bitmap {
        if (method == "copy") return ownedCopy(bitmap)
        if (method == "rotate" || method == "flip") {
            val matrix = Matrix()
            if (method == "rotate") {
                val degrees = finite(options, "degrees")
                val x = finite(options, "centerX", bitmap.width / 2.0)
                val y = finite(options, "centerY", bitmap.height / 2.0)
                require(listOf(degrees, x, y).all { it.toFloat().isFinite() }) { "Rotation arguments exceed float range." }
                matrix.postRotate(degrees.toFloat(), x.toFloat(), y.toFloat())
            } else {
                val direction = options.optString("direction", "horizontal")
                require(direction in setOf("horizontal", "vertical", "both", "none")) { "Unknown flip direction." }
                matrix.setScale(if (direction == "horizontal" || direction == "both") -1f else 1f,
                    if (direction == "vertical" || direction == "both") -1f else 1f)
            }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, options.optBoolean("filter", true))
            return if (result === bitmap) ownedCopy(bitmap) else result
        }
        if (method == "clip") {
            val rect = rectangle(bitmap, options.getInt("x"), options.getInt("y"), options.getInt("width"), options.getInt("height"))
            val clipped = Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
            // Android may return the input when the region covers the whole image.
            return if (clipped === bitmap) checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else clipped
        }
        // Inversion is defined on straight RGB, not Android's premultiplied storage channels.
        return withMat(bitmap, straightAlpha = method == "invert") { source ->
            val output = Mat()
            val gray = Mat()
            try {
                when (method) {
                    "resize" -> {
                        val width = options.getInt("width")
                        val height = options.getInt("height")
                        require(width in 1..8192 && height in 1..8192) { "Image dimensions must be in 1..8192." }
                        Imgproc.resize(source, output, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, interpolation(options))
                    }
                    "scale" -> {
                        val fx = finite(options, "fx")
                        val fy = finite(options, "fy", fx)
                        require(fx > 0 && fy > 0 && Math.rint(bitmap.width * fx) in 1.0..8192.0 && Math.rint(bitmap.height * fy) in 1.0..8192.0) { "Scaled image dimensions must be in 1..8192." }
                        Imgproc.resize(source, output, Size(), fx, fy, interpolation(options))
                    }
                    "blur" -> Imgproc.blur(source, output, kernel(options, false))
                    "gaussianBlur" -> {
                        val sigmaX = finite(options, "sigmaX", 0.0)
                        val sigmaY = finite(options, "sigmaY", 0.0)
                        require(sigmaX >= 0 && sigmaY >= 0) { "Gaussian sigma must be non-negative." }
                        Imgproc.GaussianBlur(source, output, kernel(options, true), sigmaX, sigmaY)
                    }
                    "medianBlur" -> {
                        val size = integer(options.opt("size") ?: 3, "size")
                        require(size >= 1 && size % 2 == 1) { "Median kernel size must be a positive odd integer." }
                        Imgproc.medianBlur(source, output, size)
                    }
                    "invert" -> {
                        Core.extractChannel(source, gray, 3)
                        Core.bitwise_not(source, output)
                        Core.insertChannel(gray, output, 3)
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
                toBitmap(output, premultiplyAlpha = method == "invert")
            } finally {
                gray.release()
                output.release()
            }
        }
    }

    fun pixel(bitmap: Bitmap, x: Int, y: Int): Int {
        rectangle(bitmap, x, y, 1, 1)
        return bitmap.getPixel(x, y)
    }

    fun readPixels(bitmap: Bitmap, options: JSONObject): JSONObject {
        val rect = region(bitmap, options)
        require(rect.width.toLong() * rect.height <= MAX_JSON_PIXELS) { "JSON readPixels supports at most $MAX_JSON_PIXELS pixels; choose a smaller region or use the Node binary pixel API." }
        val pixels = IntArray(Math.multiplyExact(rect.width, rect.height))
        bitmap.getPixels(pixels, 0, rect.width, rect.x, rect.y, rect.width, rect.height)
        return JSONObject().put("width", rect.width).put("height", rect.height).put("data", JSONArray(pixels.toList()))
    }

    fun detectsColor(bitmap: Bitmap, color: Any, x: Int, y: Int, options: JSONObject): Boolean {
        val value = pixel(bitmap, x, y)
        val threshold = threshold(options)
        val detector = ColorDetector.get(parseColor(color), options.optString("algorithm", "diff"), threshold)
        return detector.detectColor(Color.red(value), Color.green(value), Color.blue(value))
    }

    fun findAllPointsForColor(bitmap: Bitmap, color: Any, options: JSONObject): String = withMat(bitmap) { source ->
        JSONArray().apply {
            ColorFinder.findPointsByColors(bitmap, source, parseColor(color), threshold(options), region(bitmap, options), intArrayOf(), MAX_JSON_POINTS).forEach { put(point(it)) }
        }.toString()
    }

    private fun threshold(options: JSONObject): Int = integer(options.opt("threshold") ?: 4, "threshold")
        .also { require(it in 0..255) { "Color threshold must be in 0..255." } }

    private fun ownedCopy(bitmap: Bitmap): Bitmap = checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))

    private fun interpolation(options: JSONObject): Int = OpenCvImageArgumentPolicy.parseInterpolation(options.opt("interpolation").takeUnless { it == JSONObject.NULL })

    private fun finite(options: JSONObject, key: String, default: Double? = null): Double {
        val value = if (!options.has(key) || options.isNull(key)) default else (options.opt(key) as? Number)?.toDouble()
        require(value != null && value.isFinite()) { "$key must be a finite number." }
        return value
    }

    private fun kernel(options: JSONObject, odd: Boolean): Size {
        val raw = options.opt("size") ?: 3
        val array = raw as? JSONArray
        if (array != null) require(array.length() == 2) { "Kernel size requires [width, height]." }
        val width = integer(array?.opt(0) ?: raw, "kernel width")
        val height = integer(array?.opt(1) ?: raw, "kernel height")
        require(width > 0 && height > 0 && (!odd || (width % 2 == 1 && height % 2 == 1))) { "Kernel dimensions must be positive${if (odd) " odd" else ""} integers." }
        return Size(width.toDouble(), height.toDouble())
    }

    fun integer(value: Any?, name: String): Int {
        val number = (value as? Number)?.toDouble()
        require(number != null && number.isFinite() && number % 1.0 == 0.0 && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) { "$name must be an integer." }
        return number.toInt()
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

    fun parseColor(value: Any): Int = Colors.toIntRhino(value)

    private fun point(point: Point, offset: Rect? = null) = JSONObject()
        .put("x", point.x + (offset?.x ?: 0)).put("y", point.y + (offset?.y ?: 0))

    fun region(bitmap: Bitmap, options: JSONObject): Rect {
        if (!options.has("region")) return Rect(0, 0, bitmap.width, bitmap.height)
        val region = options.optJSONArray("region")
        require(region != null && region.length() == 4) { "Image region must be [x, y, width, height]." }
        return rectangle(bitmap, integer(region.opt(0), "region x"), integer(region.opt(1), "region y"), integer(region.opt(2), "region width"), integer(region.opt(3), "region height"))
    }

    private fun rectangle(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Rect {
        require(x >= 0 && y >= 0 && width > 0 && height > 0 && x.toLong() + width <= bitmap.width && y.toLong() + height <= bitmap.height) {
            "Image region must fit inside ${bitmap.width}x${bitmap.height}."
        }
        return Rect(x, y, width, height)
    }

    private inline fun <T> withMat(bitmap: Bitmap, straightAlpha: Boolean = false, action: (Mat) -> T): T {
        OpenCVHelper.initIfNeeded(context) { }
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat, straightAlpha)
            return action(mat)
        } finally { mat.release() }
    }

    private fun toBitmap(mat: Mat, premultiplyAlpha: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(mat, bitmap, premultiplyAlpha)
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    companion object {
        // Bound boxed JSON before allocation; large pixel planes use the existing PFD path.
        const val MAX_JSON_PIXELS = 16_384
        const val MAX_JSON_POINTS = 4_096
    }
}
