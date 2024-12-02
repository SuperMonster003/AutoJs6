package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

@Suppress("unused")
object MorseCode : Augmentable(), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::getPattern.name,
        ::getCode.name,
        ::vibrate.name,
    )

    override fun invoke(vararg args: Any?): NativeObject = ensureArgumentsLengthInRange(args, 1..2) {
        MorseCodeNativeObject(MorseCodeParser.buildParser(*args))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getPattern(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) {
        MorseCodeParser.buildParser(*it).pattern.toNativeArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun getCode(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
        MorseCodeParser.buildParser(*it).code
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun vibrate(args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..2) {
        val (source, delay) = it
        vibrateRhino(source, delay)
    }

    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun vibrateRhino(source: Any?, delay: Any? = null) {
        MorseCodeParser.buildParser(source).vibrate(if (!delay.isJsNullish()) Context.toNumber(delay) else 0.0)
    }

}
