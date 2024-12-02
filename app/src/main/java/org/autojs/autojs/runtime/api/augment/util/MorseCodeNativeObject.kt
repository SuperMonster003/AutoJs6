package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoFunctionObjectBody
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.FlexibleArray.Companion.ensureArgumentsAtMost
import org.autojs.autojs.extension.FlexibleArray.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

@Suppress("unused", "UNUSED_PARAMETER")
class MorseCodeNativeObject(val parser: MorseCodeParser) : NativeObject(), StringReadable {

    private val mFunctionNames = arrayOf(
        ::getPattern.name,
        ::getCode.name,
        ::vibrate.name,
        "toString",
    )

    init {
        super.exportAsJSClass(MAX_PROTOTYPE_ID, this, false)
        defineFunctionProperties(mFunctionNames, javaClass, PERMANENT)
        defineProperty(StringReadable.KEY, newBaseFunction(StringReadable.KEY, { toStringReadable() }, NOT_CONSTRUCTABLE), PERMANENT)
        defineProperty("pattern", { parser.pattern.toNativeArray() }, null, PERMANENT)
        defineProperty("code", { parser.code }, null, PERMANENT)
    }

    @RhinoFunctionObjectBody
    override fun toStringReadable() = toStringRhino(this)

    companion object {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getPattern(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any = ensureArgumentsIsEmpty(args) {
            (thisObj as MorseCodeNativeObject).parser.pattern.toNativeArray()
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun getCode(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any = ensureArgumentsIsEmpty(args) {
            (thisObj as MorseCodeNativeObject).parser.code
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
            toStringRhino(thisObj as MorseCodeNativeObject)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun toStringRhino(thisObj: MorseCodeNativeObject): String {
            val parser = thisObj.parser
            val code = parser.code
            val pattern = parser.pattern
            return listOf(
                "${MorseCode::class.java.simpleName} {",
                "  code: '$code',",
                "  pattern: ${Util.formatRhino(pattern)},",
                "}",
            ).joinToString("\n")
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun vibrate(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any = ensureArgumentsAtMost(args, 1) {
            val (delay) = it
            (thisObj as MorseCodeNativeObject).parser.vibrate(if (!delay.isJsNullish()) Context.toNumber(delay) else 0.0)
        }

    }

}
