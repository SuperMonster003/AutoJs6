@file:Suppress("MayBeConstant")

package org.autojs.autojs.runtime.api.augment.formatter

import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsBoolean
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.AUTO
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.DEFAULT_BYTES_STRICT
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.IEC_DIV
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.SI_DIV
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.Tough
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.Tough.LOOSE
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.Tough.NONE
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.Tough.STRICT
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes.UNITS
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes as CoreBytes

object Bytes : Augmentable(), Invokable {

    override val selfAssignmentProperties = listOf(
        "UNITS" to UNITS,
        "AUTO" to AUTO,
        "IEC_DIV" to IEC_DIV,
        "SI_DIV" to SI_DIV,
    )

    override val selfAssignmentFunctions = listOf(
        ::strict.name,
        ::loose.name,
    )

    override fun invoke(vararg args: Any?): String = ensureArgumentsLengthInRange(args, 1..4) { call(args) }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun call(args: Array<out Any?>, tough: Tough = NONE): String = ensureArgumentsLengthInRange(args, 1..4) { argList ->
        val (arg0, arg1, arg2, arg3) = argList

        when (argList.size) {
            4 -> when {
                arg3.isJsObject() -> {
                    val opts = arg3 as ScriptableObject
                    listOf("source", "fromUnit", "toUnit", "options").forBytesString(
                        source = arg0,
                        fromUnit = opts.inquire("fromUnit", arg1),
                        toUnit = opts.inquire("toUnit", arg2),
                        useIecIdentifier = opts.inquire("useIecIdentifier"),
                        useSpace = opts.inquire("useSpace"),
                        fractionDigits = opts.inquire("fractionDigits"),
                        trimTrailingZero = opts.inquire("trimTrailingZero"),
                        autoCarryThreshold = opts.inquire("autoCarryThreshold"),
                        strict = opts.inquire("strict"),
                        tough = tough,
                    )
                }
                arg3.isJsBoolean() -> {
                    listOf("source", "fromUnit", "toUnit", "useIecIdentifier").forBytesString(
                        source = arg0,
                        fromUnit = arg1,
                        toUnit = arg2,
                        useIecIdentifier = arg3,
                        strict = tough == STRICT,
                    )
                }
                arg3.isJsNumber() -> {
                    listOf("source", "fromUnit", "toUnit", "fractionDigits").forBytesString(
                        source = arg0,
                        fromUnit = arg1,
                        toUnit = arg2,
                        fractionDigits = arg3,
                        strict = tough == STRICT,
                    )
                }
                else -> throw IllegalArgumentException("Invalid argument[3] ${arg3.jsBrief()} for ${Formatter.key}.bytes")
            }
            3 -> when {
                arg2.isJsObject() -> {
                    val opts = arg2 as ScriptableObject
                    listOf("source", "toUnit", "options").forBytesString(
                        source = arg0,
                        fromUnit = opts.inquire("fromUnit"),
                        toUnit = opts.inquire("toUnit", arg1),
                        fractionDigits = opts.inquire("fractionDigits"),
                        useIecIdentifier = opts.inquire("useIecIdentifier"),
                        useSpace = opts.inquire("useSpace"),
                        trimTrailingZero = opts.inquire("trimTrailingZero"),
                        autoCarryThreshold = opts.inquire("autoCarryThreshold"),
                        strict = opts.inquire("strict"),
                        tough = tough,
                    )
                }
                arg2.isJsString() -> {
                    listOf("source", "fromUnit", "toUnit").forBytesString(
                        source = arg0,
                        fromUnit = arg1,
                        toUnit = arg2,
                        strict = tough == STRICT,
                    )
                }
                arg2.isJsBoolean() -> {
                    listOf("source", "toUnit", "useIecIdentifier").forBytesString(
                        source = arg0,
                        toUnit = arg1,
                        useIecIdentifier = arg2,
                        strict = tough == STRICT,
                    )
                }
                arg2.isJsNumber() -> {
                    listOf("source", "toUnit", "fractionDigits").forBytesString(
                        source = arg0,
                        toUnit = arg1,
                        fractionDigits = arg2,
                        strict = tough == STRICT,
                    )
                }
                else -> throw IllegalArgumentException("Invalid argument[2] ${arg2.jsBrief()} for ${Formatter.key}.bytes")
            }
            2 -> when {
                arg1.isJsObject() -> {
                    val opts = arg1 as ScriptableObject
                    listOf("source", "options").forBytesString(
                        source = arg0,
                        fromUnit = opts.inquire("fromUnit"),
                        toUnit = opts.inquire("toUnit"),
                        fractionDigits = opts.inquire("fractionDigits"),
                        useIecIdentifier = opts.inquire("useIecIdentifier"),
                        useSpace = opts.inquire("useSpace"),
                        trimTrailingZero = opts.inquire("trimTrailingZero"),
                        autoCarryThreshold = opts.inquire("autoCarryThreshold"),
                        strict = opts.inquire("strict"),
                        tough = tough,
                    )
                }
                arg1.isJsString() -> {
                    listOf("source", "toUnit").forBytesString(
                        source = arg0,
                        toUnit = arg1,
                        strict = tough == STRICT,
                    )
                }
                arg1.isJsBoolean() -> {
                    listOf("source", "useIecIdentifier").forBytesString(
                        source = arg0,
                        useIecIdentifier = arg1,
                        strict = tough == STRICT,
                    )
                }
                arg1.isJsNumber() -> {
                    listOf("source", "fractionDigits").forBytesString(
                        source = arg0,
                        fractionDigits = arg1,
                        strict = tough == STRICT,
                    )
                }
                else -> throw IllegalArgumentException("Invalid argument[1] ${arg1.jsBrief()} for ${Formatter.key}.bytes")
            }
            1 -> listOf("source").forBytesString(
                source = arg0,
                strict = tough == STRICT,
            )
            else -> throw IllegalArgumentException("Invalid arguments length ${argList.size} for ${Formatter.key}.bytes")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun strict(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..4) {
        call(args, STRICT)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun loose(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..4) {
        call(args, LOOSE)
    }

    private fun List<String>.forBytesString(
        source: Any? = null,
        fromUnit: Any? = null,
        toUnit: Any? = null,
        useIecIdentifier: Any? = null,
        useSpace: Any? = null,
        fractionDigits: Any? = null,
        trimTrailingZero: Any? = null,
        autoCarryThreshold: Any? = null,
        strict: Any? = null,
        tough: Tough = NONE,
    ): String {
        val (niceStrict, signature) = when (tough) {
            STRICT -> {
                val signature = "${Formatter.key}.bytes.strict(${this.joinToString(", ")})"
                require(strict.isJsNullish()) {
                    "Option \"strict\" ${strict.jsBrief()} must be nullish when in strict mode for $signature"
                }
                true to signature
            }
            LOOSE -> {
                val signature = "${Formatter.key}.bytes.loose(${this.joinToString(", ")})"
                require(strict.isJsNullish()) {
                    "Option \"strict\" ${strict.jsBrief()} must be nullish when in loose mode for $signature"
                }
                false to signature
            }
            NONE -> {
                val signature = "${Formatter.key}.bytes(${this.joinToString(", ")})"
                coerceBoolean(strict, DEFAULT_BYTES_STRICT) to signature
            }
        }
        return CoreBytes.stringRhino(
            source,
            fromUnit,
            toUnit,
            useIecIdentifier,
            useSpace,
            fractionDigits,
            trimTrailingZero,
            autoCarryThreshold,
            niceStrict,
            signature,
        )
    }

}
