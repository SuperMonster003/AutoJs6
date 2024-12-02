package org.autojs.autojs.runtime.api.augment.colors

import android.graphics.Color.BLACK
import org.autojs.autojs.annotation.RhinoFunctionObjectBody
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.extension.ArrayExtensions.unshiftWith
import org.autojs.autojs.extension.FlexibleArray.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.extension.FlexibleArray.Companion.ensureArgumentsOnlyOne
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.colors.Colors.parseRelativePercentage
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.StringUtils.uppercaseFirstChar
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.FunctionObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

@Suppress("unused", "EqualsOrHashCode", "UNUSED_PARAMETER")
class ColorNativeObject @JvmOverloads constructor(color: Any? = BLACK) : NativeObject(), StringReadable {

    init {
        super.exportAsJSClass(MAX_PROTOTYPE_ID, this, false)
    }

    private val mProxyTowardsNames by lazy {
        arrayOf("argb", "hsl", "hsla", "hsv", "hsva", "rgb", "rgba")
    }

    private val mProxySetterNames by lazy {
        arrayOf(
            "setAlpha", "setAlphaRelative", "removeAlpha",
            "setRed", "setRedRelative", "removeRed",
            "setGreen", "setGreenRelative", "removeGreen",
            "setBlue", "setBlueRelative", "removeBlue",
            "setRgb", "setRgba", "setArgb",
            "setHsv", "setHsva",
            "setHsl", "setHsla",
            "setPaintColor",
        )
    }

    @JvmField
    var color: Int = Colors.toIntRhino(color)

    constructor(r: Any?, g: Any?, b: Any?) : this(Colors.rgbRhino(r, g, b))

    constructor(r: Any?, g: Any?, b: Any?, a: Any?) : this(Colors.rgbaRhino(r, g, b, a))

    override fun has(name: String, start: Scriptable): Boolean {
        return when (name) {
            "equals", StringReadable.KEY -> true
            in mProxySetterNames -> true
            in mProxyTowardsNames.map { "to${uppercaseFirstChar(it)}" } -> true
            else -> super.has(name, start)
        }
    }

    override fun get(name: String, start: Scriptable): Any? = when (name) {
        "equals" -> newBaseFunction("equals", {
            ensureArgumentsOnlyOne(it) { other -> equals(other) }
        }, NOT_CONSTRUCTABLE)
        StringReadable.KEY -> newBaseFunction(StringReadable.KEY, {
            ensureArgumentsIsEmpty(it) { toStringReadable() }
        }, NOT_CONSTRUCTABLE)

        in mProxySetterNames -> {
            val method = Setter.javaClass.getMethod(
                name,
                Context::class.java,
                Scriptable::class.java,
                Array<Any>::class.java,
                Function::class.java,
            )
            FunctionObject(name, method, this)
        }
        else -> {
            val protoName = when (name) {
                in mProxyTowardsNames -> "to${uppercaseFirstChar(name)}"
                else -> name
            }
            try {
                val method = Colors::class.java.getMethod(protoName, Array<Any>::class.java)
                newBaseFunction(protoName, { args -> method.invoke(null, args.unshiftWith(color)) }, NOT_CONSTRUCTABLE)
            } catch (_: Exception) {
                super.get(protoName, start)
            }
        }
    }

    @RhinoFunctionObjectBody
    override fun equals(other: Any?) = Colors.isEqualRhino(color, other)

    @RhinoFunctionObjectBody
    override fun toStringReadable(): String = Colors.summaryRhino(color)

    @Suppress("unused")
    internal object Setter : Augmentable() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setAlpha(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setAlphaRhino(c.color, it)
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setRed(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setRedRhino(c.color, it)
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setGreen(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setGreenRhino(c.color, it)
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setBlue(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setBlueRhino(c.color, it)
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setAlphaRelative(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setAlphaRhino(c.color, Colors.alphaRhino(c.color) * parseRelativePercentage(it))
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setRedRelative(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setRedRhino(c.color, Colors.redRhino(c.color) * parseRelativePercentage(it))
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setGreenRelative(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setGreenRhino(c.color, Colors.greenRhino(c.color) * parseRelativePercentage(it))
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setBlueRelative(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.setBlueRhino(c.color, Colors.blueRhino(c.color) * parseRelativePercentage(it))
            return@ensureArgumentsOnlyOne c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeAlpha(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsIsEmpty(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.removeAlphaRhino(c.color)
            return@ensureArgumentsIsEmpty c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeRed(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsIsEmpty(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.removeRedRhino(c.color)
            return@ensureArgumentsIsEmpty c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeGreen(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsIsEmpty(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.removeGreenRhino(c.color)
            return@ensureArgumentsIsEmpty c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeBlue(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsIsEmpty(args) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.removeBlueRhino(c.color)
            return@ensureArgumentsIsEmpty c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setRgb(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val c = thisObj as ColorNativeObject
            c.color = when (it.size) {
                1 -> Colors.rgbRhino(it[0])
                3 -> Colors.rgbRhino(it[0], it[1], it[2])
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color#setRgb")
            }
            return@ensureArgumentsLengthInRange c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setRgba(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val c = thisObj as ColorNativeObject
            c.color = when (it.size) {
                1 -> Colors.rgbaRhino(it[0])
                4 -> Colors.rgbaRhino(it[0], it[1], it[2], it[3])
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color#setRgba")
            }
            return@ensureArgumentsLengthInRange c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setArgb(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val c = thisObj as ColorNativeObject
            c.color = when (it.size) {
                1 -> Colors.argbRhino(it[0])
                4 -> Colors.argbRhino(it[0], it[1], it[2], it[3])
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color#setArgb")
            }
            return@ensureArgumentsLengthInRange c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setHsv(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val c = thisObj as ColorNativeObject
            c.color = when (it.size) {
                1 -> Colors.hsvRhino(it[0])
                3 -> Colors.hsvRhino(it[0], it[1], it[2])
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color#setHsv")
            }
            return@ensureArgumentsLengthInRange c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setHsva(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLength(args, 4) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.hsvaRhino(it[0], it[1], it[2], it[3])
            return@ensureArgumentsLength c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setHsl(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val c = thisObj as ColorNativeObject
            c.color = when (it.size) {
                1 -> Colors.hslRhino(it[0])
                3 -> Colors.hslRhino(it[0], it[1], it[2])
                else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color#setHsl")
            }
            return@ensureArgumentsLengthInRange c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setHsla(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsLength(args, 4) {
            val c = thisObj as ColorNativeObject
            c.color = Colors.hslaRhino(it[0], it[1], it[2], it[3])
            return@ensureArgumentsLength c
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setPaintColor(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ColorNativeObject = ensureArgumentsOnlyOne(args) {
            val c = thisObj as ColorNativeObject
            Colors.setPaintColorRhino(it, c.color)
            return@ensureArgumentsOnlyOne c
        }

    }

}
