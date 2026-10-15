package org.autojs.autojs.runtime.api.augment.global

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import kotlin.math.abs
import kotlin.math.floor

/** Shared argument and numeric rules for the four script coordinate converters. */
internal object CoordinateScale {

    enum class Axis(val functionName: String, val usesWidth: Boolean, val cross: Boolean) {
        X("cX", true, false),
        Y("cY", false, false),
        Y_FROM_X("cYx", true, true),
        X_FROM_Y("cXy", false, true),
    }

    fun convert(axis: Axis, width: Int, height: Int, scale: Scale, args: Array<out Any?>): Double {
        val name = axis.functionName
        require(args.size <= 3) { "$name accepts at most three arguments" }
        val dimension = if (axis.usesWidth) width.toDouble() else height.toDouble()
        if (args.isEmpty()) {
            require(!axis.cross) { "$name requires a coordinate" }
            return dimension
        }

        val value = coerceNumber(args[0])
        require(value.isFinite()) { "Coordinate for $name must be finite" }
        var base = args.getOrNull(1)
        var modeArg = args.getOrNull(2)
        if (base is Boolean || base == "auto") {
            require(modeArg.isJsNullish()) { "$name cannot combine a mode shortcut with a third argument" }
            modeArg = base
            base = null
        }
        val mode: Boolean? = when {
            modeArg.isJsNullish() || modeArg == "auto" -> null
            modeArg is Boolean -> modeArg
            else -> throw IllegalArgumentException("Ratio mode for $name must be true, false or auto")
        }
        val literalRatio = if (axis.cross && base is CharSequence) {
            require(mode != false) { "$name cannot use an aspect ratio with absolute coordinate mode" }
            parseAspectRatio(base.toString(), name)
        } else null
        val isRatio = literalRatio != null || (mode ?: (abs(value) < 1))

        val result = if (!isRatio) {
            val defaultBase = if (axis.usesWidth) scale.baseX else scale.baseY
            divideScaled(value, dimension, absoluteBase(base, defaultBase, name))
        } else if (!axis.cross) {
            // The direct proportional form intentionally does not use the base.
            dimension * value
        } else {
            val ratio = literalRatio ?: when {
                base.isJsNullish() -> {
                    // Only the implicit aspect ratio needs a coherent pair.
                    // An explicit base or ratio is independent of that pair.
                    scale.ensureBasesConsistent()
                    scale.baseX.toDouble() / scale.baseY.toDouble()
                }
                base is Number -> base.toDouble().also { requirePositiveRatio(it, name) }
                else -> throw IllegalArgumentException("Aspect ratio for $name must be a number or width:height")
            }
            // A ratio always means width / height, including landscape layouts.
            if (axis.usesWidth) divideScaled(value, dimension, ratio) else {
                val product = dimension * value
                if (product.isFinite()) product * ratio else value * ratio * dimension
            }
        }
        require(result.isFinite()) { "Scaled coordinate for $name must be finite" }
        return roundCoordinate(result)
    }

    private fun absoluteBase(base: Any?, default: Int, name: String): Double {
        if (base.isJsNullish()) return default.toDouble()
        require(base is Number) { "Scale base for $name must be a positive integer" }
        val number = base.toDouble()
        require(number.isFinite() && number >= 1 && number <= Int.MAX_VALUE && number == floor(number)) {
            "Scale base for $name must be a positive integer no larger than ${Int.MAX_VALUE}"
        }
        return number
    }

    private fun parseAspectRatio(text: String, name: String): Double {
        val parts = text.split(':')
        require(parts.size == 2) { "Aspect ratio for $name must have exactly two terms: width:height" }
        val width = parts[0].trim().toDoubleOrNull()
        val height = parts[1].trim().toDoubleOrNull()
        require(width != null && height != null && width.isFinite() && height.isFinite() && width > 0 && height > 0) {
            "Aspect ratio terms for $name must be positive finite numbers"
        }
        return (width / height).also { requirePositiveRatio(it, name) }
    }

    private fun requirePositiveRatio(value: Double, name: String) {
        require(value.isFinite() && value > 0) { "Aspect ratio for $name must be a positive finite number" }
    }

    private fun divideScaled(value: Double, dimension: Double, divisor: Double): Double {
        val product = dimension * value
        // Do not reject a representable result merely because multiplication
        // overflowed before division could reduce it.
        return if (product.isFinite()) product / divisor else value * (dimension / divisor)
    }

    private fun roundCoordinate(value: Double): Double {
        // Match roundToLong's nearest-integer, ties-toward-positive-infinity rule
        // without silently clamping a finite JavaScript number to Long's range.
        val lower = floor(value)
        return lower + if (value - lower >= 0.5) 1.0 else 0.0
    }
}
