package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.rhino.ArgumentGuards
import android.content.res.ColorStateList
import android.graphics.Color.HSVToColor
import android.graphics.Paint
import android.os.Build
import androidx.core.graphics.ColorUtils.HSLToColor
import androidx.core.graphics.ColorUtils.RGBToHSL
import androidx.core.graphics.ColorUtils.blendARGB
import androidx.core.graphics.ColorUtils.calculateContrast
import androidx.core.graphics.ColorUtils.calculateLuminance
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.rhino.extension.ArrayExtensions.jsArrayBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.modules.jsox.Numberx
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ColorUtils.roundToAlphaString
import org.autojs.autojs.util.ColorUtils.roundToHueString
import org.autojs.autojs.util.ColorUtils.roundToSaturationString
import org.autojs.autojs.util.ColorUtils.roundToValueString
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.ensureNativeArrayLength
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import kotlin.math.ceil
import kotlin.math.sqrt
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.api.modules.images.Images
import org.autojs.autojs.theme.color.ColorHarmony
import org.autojs.autojs.theme.color.ColorQuantizer
import org.autojs.autojs.theme.color.ColorVisionSimulation
import org.autojs.autojs.theme.color.OkColor
import org.autojs.autojs.theme.color.OkLab
import org.autojs.autojs.theme.color.OkLch
import org.autojs.autojs.theme.color.ThemeColorDerivation

@Suppress("unused", "UNUSED_PARAMETER")
object Colors : ArgumentGuards() {

    @JvmField
    @Suppress("MayBeConstant")
    val DEFAULT_COLOR_THRESHOLD = 4

    @JvmField
    @Suppress("MayBeConstant")
    val DEFAULT_COLOR_ALGORITHM = "diff"

    private const val DEFAULT_CONTRAST_RATIO = 4.5
    private const val DEFAULT_TONE_COUNT = 10
    private const val MAX_TONE_COUNT = 20
    private const val DEFAULT_EXTRACT_COUNT = 5

    /** Default number of pixels sampled by `colors.extract`. zh-CN: `colors.extract` 默认采样的像素数. */
    private const val EXTRACT_SAMPLES = 65536.0

    private const val OPAQUE = 0xFF shl 24

    internal val colorTables = listOf(
        ColorTable.Android,
        ColorTable.Css,
        ColorTable.Web,
        ColorTable.Material,
    )

