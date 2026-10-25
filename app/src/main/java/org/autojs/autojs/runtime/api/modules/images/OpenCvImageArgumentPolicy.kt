package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.NativeArray
import org.opencv.imgproc.Imgproc
import org.opencv.core.Size as OpenCvSize

/** Validates script arguments before they can cross OpenCV's native boundary. */
internal object OpenCvImageArgumentPolicy {

    private const val RESIZE_SIGNATURE = "images.resize(image, [width, height], interpolation?)"

    private val interpolationByName = linkedMapOf(
        "NEAREST" to Imgproc.INTER_NEAREST,
        "LINEAR" to Imgproc.INTER_LINEAR,
        "CUBIC" to Imgproc.INTER_CUBIC,
        "AREA" to Imgproc.INTER_AREA,
        "LANCZOS4" to Imgproc.INTER_LANCZOS4,
        "LINEAR_EXACT" to Imgproc.INTER_LINEAR_EXACT,
        "NEAREST_EXACT" to Imgproc.INTER_NEAREST_EXACT,
    )

    private val interpolationValues = interpolationByName.values.toSet()

    data class ResizeArguments(
        val size: OpenCvSize,
        val interpolation: Int,
    )

    fun parseResize(size: Any?, interpolation: Any?): ResizeArguments {
        val nativeSize = size as? NativeArray ?: throw WrappedIllegalArgumentException(
            "Argument \"size\" ${size.jsBrief()} for images.resize must be an Array with one or two dimensions. " +
                    "Expected: $RESIZE_SIGNATURE",
        )
        if (nativeSize.size !in 1..2) {
            throw WrappedIllegalArgumentException(
                "Argument \"size\" for images.resize must contain one or two dimensions, but contained ${nativeSize.size}. " +
                        "Expected: $RESIZE_SIGNATURE",
            )
        }
        val width = parseResizeDimension(nativeSize[0], "width")
        val height = parseResizeDimension(nativeSize[if (nativeSize.size == 1) 0 else 1], "height")
        return ResizeArguments(OpenCvSize(width, height), parseInterpolation(interpolation))
    }

    fun parseInterpolation(interpolation: Any?): Int {
        val value = when {
            interpolation.isJsNullish() -> Imgproc.INTER_LINEAR
            interpolation.isJsString() -> {
                val normalized = coerceString(interpolation)
                    .trim()
                    .uppercase()
                    .replace('-', '_')
                    .removePrefix("INTER_")
                interpolationByName[normalized]
            }
            interpolation.isJsNumber() -> {
                val number = runCatching { coerceNumber(interpolation) }.getOrNull()
                number?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()
            }
            else -> null
        }
        if (value == null || value !in interpolationValues) {
            throw WrappedIllegalArgumentException(
                "Unknown image interpolation ${interpolation.jsBrief()}. Expected one of " +
                        "${interpolationByName.keys.joinToString()} or a numeric value from " +
                        "${interpolationValues.min()} to ${interpolationValues.max()}",
            )
        }
        return value
    }

    private fun parseResizeDimension(value: Any?, name: String): Double {
        val number = runCatching { coerceNumber(value) }.getOrNull()
        if (number == null || !number.isFinite() || number < 1.0 || number > Int.MAX_VALUE.toDouble()) {
            throw WrappedIllegalArgumentException(
                "Argument \"$name\" ${value.jsBrief()} for images.resize must be a positive finite number no greater " +
                        "than ${Int.MAX_VALUE}. Expected: $RESIZE_SIGNATURE",
            )
        }
        return number
    }
}
