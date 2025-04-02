package org.autojs.autojs.runtime.api.augment.colors

import android.content.res.ColorStateList
import android.graphics.Color.HSVToColor
import android.graphics.Paint
import android.os.Build
import androidx.core.graphics.ColorUtils.HSLToColor
import androidx.core.graphics.ColorUtils.RGBToHSL
import androidx.core.graphics.ColorUtils.calculateLuminance
import org.autojs.autojs.annotation.AugmentableSimpleGetterProxyInterface
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.extension.ArrayExtensions.jsArrayBrief
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.extension.FlexibleArray.Companion.component1
import org.autojs.autojs.extension.FlexibleArray.Companion.component2
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.SimpleGetterProxy
import org.autojs.autojs.runtime.api.augment.jsox.Numberx
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ColorUtils.roundToAlphaString
import org.autojs.autojs.util.ColorUtils.roundToHueString
import org.autojs.autojs.util.ColorUtils.roundToSaturationString
import org.autojs.autojs.util.ColorUtils.roundToValueString
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.ensureNativeArrayLength
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Suppress("unused", "UNUSED_PARAMETER")
object Colors : Augmentable(), SimpleGetterProxy {

    @JvmField
    @Suppress("MayBeConstant")
    val DEFAULT_COLOR_THRESHOLD = 4

    @JvmField
    @Suppress("MayBeConstant")
    val DEFAULT_COLOR_ALGORITHM = "diff"

    private val colorTables = arrayOf(
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

    override val selfAssignmentFunctions = listOf(
        "toString",
        ::toInt.name,
        ::toHex.name,
        ::toFullHex.name,
        ::alpha.name,
        ::getAlpha.name,
        ::alphaDouble.name,
        ::getAlphaDouble.name,
        ::setAlpha.name,
        ::setAlphaRelative.name,
        ::removeAlpha.name,
        ::red.name,
        ::getRed.name,
        ::redDouble.name,
        ::getRedDouble.name,
        ::setRed.name,
        ::setRedRelative.name,
        ::removeRed.name,
        ::green.name,
        ::getGreen.name,
        ::greenDouble.name,
        ::getGreenDouble.name,
        ::setGreen.name,
        ::setGreenRelative.name,
        ::removeGreen.name,
        ::blue.name,
        ::getBlue.name,
        ::blueDouble.name,
        ::getBlueDouble.name,
        ::setBlue.name,
        ::setBlueRelative.name,
        ::removeBlue.name,
        ::rgb.name,
        ::argb.name,
        ::rgba.name,
        ::hsv.name,
        ::hsva.name,
        ::hsl.name,
        ::hsla.name,
        ::toRgb.name,
        ::toRgba.name,
        ::toArgb.name,
        ::toHsv.name,
        ::toHsva.name,
        ::toHsl.name,
        ::toHsla.name,
        ::toRgbString.name,
        ::toRgbaString.name,
        ::toArgbString.name,
        ::toHsvString.name,
        ::toHsvaString.name,
        ::toHslString.name,
        ::toHslaString.name,
        ::isSimilar.name,
        ::isEqual.name,
        ::toColorStateList.name,
        ::setPaintColor.name,
        ::luminance.name,
        ::build.name,
        ::summary.name,
    )

    override val selfAssignmentProperties = colorTables.map { table ->
        table::class.java.simpleName.lowercase() to table
    }

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "all" to Supplier { all.toNativeObject() },
    )

    @JvmStatic
    @AugmentableSimpleGetterProxyInterface
    fun get(scope: Scriptable, key: String): Any? {
        for (table in colorTables) {
            // @Alter by SuperMonster003 on Jun 15, 2024.
            //  # for (member in table::class.members) {
            //  #     if (member.name == name) {
            //  #         return@ensureArgumentsOnlyOne member.call()
            //  #     }
            //  # }
            try {
                return table::class.java.getDeclaredField(key).apply {
                    isAccessible = true
                }.get(null) ?: NOT_FOUND
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return NOT_FOUND
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
        val newOptions = newNativeObject().apply { put("max", this, niceOptions.prop("maxAlpha")) }
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
        val newOptions = newNativeObject().apply { put("max", this, niceOptions.prop("maxAlpha")) }
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
            2 -> isSimilarRhino(it[0], it[1])
            3 -> isSimilarRhino(it[0], it[1], it[2])
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
        require(paint is Paint) { "Argument paint for colors.setPaintColor must be a Paint instead of ${paint?.javaClass}" }
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

    private fun toUnit8RgbList(components: Array<Any?>): List<Int> {
        val compList = toComponents(components)
        val isPercentNums = compList.all { it <= 1 } && compList.any { it != 1 }
        return when {
            isPercentNums -> compList.map { if (it == 1) 255 else toUnit8(it) }
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

    private fun toComponents(components: Array<Any?>): Array<Int> {
        return components.map { comp ->
            val num = Numberx.parseAnyRhino(comp)
            require(!num.isNaN()) { "Cannot convert $comp into a color component" }
            num.roundToInt()
        }.toTypedArray()
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