    private val all: Map<String, Int> by lazy {
        val colorMap = mutableMapOf<String, Int>()
        for (table in colorTables) {
            table::class.java.declaredFields.forEach { field ->
                if (field.type == Int::class.javaPrimitiveType) {
                    runCatching {
                        field.isAccessible = true
                        val colorName = field.name
                        val colorValue = field.getInt(null)
                        if (!colorMap.containsKey(colorName)) {
                            colorMap[colorName] = colorValue
                        }
                    }
                }
            }
        }
        colorMap
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toInt(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        toIntRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toIntRhino(color: Any?): Int = when (color) {
        is ColorNativeObject -> toIntRhino(color.color)
        is ThemeColor -> ColorUtils.toInt(color)
        is Number -> ColorUtils.toInt(color.toLong())
        is String -> ColorUtils.toInt(color)
        else -> throw WrappedIllegalArgumentException("Argument \"$color\" cannot be converted as a color int")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHex(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, alphaOrLengthArg) = it
        toHexRhino(colorArg, alphaOrLengthArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toHexRhino(color: Any?, alphaOrLength: Any? = null): String = when (color) {
        is ColorNativeObject -> toHexRhino(color.color, alphaOrLength)
        is ThemeColor -> {
            when (alphaOrLength) {
                null -> ColorUtils.toHex(color)
                is Boolean -> ColorUtils.toHex(color, alphaOrLength)
                is Number -> ColorUtils.toHex(color, alphaOrLength.toInt())
                else -> throw WrappedIllegalArgumentException("Argument \"$alphaOrLength\" for toHex(color: ThemeColor, alphaOrLength: ${alphaOrLength.javaClass}) is invalid")
            }
        }
        is Number -> {
            when (alphaOrLength) {
                null -> ColorUtils.toHex(color.toLong())
                is Boolean -> ColorUtils.toHex(color.toLong(), alphaOrLength)
                is Number -> ColorUtils.toHex(color.toLong(), alphaOrLength.toInt())
                else -> throw WrappedIllegalArgumentException("Argument \"$alphaOrLength\" for toHex(color: Number, alphaOrLength: ${alphaOrLength.javaClass}) is invalid")
            }
        }
        is String -> {
            when (alphaOrLength) {
                null -> ColorUtils.toHex(color)
                is Boolean -> ColorUtils.toHex(color, alphaOrLength)
                is Number -> ColorUtils.toHex(color, alphaOrLength.toInt())
                else -> throw WrappedIllegalArgumentException("Argument \"$alphaOrLength\" for toHex(color: String, alphaOrLength: ${alphaOrLength.javaClass}) is invalid")
            }
        }
        else -> throw WrappedIllegalArgumentException("Argument \"$color\" cannot be converted as a color int")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toFullHex(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) {
        toFullHexRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toFullHexRhino(color: Any?): String = when (color) {
        is ColorNativeObject -> toFullHexRhino(color.color)
        is ThemeColor -> ColorUtils.toFullHex(color)
        is Number -> ColorUtils.toFullHex(color.toLong())
        is String -> ColorUtils.toFullHex(color)
        else -> throw WrappedIllegalArgumentException("Argument \"$color\" cannot be converted as a color int")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toString(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) {
        when (it.size) {
            0 -> super.toString()
            else -> toHex(it)
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun alpha(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        alphaRhino(colorArg, optionsArg)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getAlpha(args: Array<out Any?>): Double {
        return alpha(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun alphaRhino(color: Any?, options: Any? = null): Double {
        val niceColor = toIntRhino(color)
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val max = niceOptions.prop("max")
        return when {
            max.isJsNullish() -> (toIntRhino(niceColor) shr 24) and 0xFF
            Context.toNumber(max) == 255.0 -> (toIntRhino(niceColor) shr 24) and 0xFF
            Context.toNumber(max) == 1.0 -> alphaDoubleRhino(niceColor)
            else -> throw WrappedIllegalArgumentException("Option \"max\" specified must be either 1 or 255 instead of $max for colors.alpha")
        }.toDouble()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun getAlphaRhino(color: Any?, options: Any? = null): Double {
        return alphaRhino(color, options)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun alphaDouble(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toDoubleComponent(alphaRhino(colorArg, optionsArg))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getAlphaDouble(args: Array<out Any?>): Double {
        return alphaDouble(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun alphaDoubleRhino(color: Any?): Double {
        return toDoubleComponent(alphaRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setAlpha(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, alphaArg) = it
        setAlphaRhino(colorArg, alphaArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setAlphaRhino(color: Any?, alpha: Any?): Int {
        val (r, g, b) = toRgbRhino(color)
        return toIntRhino(argbRhino(alpha, r, g, b))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setAlphaRelative(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, alphaArg) = it
        setAlphaRelativeRhino(colorArg, alphaArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setAlphaRelativeRhino(color: Any?, percentage: Any?): Int {
        return setAlphaRhino(color, getAlphaRhino(color) * parseRelativePercentage(percentage))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeAlpha(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        removeAlphaRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeAlphaRhino(color: Any?): Int {
        return setAlphaRhino(color, 0)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun red(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        redRhino(colorArg, optionsArg)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getRed(args: Array<out Any?>): Double {
        return red(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun redRhino(color: Any?, options: Any? = null): Double {
        val niceColor = toIntRhino(color)
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val max = niceOptions.prop("max")
        return when {
            max.isJsNullish() -> (toIntRhino(niceColor) shr 16) and 0xFF
            Context.toNumber(max) == 255.0 -> (toIntRhino(niceColor) shr 16) and 0xFF
            Context.toNumber(max) == 1.0 -> redDoubleRhino(niceColor)
            else -> throw WrappedIllegalArgumentException("Option \"max\" specified must be either 1 or 255 instead of $max for colors.red")
        }.toDouble()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun getRedRhino(color: Any?, options: Any? = null): Double {
        return redRhino(color, options)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun redDouble(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toDoubleComponent(redRhino(colorArg, optionsArg))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getRedDouble(args: Array<out Any?>): Double {
        return redDouble(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun redDoubleRhino(color: Any?): Double {
        return toDoubleComponent(redRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setRed(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, redArg) = it
        setRedRhino(colorArg, redArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setRedRhino(color: Any?, red: Any?): Int {
        val (a, _, g, b) = toArgbRhino(color)
        return toIntRhino(argbRhino(a, red, g, b))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setRedRelative(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, redArg) = it
        setRedRelativeRhino(colorArg, redArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setRedRelativeRhino(color: Any?, percentage: Any?): Int {
        return setRedRhino(color, getRedRhino(color) * parseRelativePercentage(percentage))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeRed(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        removeRedRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeRedRhino(color: Any?): Int {
        return setRedRhino(color, 0)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun green(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        greenRhino(colorArg, optionsArg)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getGreen(args: Array<out Any?>): Double {
        return green(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun greenRhino(color: Any?, options: Any? = null): Double {
        val niceColor = toIntRhino(color)
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val max = niceOptions.prop("max")
        return when {
            max.isJsNullish() -> (toIntRhino(niceColor) shr 8) and 0xFF
            Context.toNumber(max) == 255.0 -> (toIntRhino(niceColor) shr 8) and 0xFF
            Context.toNumber(max) == 1.0 -> greenDoubleRhino(niceColor)
            else -> throw WrappedIllegalArgumentException("Option \"max\" specified must be either 1 or 255 instead of $max for colors.green")
        }.toDouble()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun getGreenRhino(color: Any?, options: Any? = null): Double {
        return greenRhino(color, options)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun greenDouble(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toDoubleComponent(greenRhino(colorArg, optionsArg))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getGreenDouble(args: Array<out Any?>): Double {
        return greenDouble(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun greenDoubleRhino(color: Any?): Double {
        return toDoubleComponent(greenRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setGreen(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, greenArg) = it
        setGreenRhino(colorArg, greenArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setGreenRhino(color: Any?, green: Any?): Int {
        val (a, r, _, b) = toArgbRhino(color)
        return toIntRhino(argbRhino(a, r, green, b))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setGreenRelative(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, greenArg) = it
        setGreenRelativeRhino(colorArg, greenArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setGreenRelativeRhino(color: Any?, percentage: Any?): Int {
        return setGreenRhino(color, getGreenRhino(color) * parseRelativePercentage(percentage))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeGreen(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        removeGreenRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeGreenRhino(color: Any?): Int {
        return setGreenRhino(color, 0)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun blue(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        blueRhino(colorArg, optionsArg)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getBlue(args: Array<out Any?>): Double {
        return blue(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun blueRhino(color: Any?, options: Any? = null): Double {
        val niceColor = toIntRhino(color)
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val max = niceOptions.prop("max")
        return when {
            max.isJsNullish() -> toIntRhino(niceColor) and 0xFF
            Context.toNumber(max) == 255.0 -> toIntRhino(niceColor) and 0xFF
            Context.toNumber(max) == 1.0 -> blueDoubleRhino(niceColor)
            else -> throw WrappedIllegalArgumentException("Option \"max\" specified must be either 1 or 255 instead of $max for colors.blue")
        }.toDouble()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun getBlueRhino(color: Any?, options: Any? = null): Double {
        return blueRhino(color, options)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun blueDouble(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toDoubleComponent(blueRhino(colorArg, optionsArg))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getBlueDouble(args: Array<out Any?>): Double {
        return blueDouble(args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun blueDoubleRhino(color: Any?): Double {
        return toDoubleComponent(blueRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setBlue(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, blueArg) = it
        setBlueRhino(colorArg, blueArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setBlueRhino(color: Any?, blue: Any?): Int {
        val (a, r, g) = toArgbRhino(color)
        return toIntRhino(argbRhino(a, r, g, blue))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setBlueRelative(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (colorArg, blueArg) = it
        setBlueRelativeRhino(colorArg, blueArg)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setBlueRelativeRhino(color: Any?, percentage: Any?): Int {
        return setBlueRhino(color, getBlueRhino(color) * parseRelativePercentage(percentage))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeBlue(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        removeBlueRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeBlueRhino(color: Any?): Int {
        return setBlueRhino(color, 0)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun rgb(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> rgbRhino(it[0])
            3 -> rgbRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.rgb")
        }
    }

    /**
     * Parameters
     * * red: `ColorComponent`
     * * green: `ColorComponent`
     * * blue: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun rgbRhino(red: Any?, green: Any?, blue: Any?): Int {
        val (r, g, b) = toUnit8RgbList(arrayOf(red, green, blue))
        return AndroidColor.rgb(r, g, b)
    }

    /**
     * Parameters
     * * o <1> rgbComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     * * o <2> color: `OmniColor`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun rgbRhino(o: Any?): Int = when (o) {
        is NativeArray -> {
            val (r, g, b) = o.also { ensureNativeArrayLength(o, 3, "colors.rgb") }
            rgbRhino(r, g, b)
        }
        else -> toIntRhino(toHexRhino(o, 6))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun argb(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> argbRhino(it[0])
            4 -> argbRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.argb")
        }
    }

    /**
     * Parameters
     * * alpha: `ColorComponent`
     * * red: `ColorComponent`
     * * green: `ColorComponent`
     * * blue: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun argbRhino(alpha: Any?, red: Any?, green: Any?, blue: Any?): Int {
        val (r, g, b) = toUnit8RgbList(arrayOf(red, green, blue))
        return AndroidColor.argb(parseDoubleComponent(alpha), r, g, b)
    }

    /**
     * Parameters
     * * o <1> argbComponents: `[ColorComponent, ColorComponent, ColorComponent, ColorComponent]`
     * * o <2> colorHex: `String` (#AARRGGBB)
     */
    @JvmStatic
    @RhinoFunctionBody
    fun argbRhino(o: Any?): Int = when (o) {
        is NativeArray -> {
            val (a, r, g, b) = o.also { ensureNativeArrayLength(o, 4, "colors.argb") }
            argbRhino(a, r, g, b)
        }
        else -> toIntRhino(toHexRhino(o, 8))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun rgba(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> rgbaRhino(it[0])
            2 -> rgbaRhino(it[0], it[1])
            4 -> rgbaRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.rgba")
        }
    }

    /**
     * Parameters
     * * red: `ColorComponent`
     * * green: `ColorComponent`
     * * blue: `ColorComponent`
     * * alpha: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun rgbaRhino(red: Any?, green: Any?, blue: Any?, alpha: Any?): Int {
        val (r, g, b) = toUnit8RgbList(arrayOf(red, green, blue))
        return AndroidColor.argb(parseDoubleComponent(alpha), r, g, b)
    }

    /**
     * Parameters
     * * rgbComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     * * alpha: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun rgbaRhino(rgbComponents: Any?, alpha: Any?): Int {
        if (rgbComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"rgb\" must be a JavaScript Array for colors.rgba(rgb, alpha)")
        val (r, g, b) = rgbComponents.also { ensureNativeArrayLength(it, 3, "colors.rgba") }
        return rgbaRhino(r, g, b, alpha)
    }

    /**
     * Parameters
     * * o <1> rgbaComponents: `[ColorComponent, ColorComponent, ColorComponent, ColorComponent]`
     * * o <2> colorHex: `String` (#RRGGBBAA)
     */
    @JvmStatic
    @RhinoFunctionBody
    fun rgbaRhino(o: Any?): Int = when (o) {
        is NativeArray -> {
            val (r, g, b, a) = o.also { ensureNativeArrayLength(o, 4, "colors.rgba") }
            rgbaRhino(r, g, b, a)
        }
        else -> when {
            o is String && o.trim().startsWith("#") -> {
                toIntRhino(toFullHexRhino(o).replace(Regex("^\\s*(#)(\\w{6})(\\w{2}\\s*$)"), "$1$3$2"))
            }
            else -> argbRhino(toFullHexRhino(o))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hsv(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> hsvRhino(it[0])
            3 -> hsvRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.hsv")
        }
    }

    /**
     * Parameters
     * * h: `ColorComponent`
     * * s: `ColorComponent`
     * * v: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hsvRhino(h: Any?, s: Any?, v: Any?): Int {
        val hsvComponents = floatArrayOf(parseHueComponent(h), toPercentage(s), toPercentage(v))
        return HSVToColor(hsvComponents)
    }

    /**
     * Parameters
     * * hsvComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hsvRhino(hsvComponents: Any?): Int {
        if (hsvComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsv\" must be a JavaScript Array for colors.hsv(hsv)")
        val (h, s, v) = hsvComponents.also { ensureNativeArrayLength(it, 3, "colors.hsv") }
        return hsvRhino(h, s, v)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hsva(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> hsvaRhino(it[0])
            2 -> hsvaRhino(it[0], it[1])
            4 -> hsvaRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.hsva")
        }
    }

    /**
     * Parameters
     * * h: `ColorComponent`
     * * s: `ColorComponent`
     * * v: `ColorComponent`
     * * a: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hsvaRhino(h: Any?, s: Any?, v: Any?, a: Any?): Int {
        val hsvComponents = floatArrayOf(parseHueComponent(h), toPercentage(s), toPercentage(v))
        return HSVToColor(parseDoubleComponent(a), hsvComponents)
    }

    /**
     * Parameters
     * * hsvComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     * * alpha: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hsvaRhino(hsvComponents: Any?, alpha: Any?): Int {
        if (hsvComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsv\" must be a JavaScript Array for colors.hsva(hsv, alpha)")
        val (r, g, b) = hsvComponents.also { ensureNativeArrayLength(it, 3, "colors.hsva") }
        return hsvaRhino(r, g, b, alpha)
    }

    /**
     * Parameters
     * * hsvaComponents: `[ColorComponent, ColorComponent, ColorComponent, ColorComponent]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hsvaRhino(hsvaComponents: Any?): Int {
        if (hsvaComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsva\" must be a JavaScript Array for colors.hsva(hsva)")
        val (h, s, v, a) = hsvaComponents.also { ensureNativeArrayLength(it, 4, "colors.hsva") }
        return hsvaRhino(h, s, v, a)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hsl(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> hslRhino(it[0])
            3 -> hslRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.hsl")
        }
    }

    /**
     * Parameters
     * * h: `ColorComponent`
     * * s: `ColorComponent`
     * * l: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hslRhino(h: Any?, s: Any?, l: Any?): Int {
        val hslComponents = floatArrayOf(parseHueComponent(h), toPercentage(s), toPercentage(l))
        return HSLToColor(hslComponents)
    }

    /**
     * Parameters
     * * hslComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hslRhino(hslComponents: Any?): Int {
        if (hslComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsl\" must be a JavaScript Array for colors.hsl(hsl)")
        val (h, s, l) = hslComponents.also { ensureNativeArrayLength(it, 3, "colors.hsl") }
        return hslRhino(h, s, l)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hsla(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> hslaRhino(it[0])
            2 -> hslaRhino(it[0], it[1])
            4 -> hslaRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.hsla")
        }
    }

    /**
     * Parameters
     * * h: `ColorComponent`
     * * s: `ColorComponent`
     * * l: `ColorComponent`
     * * a: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hslaRhino(h: Any?, s: Any?, l: Any?, a: Any?): Int {
        val cInt = hslRhino(h, s, l)
        return rgbaRhino(redRhino(cInt), greenRhino(cInt), blueRhino(cInt), parseDoubleComponent(a))
    }

    /**
     * Parameters
     * * hslComponents: `[ColorComponent, ColorComponent, ColorComponent]`
     * * alpha: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hslaRhino(hslComponents: Any?, alpha: Any?): Int {
        if (hslComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsl\" must be a JavaScript Array for colors.hsla(hsl, alpha)")
        val (r, g, b) = hslComponents.also { ensureNativeArrayLength(it, 3, "colors.hsla") }
        return hslaRhino(r, g, b, alpha)
    }

    /**
     * Parameters
     * * hslaComponents: `[ColorComponent, ColorComponent, ColorComponent, ColorComponent]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun hslaRhino(hslaComponents: Any?): Int {
        if (hslaComponents !is NativeArray) throw WrappedIllegalArgumentException("Components argument \"hsla\" must be a JavaScript Array for colors.hsla(hsla)")
        val (h, s, l, a) = hslaComponents.also { ensureNativeArrayLength(it, 4, "colors.hsla") }
        return hslaRhino(h, s, l, a)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRgb(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> toRgbRhino(it[0])
            3 -> toRgbRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.rgb")
        }.toNativeArray()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toRgbRhino(color: Any?): List<Double> {
        return listOf(redRhino(color), greenRhino(color), blueRhino(color))
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toRgbRhino(r: Any?, g: Any?, b: Any?): List<Double> {
        val color = rgbRhino(r, g, b)
        return listOf(redRhino(color), greenRhino(color), blueRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRgba(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toRgbaRhino(colorArg, optionsArg).toNativeArray()
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun toRgbaRhino(color: Any?, options: Any? = null): List<Double> {
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val newOptions = newNativeObject(getDeclarationScope(niceOptions)).apply { put("max", this, niceOptions.prop("maxAlpha")) }
        return listOf(redRhino(color), greenRhino(color), blueRhino(color), alphaRhino(color, newOptions))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toArgb(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) {
        val (colorArg, optionsArg) = it
        toArgbRhino(colorArg, optionsArg).toNativeArray()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toArgbRhino(color: Any?, options: Any? = null): List<Double> {
        val niceOptions = options as? NativeObject ?: newNativeObject()
        val newOptions = newNativeObject(getDeclarationScope(niceOptions)).apply { put("max", this, niceOptions.prop("maxAlpha")) }
        return listOf(alphaRhino(color, newOptions), redRhino(color), greenRhino(color), blueRhino(color))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsv(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> toHsvRhino(it[0])
            2 -> toHsvRhino(it[0], it[1])
            3 -> toHsvRhino(it[0], it[1], it[2])
            4 -> toHsvRhino(it[0], it[1], it[2], it[3])
            else -> throw ShouldNeverHappenException()
        }.toNativeArray()
    }

    /**
     * Parameters
     * * color: `OmniColor`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvRhino(color: Any?): List<Double> {
        return toHsvRhino(rgbRhino(color), FloatArray(3))
    }

    /**
     * Parameters
     * * color: `OmniColor`
     * * hsvResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvRhino(color: Any?, hsvResultContainer: Any?): List<Double> {
        require(hsvResultContainer is FloatArray) { "Container argument \"hsvResultContainer\" must be a Java Float Array for colors.toHsv(r, g, b, hsvResultContainer)" }
        AndroidColor.RGBToHSV(
            redRhino(color).roundToInt(),
            greenRhino(color).roundToInt(),
            blueRhino(color).roundToInt(),
            hsvResultContainer,
        )
        return hsvResultContainer.map { it.toDouble() }
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvRhino(r: Any?, g: Any?, b: Any?): List<Double> {
        return toHsvRhino(rgbRhino(r, g, b), FloatArray(3))
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     * * hsvResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvRhino(r: Any?, g: Any?, b: Any?, hsvResultContainer: Any?): List<Double> {
        require(hsvResultContainer is FloatArray) { "Container argument \"hsvResultContainer\" must be a Java Float Array for colors.toHsv(r, g, b, hsvResultContainer)" }
        return toHsvRhino(rgbRhino(r, g, b), hsvResultContainer)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsva(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..5) {
        when (it.size) {
            1 -> toHsvaRhino(it[0])
            2 -> toHsvaRhino(it[0], it[1])
            4 -> toHsvaRhino(it[0], it[1], it[2], it[3])
            5 -> toHsvaRhino(it[0], it[1], it[2], it[3], it[4])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.toHsva")
        }.toNativeArray()
    }

    /**
     * Parameters
     * * color: `OmniColor`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvaRhino(color: Any?): List<Double> {
        return toHsvaRhino(color, FloatArray(4))
    }

    /**
     * Parameters
     * * color: `OmniColor`
     * * hsvaResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvaRhino(color: Any?, hsvaResultContainer: Any?): List<Double> {
        require(hsvaResultContainer is FloatArray) { "Container argument \"hsvaResultContainer\" must be a Java Float Array for colors.toHsva(color, hsvResultContainer)" }
        val r = redRhino(color)
        val g = greenRhino(color)
        val b = blueRhino(color)
        val a = alphaRhino(color)
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r.roundToInt(), g.roundToInt(), b.roundToInt(), hsv)
        val hsva = hsv + toDoubleComponent(a).toFloat()
        hsva.forEachIndexed { index, fl -> hsvaResultContainer[index] = fl }
        return hsva.map { it.toDouble() }
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     * * a: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvaRhino(r: Any?, g: Any?, b: Any?, a: Any?): List<Double> {
        return toHsvaRhino(rgbaRhino(r, g, b, a), FloatArray(4))
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     * * a: `ColorComponent`
     * * hsvaResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHsvaRhino(r: Any?, g: Any?, b: Any?, a: Any?, hsvaResultContainer: Any?): List<Double> {
        require(hsvaResultContainer is FloatArray) { "Container argument \"hsvaResultContainer\" must be a Java Float Array for colors.toHsva(r, g, b, a, hsvResultContainer)" }
        return toHsvaRhino(rgbaRhino(r, g, b, a), hsvaResultContainer)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsl(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> toHslRhino(it[0])
            2 -> toHslRhino(it[0], it[1])
            3 -> toHslRhino(it[0], it[1], it[2])
            4 -> toHslRhino(it[0], it[1], it[2], it[3])
            else -> throw ShouldNeverHappenException()
        }.toNativeArray()
    }

    /**
     * Parameters
     * * color: `OmniColor`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslRhino(color: Any?): List<Double> {
        return toHslRhino(rgbRhino(color), FloatArray(3))
    }

    /**
     * Parameters
     * * color: `OmniColor`
     * * hslResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslRhino(color: Any?, hslResultContainer: Any?): List<Double> {
        require(hslResultContainer is FloatArray) { "Container argument \"hslResultContainer\" must be a Java Float Array for colors.toHsl(r, g, b, hslResultContainer)" }
        RGBToHSL(
            redRhino(color).roundToInt(),
            greenRhino(color).roundToInt(),
            blueRhino(color).roundToInt(),
            hslResultContainer
        )
        return hslResultContainer.map { it.toDouble() }
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslRhino(r: Any?, g: Any?, b: Any?): List<Double> {
        return toHslRhino(rgbRhino(r, g, b), FloatArray(3))
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     * * hslResultContainer: `java.lang.Float[]`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslRhino(r: Any?, g: Any?, b: Any?, hslResultContainer: Any?): List<Double> {
        require(hslResultContainer is FloatArray) { "Container argument \"hslResultContainer\" must be a Java Float Array for colors.toHsl(r, g, b, hslResultContainer)" }
        return toHslRhino(rgbRhino(r, g, b), hslResultContainer)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsla(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..4) {
        when (it.size) {
            1 -> toHslaRhino(it[0])
            4 -> toHslaRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.toHsla")
        }.toNativeArray()
    }

    /**
     * Parameters
     * * color: `OmniColor`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslaRhino(color: Any?): List<Double> {
        return toHslaRhino(redRhino(color), greenRhino(color), blueRhino(color), alphaRhino(color))
    }

    /**
     * Parameters
     * * r: `ColorComponent`
     * * g: `ColorComponent`
     * * b: `ColorComponent`
     * * a: `ColorComponent`
     */
    @JvmStatic
    @RhinoFunctionBody
    fun toHslaRhino(r: Any?, g: Any?, b: Any?, a: Any?): List<Double> {
        val (h, s, l) = toHslRhino(rgbRhino(r, g, b))
        return listOf(h, s, l, toDoubleComponent(a ?: 1))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isSimilar(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 2..4) {
        when (it.size) {
            2 -> isSimilarRhino(it[0], it[1])
            3 -> isSimilarRhino(it[0], it[1], it[2])
            4 -> isSimilarRhino(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.isSimilar")
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isSimilarRhino(colorA: Any?, colorB: Any?): Boolean {
        return isSimilarRhino(colorA, colorB, DEFAULT_COLOR_THRESHOLD, DEFAULT_COLOR_ALGORITHM)
    }

    @Suppress("UnnecessaryVariable")
    @JvmStatic
    @RhinoFunctionBody
    fun isSimilarRhino(colorA: Any?, colorB: Any?, thresholdOrOptions: Any?): Boolean {
        return when (thresholdOrOptions) {
            is NativeObject -> {
                val options = thresholdOrOptions
                val threshold = when {
                    "threshold" in options -> {
                        require("similarity" !in options) {
                            "Properties threshold and similarity cannot be specified at the same time"
                        }
                        val threshold = Numberx.parseAnyRhino(options.prop("threshold"))
                        require(!threshold.isNaN()) {
                            "Property threshold must be a number or number-like type"
                        }
                        threshold.roundToInt()
                    }
                    "similarity" in options -> {
                        val similarity = Numberx.parseAnyRhino(options.prop("similarity"))
                        require(!similarity.isNaN()) {
                            "Property similarity must be a number or number-like type"
                        }
                        ((1 - similarity) * 255).roundToInt()
                    }
                    else -> DEFAULT_COLOR_THRESHOLD
                }
                isSimilarRhino(colorA, colorB, threshold, options.prop("algorithm"))
            }
            else -> isSimilarRhino(colorA, colorB, thresholdOrOptions, DEFAULT_COLOR_ALGORITHM)
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isSimilarRhino(colorA: Any?, colorB: Any?, threshold: Any?, algorithm: Any?): Boolean {
        return ColorDetector.get(
            toIntRhino(colorA),
            if (algorithm.isJsNullish()) DEFAULT_COLOR_ALGORITHM else Context.toString(algorithm),
            Numberx.parseAnyRhino(threshold).takeIf { !it.isNaN() }?.roundToInt() ?: DEFAULT_COLOR_THRESHOLD,
        ).detectColor(
            redRhino(colorB).roundToInt(),
            greenRhino(colorB).roundToInt(),
            blueRhino(colorB).roundToInt(),
        )
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isEqual(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 2..3) {
        when (it.size) {
            2 -> isEqualRhino(it[0], it[1])
            3 -> isEqualRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.isEqual")
        }
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun isEqualRhino(colorA: Any?, colorB: Any?, alphaMatters: Any? = false): Boolean = when {
        alphaMatters is Boolean -> when {
            alphaMatters -> toIntRhino(colorA) == toIntRhino(colorB)
            else -> rgbRhino(colorA) == rgbRhino(colorB)
        }
        alphaMatters.isJsNullish() -> isEqualRhino(colorA, colorB, false)
        else -> throw WrappedIllegalArgumentException("Invalid argument alphaMatters (value: ${Context.toString(alphaMatters)}, species: ${alphaMatters.jsSpecies()}) for colors.isEqual")
    }

    /**
     * Distance between two colors measured by a color detection algorithm, on the 0..255 threshold scale,
     * so that `colors.isSimilar(a, b, threshold, algorithm)` is equivalent to `colors.distance(a, b, algorithm) <= threshold`.
     *
     * zh-CN: 使用颜色检测算法度量两个颜色的距离 (0..255, 与阈值同量纲),
     * 因此 `colors.isSimilar(a, b, threshold, algorithm)` 等价于 `colors.distance(a, b, algorithm) <= threshold`.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun distance(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 2..3) {
        val (colorA, colorB, algorithm) = it
        distanceRhino(colorA, colorB, algorithm)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun distanceRhino(colorA: Any?, colorB: Any?, algorithm: Any? = null): Double {
        val name = when {
            algorithm.isJsNullish() -> DEFAULT_COLOR_ALGORITHM
            algorithm is NativeObject -> algorithm.inquire("algorithm") { Context.toString(it) } ?: DEFAULT_COLOR_ALGORITHM
            else -> Context.toString(algorithm)
        }
        return ColorDetector.get(toIntRhino(colorA), name, 0).distance(
            redRhino(colorB).roundToInt(),
            greenRhino(colorB).roundToInt(),
            blueRhino(colorB).roundToInt(),
        )
    }

    /**
     * Inverts the RGB channels of a color, keeping its alpha channel.
     * zh-CN: 反转颜色的 RGB 通道, 保留 alpha 通道.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun invert(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        invertRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun invertRhino(color: Any?): Int = toIntRhino(color) xor 0x00FFFFFF

    /**
     * Blends two colors (including their alpha channels); `ratio` is the weight of the second color (0..1 or a percentage).
     * zh-CN: 混合两个颜色 (含 alpha 通道); `ratio` 为第二个颜色的权重 (0..1 或百分比).
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun blend(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 2..3) {
        val (colorA, colorB, ratio) = it
        blendRhino(colorA, colorB, ratio)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun blendRhino(colorA: Any?, colorB: Any?, ratio: Any? = null): Int {
        val weight = when {
            ratio.isJsNullish() -> 0.5
            else -> Numberx.parseAnyRhino(ratio).also {
                require(!it.isNaN() && it in 0.0..1.0) { "Argument \"ratio\" ${ratio.jsBrief()} for colors.blend must be a number in range 0..1 or a percentage" }
            }
        }
        return blendARGB(toIntRhino(colorA), toIntRhino(colorB), weight.toFloat())
    }

    /**
     * WCAG contrast ratio (1..21) between a foreground color and an opaque background color.
     * zh-CN: 前景色与不透明背景色之间的 WCAG 对比度 (1..21).
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun contrast(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
        val (foreground, background) = it
        contrastRhino(foreground, background)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun contrastRhino(foreground: Any?, background: Any?): Double {
        val backgroundInt = toIntRhino(background)
        require(alphaRhino(backgroundInt) == 255.0) {
            "Argument \"background\" ${background.jsBrief()} for colors.contrast must be an opaque color"
        }
        return calculateContrast(toIntRhino(foreground), backgroundInt)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toColorStateList(args: Array<out Any?>) = ensureArgumentsAtLeast(args, 1) {
        toColorStateListRhino(*it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toColorStateListRhino(vararg colors: Any?) = when (colors.size) {
        1 -> ColorStateList.valueOf(toIntRhino(colors[0]))
        else -> ColorStateList(arrayOf(intArrayOf()), colors.map { toIntRhino(it) }.toIntArray())
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun setPaintColor(args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
        val (paint, color) = it
        setPaintColorRhino(paint, color)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun setPaintColorRhino(paint: Any?, color: Any?) {
        require(paint is Paint) { "Argument \"paint\" ${paint.jsBrief()} for colors.setPaintColor must be a Paint" }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val (a, r, g, b) = toArgbRhino(color).map { it.roundToInt() }
                paint.setARGB(a, r, g, b)
            }
            else -> paint.setColor(toIntRhino(color))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun luminance(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        luminanceRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun luminanceRhino(color: Any?): Double = calculateLuminance(toIntRhino(color))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun build(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 0..4) {
        Color.invoke(*it)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun summary(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) {
        summaryRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun summaryRhino(color: Any?): String = when (alphaDoubleRhino(color)) {
        1.0 -> "Color { ${toHexRhino(color)} | ${toRgbStringRhino(color)} | ${toHslStringRhino(color)} | ${toHsvStringRhino(color)} | int(${toIntRhino(color)}) }"
        else -> "Color { ${toHexRhino(color)} | ${toRgbaString(arrayOf(color))} | ${toHslaString(arrayOf(color))} | ${toHsvaString(arrayOf(color))} | int(${toIntRhino(color)}) }"
    }

    // Perceptual color functions (roadmap P6.1). zh-CN: 感知色彩函数 (Roadmap P6.1).

    /**
     * OKLab components `[L, a, b]` of a color: L in 0..1, a and b about -0.4..0.4.
     * zh-CN: 颜色的 OKLab 分量 `[L, a, b]`: L 取值 0..1, a 与 b 约为 -0.4..0.4.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toOklab(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
        toOklabRhino(it).toNativeArray()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toOklabRhino(color: Any?): List<Double> = OkColor.toOkLab(toIntRhino(color)).let { listOf(it.l, it.a, it.b) }

    /**
     * A color from OKLab components (or one array of them), with an optional alpha. Values outside sRGB keep their
     * lightness and hue and lose chroma.
     * zh-CN: 由 OKLab 分量 (或分量数组) 及可选的 alpha 构造颜色. 超出 sRGB 的值保持亮度与色相, 降低彩度.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun oklab(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..4) {
        val (l, a, b, alpha) = perceptualComponents(it, "colors.oklab")
        oklabRhino(l, a, b, alpha)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun oklabRhino(l: Any?, a: Any?, b: Any?, alpha: Any? = null): Int {
        val lab = OkLab(parseLightness(l, "colors.oklab"), parseFinite(a, "a", "colors.oklab"), parseFinite(b, "b", "colors.oklab"))
        val color = if (OkColor.isInGamut(lab)) OkColor.fromOkLab(lab) else OkColor.fromOkLch(OkColor.toOkLch(lab))
        return withAlphaComponent(color, alpha)
    }

    /**
     * OKLCH components `[L, C, h]` of a color: L in 0..1, chroma C from 0 (about 0.37 at most in sRGB), hue h in degrees.
     * zh-CN: 颜色的 OKLCH 分量 `[L, C, h]`: L 取值 0..1, 彩度 C 不小于 0 (sRGB 中至多约 0.37), 色相 h 以度为单位.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toOklch(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
        toOklchRhino(it).toNativeArray()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toOklchRhino(color: Any?): List<Double> = OkColor.toOkLch(toIntRhino(color)).let { listOf(it.l, it.c, it.h) }

    /**
     * A color from OKLCH components (or one array of them), with an optional alpha; chroma is reduced into sRGB.
     * zh-CN: 由 OKLCH 分量 (或分量数组) 及可选的 alpha 构造颜色; 彩度会降低到 sRGB 范围内.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun oklch(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..4) {
        val (l, c, h, alpha) = perceptualComponents(it, "colors.oklch")
        oklchRhino(l, c, h, alpha)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun oklchRhino(l: Any?, c: Any?, h: Any?, alpha: Any? = null): Int {
        val chroma = parseFinite(c, "C", "colors.oklch").also {
            require(it >= 0.0) { "Argument \"C\" ${c.jsBrief()} for colors.oklch must not be negative" }
        }
        val lch = OkLch(parseLightness(l, "colors.oklch"), chroma, OkColor.normalizeHue(parseFinite(h, "h", "colors.oklch")))
        return withAlphaComponent(OkColor.fromOkLch(lch), alpha)
    }

    /**
     * The color nearest to [color] in lightness (same hue) that reaches the contrast [ratio] against an opaque
     * background; the default ratio 4.5 is WCAG AA for text. The alpha of [color] is kept.
     * zh-CN: 与 [color] 同色相, 亮度最接近且与不透明背景的对比度达到 [ratio] 的颜色; 默认 4.5 为 WCAG AA 文本标准. 保留 [color] 的 alpha.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureContrast(args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 2..3) {
        val (color, background, ratio) = it
        ensureContrastRhino(color, background, ratio)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun ensureContrastRhino(color: Any?, background: Any?, ratio: Any? = null): Int = ensureContrastOf(toIntRhino(color), toIntRhino(background), ratio)

    internal fun ensureContrastOf(color: Int, background: Int, ratio: Any? = null): Int {
        require(background ushr 24 == 0xFF) {
            "Argument \"background\" ${String.format("#%08X", background)} for colors.ensureContrast must be an opaque color"
        }
        val target = when {
            ratio.isJsNullish() -> DEFAULT_CONTRAST_RATIO
            else -> parseFinite(ratio, "ratio", "colors.ensureContrast").also {
                require(it in 1.0..21.0) { "Argument \"ratio\" ${ratio.jsBrief()} for colors.ensureContrast must be in range 1..21" }
            }
        }
        val result = ThemeColorDerivation.ensureContrast(color or OPAQUE, background, target)
        return (result and 0xFFFFFF) or (color and OPAQUE)
    }

    /**
     * Harmony colors of [type] (`complementary`, `analogous`, `triadic`, `splitComplementary` or `tetradic`), the
     * color itself first, by rotating the OKLCH hue.
     * zh-CN: [type] 类型 (`complementary`, `analogous`, `triadic`, `splitComplementary` 或 `tetradic`) 的和谐色,
     * 首个为颜色本身, 通过旋转 OKLCH 色相得到.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun harmony(args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
        val (color, type) = it
        harmonyRhino(color, type).toNativeArray()
    }

    @JvmStatic
    @RhinoFunctionBody
    fun harmonyRhino(color: Any?, type: Any?): List<Int> = harmonyOf(toIntRhino(color), type)

    internal fun harmonyOf(color: Int, type: Any?): List<Int> {
        val harmony = ColorHarmony.Type.of(if (type.isJsNullish()) null else Context.toString(type))
            ?: throw WrappedIllegalArgumentException(
                "Argument \"type\" ${type.jsBrief()} for colors.harmony must be one of: ${ColorHarmony.Type.entries.joinToString(", ") { it.key }}"
            )
        return ColorHarmony.harmony(color, harmony)
    }

    /**
     * [count] tones of the color's hue from light to dark (10 by default, 2..20).
     * zh-CN: 与颜色同色相的 [count] 级色阶, 由浅到深 (默认 10 级, 2..20).
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tones(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) {
        val (color, count) = it
        tonesRhino(color, count).toNativeArray()
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun tonesRhino(color: Any?, count: Any? = null): List<Int> = tonesOf(toIntRhino(color), count)

    internal fun tonesOf(color: Int, count: Any? = null): List<Int> {
        val n = when {
            count.isJsNullish() -> DEFAULT_TONE_COUNT
            else -> parseFinite(count, "count", "colors.tones").let { value ->
                value.roundToInt().also {
                    require(value == it.toDouble() && it in 2..MAX_TONE_COUNT) { "Argument \"count\" ${count.jsBrief()} for colors.tones must be an integer in range 2..$MAX_TONE_COUNT" }
                }
            }
        }
        return ColorHarmony.tones(color, n)
    }

    /**
     * `{ primary, accent, dark }` derived from a primary color, as the "Derived colors" setting of the theme color does
     * on "Auto": the accent keeps 3:1 against both window backgrounds, the dark is darker in the same hue.
     * zh-CN: 由主色推导的 `{ primary, accent, dark }`, 与主题色 "派生配色" 设为 "自动" 时一致:
     * 强调色与日 / 夜窗口背景的对比度均不低于 3:1, 深色为同色相的更深颜色.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun derive(args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) {
        deriveRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun deriveRhino(color: Any?): NativeObject {
        val primary = toIntRhino(color) or OPAQUE
        val (accent, dark) = ThemeColorDerivation.accentAndDark(primary, null, null, derive = true)
        return newNativeObject().also {
            it.put("primary", it, primary)
            it.put("accent", it, accent)
            it.put("dark", it, dark)
        }
    }

    /**
     * The night counterpart of a day color: lightness pulled to about 0.25..0.5 with the hue kept.
     * zh-CN: 日间颜色在夜间的对应色: 亮度收拢到约 0.25..0.5, 保持色相.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun nightVariant(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
        nightVariantRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun nightVariantRhino(color: Any?): Int = ThemeColorDerivation.nightVariant(toIntRhino(color) or OPAQUE)

    /**
     * How a color looks with a color vision [deficiency] (`protanopia`, `deuteranopia` or `tritanopia`, Machado 2009).
     * zh-CN: 颜色在色觉缺陷 [deficiency] (`protanopia`, `deuteranopia` 或 `tritanopia`, Machado 2009) 下的样子.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun simulate(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) {
        val (color, deficiency) = it
        simulateRhino(color, deficiency)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun simulateRhino(color: Any?, deficiency: Any?): Int = simulateOf(toIntRhino(color), deficiency)

    internal fun simulateOf(color: Int, deficiency: Any?): Int {
        val key = if (deficiency.isJsNullish()) null else Context.toString(deficiency)
        val type = ColorVisionSimulation.Deficiency.entries.firstOrNull { it.key == key }
            ?: throw WrappedIllegalArgumentException(
                "Argument \"deficiency\" ${deficiency.jsBrief()} for colors.simulate must be one of: ${ColorVisionSimulation.Deficiency.entries.joinToString(", ") { it.key }}"
            )
        return ColorVisionSimulation.simulate(color, type)
    }

    /**
     * Dominant colors of an image, the most frequent first. Options: `count` (1..16, default 5), `region`
     * (as in images.findColor) and `step` (sample every n pixels in both directions; by default about 65536 samples).
     * Pixels with alpha below 128 are ignored.
     * zh-CN: 图像的主要颜色, 出现最多者在前. 选项: `count` (1..16, 默认 5), `region` (与 images.findColor 相同)
     * 与 `step` (横纵向每隔 n 个像素采样; 默认采样约 65536 个像素). 忽略 alpha 低于 128 的像素.
     */
    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun extract(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) {
        val (image, options) = it
        extractRhino(image, options).toNativeArray()
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun extractRhino(image: Any?, options: Any? = null): List<Int> {
        require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for colors.extract must be an ImageWrapper" }
        require(options.isJsNullish() || options is NativeObject) { "Argument \"options\" ${options.jsBrief()} for colors.extract must be an object" }
        val opt = options as? NativeObject
        val count = opt?.inquire("count") { value ->
            parseFinite(value, "count", "colors.extract").let { number ->
                number.roundToInt().also { require(number == it.toDouble() && it in 1..ColorQuantizer.MAX_COLORS) { "Option \"count\" ${value.jsBrief()} for colors.extract must be an integer in range 1..${ColorQuantizer.MAX_COLORS}" } }
            }
        } ?: DEFAULT_EXTRACT_COUNT
        try {
            val region = Images.buildRegionInternal(image, opt?.prop("region"))
            val step = opt?.inquire("step") { value ->
                parseFinite(value, "step", "colors.extract").let { number ->
                    number.roundToInt().also { require(number == it.toDouble() && it >= 1) { "Option \"step\" ${value.jsBrief()} for colors.extract must be a positive integer" } }
                }
            } ?: ceil(sqrt(region.width.toDouble() * region.height / EXTRACT_SAMPLES)).toInt().coerceAtLeast(1)
            val bitmap = image.bitmap
            val row = IntArray(region.width)
            val samples = ArrayList<Int>()
            var y = region.y
            while (y < region.y + region.height) {
                bitmap.getPixels(row, 0, region.width, region.x, y, region.width, 1)
                var x = 0
                while (x < region.width) {
                    samples += row[x]
                    x += step
                }
                y += step
            }
            return ColorQuantizer.quantize(samples.toIntArray(), count).map { it.color }
        } finally {
            image.shoot()
        }
    }

    /** `[c1, c2, c3]` or `[c1, c2, c3, alpha]`, given as one array or as separate arguments. zh-CN: 以单个数组或分别给出的 `[c1, c2, c3]` 或 `[c1, c2, c3, alpha]`. */
    private fun perceptualComponents(args: Array<out Any?>, name: String): List<Any?> {
        val components: List<Any?> = when (args.size) {
            1 -> (args[0] as? NativeArray)?.toList()
                ?: throw WrappedIllegalArgumentException("Components argument ${args[0].jsBrief()} for $name must be a JavaScript Array")
            else -> args.toList()
        }
        require(components.size in 3..4) { "$name needs 3 components and an optional alpha instead of ${components.size} values" }
        return components + listOf(null)
    }

    private fun parseFinite(value: Any?, label: String, name: String): Double {
        val number = Numberx.parseAnyRhino(value)
        require(!number.isNaN() && !number.isInfinite()) { "Argument \"$label\" ${value.jsBrief()} for $name must be a finite number" }
        return number
    }

    /** Lightness in 0..1, also given as a percentage such as "50%". zh-CN: 0..1 的亮度, 也可写作 "50%" 这样的百分比. */
    private fun parseLightness(value: Any?, name: String): Double = parseFinite(value, "L", name).also {
        require(it in 0.0..1.0) { "Argument \"L\" ${value.jsBrief()} for $name must be in range 0..1" }
    }

    private fun withAlphaComponent(color: Int, alpha: Any?): Int = when {
        alpha.isJsNullish() -> color or OPAQUE
        else -> (color and 0xFFFFFF) or (parseDoubleComponent(alpha) shl 24)
    }

    internal fun parseRelativePercentage(percentage: Any?): Double {
        return Numberx.parseAnyRhino(percentage).also {
            require(it >= 0.0) { "Relative percentage must be in range 0..255, instead of $percentage" }
        }
    }

    private fun toDoubleComponent(component: Any): Double = toDouble(component, 255)

    @Suppress("SameParameterValue")
    private fun toDouble(component: Any, by: Int): Double {
        if (component !is Number) {
            throw WrappedIllegalArgumentException("Argument o must be of type number")
        }
        return when (val niceComponent = component.toDouble()) {
            in 0.0..1.0 -> niceComponent
            in 1.0..by.toDouble() -> niceComponent / by
            else -> throw WrappedIllegalArgumentException("Argument o must be in the range 0..255")
        }
    }

    internal fun toUnit8RgbList(components: Array<Any?>): List<Int> {
        val compList = toComponents(components)
        // Components must stay unrounded here: a fraction such as 0.5 means 50% and rounding it first would turn it into 1.
        // zh-CN: 此处分量不能提前取整: 0.5 这样的小数表示 50%, 先取整会把它变成 1.
        val isPercentNums = compList.all { it <= 1.0 } && compList.any { it != 1.0 }
        return when {
            isPercentNums -> compList.map { if (it == 1.0) 255 else toUnit8(it) }
            else -> compList.map { toUnit8(it) }
        }
    }

    private fun toUnit8(o: Any): Int {
        when (o) {
            is Number -> {
                val num = o.toDouble()
                if (num >= 1) {
                    return minOf(255, num.roundToInt())
                }
                if (num < 0) {
                    throw WrappedIllegalArgumentException("Number should not be negative.")
                }
                return (num * 255.0).roundToInt()
            }
            else -> {
                val num = o.toString().toDoubleOrNull()
                if (num != null) {
                    return toUnit8(num)
                }
                throw WrappedIllegalArgumentException("Argument o ($o) cannot be parsed as a number")
            }
        }
    }

    private fun toComponents(components: Array<Any?>): List<Double> {
        return components.map { comp ->
            val num = Numberx.parseAnyRhino(comp)
            require(!num.isNaN()) { "Cannot convert $comp into a color component" }
            num
        }
    }

    private fun toPercentage(o: Any?): Float {
        val x = Numberx.parseAnyRhino(o)
        if (x.isNaN()) throw WrappedIllegalArgumentException("Argument $o cannot be converted to a percentage object")
        if (x < 0) throw WrappedIllegalArgumentException("Number to be converted into a percentage must be positive")
        if (x > 100) throw WrappedIllegalArgumentException("Number to be converted into a percentage must be less than 100")
        return when {
            x <= 1.0 -> x
            else -> x / 100
        }.toFloat()
    }

    private fun parseDoubleComponent(component: Any?): Int {
        if (component !is Number) throw WrappedIllegalArgumentException("Component is not a number")
        return if (component.toDouble() == 1.0) 255 else toUnit8(component)
    }

    private fun parseHueComponent(o: Any?): Float {
        var x = Numberx.parseAnyRhino(o)
        if (x.isNaN()) throw WrappedIllegalArgumentException("Argument $o cannot be converted to a hue component")
        if (abs(x) < 1.0) x *= 360
        return Numberx.clampToRhino(x, listOf(0, 360), 360).toFloat()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRgbString(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> toRgbStringRhino(it[0])
            3 -> toRgbStringRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.toRgbString")
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toRgbStringRhino(color: Any?): String {
        return toRgbRhino(color).joinToString(", ", prefix = "rgb(", postfix = ")") {
            it.roundToInt().coerceIn(0..255).toString()
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toRgbStringRhino(r: Any?, g: Any?, b: Any?): String {
        return toRgbRhino(r, g, b).joinToString(", ", prefix = "rgb(", postfix = ")") {
            it.roundToInt().coerceIn(0..255).toString()
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRgbaString(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) { argList ->
        val (color, options) = argList

        val keepTrailingZeroForFullAlpha = when (options) {
            is NativeObject -> options.inquire<Boolean>("keepTrailingZeroForFullAlpha", ::coerceBoolean) != false
            is Boolean -> options
            else -> true
        }

        val list = listOf(redRhino(color), greenRhino(color), blueRhino(color)).map {
            it.roundToInt().coerceIn(0..255).toString()
        } + alphaDoubleRhino(color).roundToAlphaString(keepTrailingZeroForFullAlpha = keepTrailingZeroForFullAlpha)

        list.joinToString(", ", prefix = "rgba(", postfix = ")")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toArgbString(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) { argList ->
        val (color, options) = argList

        val keepTrailingZeroForFullAlpha = when (options) {
            is NativeObject -> options.inquire<Boolean>("keepTrailingZeroForFullAlpha", ::coerceBoolean) != false
            is Boolean -> options
            else -> true
        }

        val list = listOf(
            alphaDoubleRhino(color).roundToAlphaString(keepTrailingZeroForFullAlpha = keepTrailingZeroForFullAlpha)
        ) + listOf(redRhino(color), greenRhino(color), blueRhino(color)).map {
            it.roundToInt().coerceIn(0..255).toString()
        }

        list.joinToString(", ", prefix = "argb(", postfix = ")")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsvString(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> toHsvStringRhino(it[0])
            3 -> toHsvStringRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.toHsvString")
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toHsvStringRhino(color: Any?): String {
        val (r, g, b) = toRgbRhino(color)
        return toHsvStringRhino(r, g, b)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toHsvStringRhino(r: Any?, g: Any?, b: Any?): String {
        val (hue, saturation, value) = toHsvRhino(r, g, b)
        return listOf(
            hue.roundToHueString(),
            saturation.roundToSaturationString(),
            value.roundToValueString(),
        ).joinToString(", ", prefix = "hsv(", postfix = ")")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHsvaString(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) { argList ->
        val (color, options) = argList
        val (hue, saturation, value) = toHsvRhino(color)

        val keepTrailingZeroForFullAlpha = when (options) {
            is NativeObject -> options.inquire<Boolean>("keepTrailingZeroForFullAlpha", ::coerceBoolean) != false
            is Boolean -> options
            else -> true
        }

        val list = listOf(
            hue.roundToHueString(),
            saturation.roundToSaturationString(),
            value.roundToValueString(),
        ) + alphaDoubleRhino(color).roundToAlphaString(keepTrailingZeroForFullAlpha = keepTrailingZeroForFullAlpha)

        list.joinToString(", ", prefix = "hsva(", postfix = ")")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHslString(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) {
        when (it.size) {
            1 -> toHslStringRhino(it[0])
            3 -> toHslStringRhino(it[0], it[1], it[2])
            else -> throw WrappedIllegalArgumentException("Invalid arguments ${it.jsArrayBrief()} for colors.toHslString")
        }
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toHslStringRhino(color: Any?): String {
        val (r, g, b) = toRgbRhino(color)
        return toHslStringRhino(r, g, b)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toHslStringRhino(r: Any?, g: Any?, b: Any?): String {
        val (hue, saturation, lightness) = toHslRhino(r, g, b)
        return listOf(
            hue.roundToHueString(),
            saturation.roundToSaturationString(),
            lightness.roundToValueString(),
        ).joinToString(", ", prefix = "hsl(", postfix = ")")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toHslaString(args: Array<out Any?>): String = ensureArgumentsAtMost(args, 2) { argList ->
        val (color, options) = argList
        val (hue, saturation, lightness) = toHslRhino(color)

        val keepTrailingZeroForFullAlpha = when (options) {
            is NativeObject -> options.inquire<Boolean>("keepTrailingZeroForFullAlpha", ::coerceBoolean) != false
            is Boolean -> options
            else -> true
        }

        val list = listOf(
            hue.roundToHueString(),
            saturation.roundToSaturationString(),
            lightness.roundToValueString(),
        ) + alphaDoubleRhino(color).roundToAlphaString(keepTrailingZeroForFullAlpha = keepTrailingZeroForFullAlpha)

        list.joinToString(", ", prefix = "hsla(", postfix = ")")
    }

}
