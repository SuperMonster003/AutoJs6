package org.autojs.autojs.runtime.api.modules.cvt

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.floor
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Byte unit conversion shared by `cvt.bytes`, `fmt.bytes` and `s13n.bytes` on the new script module layer (roadmap
 * P2.2), ported from the current `augment.converter.core.Bytes` with Rhino and the JDK only.
 *
 * Loose mode (the default) ignores case, reads IEC names such as `KiB` as `KB` and uses 1024; strict mode tells SI
 * (1000) from IEC (1024) units. A string source is a number with an optional unit (`'1.5 MiB'`), which must agree with
 * an explicit `fromUnit`. Deviations of the current module fixed here (ROADMAP appendix E, "P2.2"): a nullish source was
 * read as 0; `fractionDigits` was ignored for the target `B` (and below the first unit with `AUTO`), so `s13n.bytes` was
 * not an integer; a unit prefix of several letters matched as a substring of "KMGTPEZYRQ" (`'KMB'` was `KB`); strict
 * `AUTO` in `fmt.bytes` computed with the SI base of the source unit but labelled the result with an IEC unit; results
 * beyond 2^53 - 1 were Java BigDecimal objects (a lossy number to scripts) instead of the documented bigint. Errors
 * follow D10: a value of the wrong type is a TypeError, an unusable value (negative, unknown unit, malformed text) a
 * RangeError, an option the call does not allow a TypeError.
 *
 * zh-CN: 新脚本模块层上 cvt.bytes, fmt.bytes 与 s13n.bytes 共用的字节单位换算 (P2.2), 由现有 augment.converter.core.Bytes 移植, 只依赖 Rhino 与 JDK.
 * 宽松模式 (默认) 不区分大小写, 把 KiB 等 IEC 写法读作 KB, 基数 1024; 严格模式区分 SI (1000) 与 IEC (1024). 字符串 source 为数值加可选单位, 须与显式 fromUnit
 * 一致. 修正的现有模块偏差见附录 E "P2.2": nullish source 被当作 0; 目标单位为 B (以及 AUTO 下不足第一个单位) 时忽略 fractionDigits, s13n.bytes 因此不是整数;
 * 多字母的单位前缀按 "KMGTPEZYRQ" 的子串匹配 ('KMB' 被当作 KB); fmt.bytes 严格模式的 AUTO 按源单位的 SI 基数计算却标为 IEC 单位; 超过 2^53 - 1 的结果是 Java
 * BigDecimal (对脚本是有损的 number) 而不是文档写明的 bigint. 错误按 D10: 类型不对为 TypeError, 取值不可用 (负数, 未知单位, 格式错误的文本) 为 RangeError, 调用不允许的
 * 选项为 TypeError.
 */
internal object ByteUnits {

    @Suppress("SpellCheckingInspection")
    const val UNITS = "KMGTPEZYRQ"
    const val AUTO = "AUTO"
    const val IEC_DIV = 1024L
    const val SI_DIV = 1000L

    private const val DEFAULT_FROM_UNIT = "B"
    private const val DEFAULT_FRACTION_DIGITS = 2.0
    private const val DEFAULT_AUTO_CARRY_THRESHOLD = 1024.0

    private val BI_IEC = BigInteger.valueOf(IEC_DIV)
    private val BI_SI = BigInteger.valueOf(SI_DIV)
    private val POW_IEC = Array(UNITS.length + 1) { i -> BI_IEC.pow(i) }
    private val POW_SI = Array(UNITS.length + 1) { i -> BI_SI.pow(i) }
    private val MAX_SAFE = BigDecimal.valueOf(9_007_199_254_740_991L)
    private val MIN_SAFE = MAX_SAFE.negate()

    private val SOURCE_TEXT = Regex("^(\\d+(?:\\.\\d+)?)\\s*([A-Za-z]*)$")
    private val IEC_INFIX = Regex("I(?=B$)")
    private val TRAILING_ZEROS = Regex("(\\.\\d*?)0+$")

    /** `cvt.bytes(...)` / `fmt.bytes(...)` (strict by `options.strict`), or the forced modes `.strict(...)` / `.loose(...)`. */
    enum class Mode { DEFAULT, STRICT, LOOSE }

    /** The raw script values of one call after the positional overloads are resolved. */
    class Request(
        val function: String,
        val source: Any?,
        val fromUnit: Any? = null,
        val toUnit: Any? = null,
        val fractionDigits: Any? = null,
        val autoCarryThreshold: Any? = null,
        val strict: Boolean = false,
        val useIecIdentifier: Any? = null,
        val useSpace: Any? = null,
        val trimTrailingZero: Any? = null,
    )

    /**
     * Resolves the overloads `(source)`, `(source, toUnit | options | fractionDigits | useIecIdentifier)`,
     * `(source, toUnit, options | fractionDigits | useIecIdentifier)`, `(source, fromUnit, toUnit)` and
     * `(source, fromUnit, toUnit, options | fractionDigits | useIecIdentifier)` of `cvt.bytes` / `fmt.bytes`: the last
     * argument decides by its type, `options.fromUnit` / `options.toUnit` take precedence over the positional units, and
     * a boolean is `useIecIdentifier` (only `fmt` reads it). Trailing `undefined` arguments count as omitted (T4; the
     * generated rest parameter keeps them).
     */
    fun request(function: String, all: Array<out Any?>, mode: Mode): Request {
        val args = Array<Any?>(ScriptArgs.count(all, 1)) { all[it] }
        if (args.isEmpty() || args.size > 4) throw ScriptArgs.arityError(function, 1, 4, args.size)
        val source = args[0]
        val positional = mode == Mode.STRICT
        if (args.size == 1) return Request(function, source, strict = positional)
        val last = args.last()
        return when (kindOf(last)) {
            Kind.OPTIONS -> {
                val options = last as Scriptable
                fun option(name: String): Any? = ScriptableObject.getProperty(options, name).takeUnless { it === Scriptable.NOT_FOUND || ScriptArgs.isNullish(it) }
                Request(
                    function = function,
                    source = source,
                    fromUnit = option("fromUnit") ?: args.getOrNull(1).takeIf { args.size == 4 },
                    toUnit = option("toUnit") ?: args.getOrNull(args.size - 2).takeIf { args.size >= 3 },
                    fractionDigits = option("fractionDigits"),
                    autoCarryThreshold = option("autoCarryThreshold"),
                    strict = strictOf(function, mode, option("strict")),
                    useIecIdentifier = option("useIecIdentifier"),
                    useSpace = option("useSpace"),
                    trimTrailingZero = option("trimTrailingZero"),
                )
            }
            Kind.STRING -> when (args.size) {
                2 -> Request(function, source, toUnit = last, strict = positional)
                3 -> Request(function, source, fromUnit = args[1], toUnit = last, strict = positional)
                else -> throw lastArgumentError(function, last, "number / boolean / Object")
            }
            Kind.BOOLEAN -> when (args.size) {
                2 -> Request(function, source, useIecIdentifier = last, strict = positional)
                3 -> Request(function, source, toUnit = args[1], useIecIdentifier = last, strict = positional)
                else -> Request(function, source, fromUnit = args[1], toUnit = args[2], useIecIdentifier = last, strict = positional)
            }
            Kind.NUMBER -> when (args.size) {
                2 -> Request(function, source, fractionDigits = last, strict = positional)
                3 -> Request(function, source, toUnit = args[1], fractionDigits = last, strict = positional)
                else -> Request(function, source, fromUnit = args[1], toUnit = args[2], fractionDigits = last, strict = positional)
            }
            Kind.OTHER -> throw lastArgumentError(function, last, if (args.size == 4) "number / boolean / Object" else "string / number / boolean / Object")
        }
    }

    /** `s13n.bytes(source, fromUnit?)`: loose mode, the target `B` and no fraction digits, that is an integer byte count. */
    fun integerBytesRequest(function: String, source: Any?, fromUnit: Any?) =
        Request(function, source, fromUnit = fromUnit, toUnit = DEFAULT_FROM_UNIT, fractionDigits = 0.0)

    /** The converted value: a number, or a bigint for an integer beyond the safe integer range (as documented). */
    fun number(r: Request): Any {
        val value = valueOf(resolve(r))
        return when {
            value >= MIN_SAFE && value <= MAX_SAFE -> value.toDouble()
            value.stripTrailingZeros().scale() <= 0 -> value.toBigIntegerExact()
            else -> value.toDouble()
        }
    }

    fun string(r: Request): String {
        val resolved = resolve(r)
        if (r.strict && !ScriptArgs.isNullish(r.useIecIdentifier)) {
            throw typeError(r.function, ScriptMessages.argumentCannotBe("useIecIdentifier", ScriptArgs.describe(r.useIecIdentifier)))
        }
        val useIec = !ScriptArgs.isNullish(r.useIecIdentifier) && RhinoScriptRuntime.toBoolean(r.useIecIdentifier)
        val space = if (ScriptArgs.isNullish(r.useSpace) || RhinoScriptRuntime.toBoolean(r.useSpace)) " " else ""
        val trim = !ScriptArgs.isNullish(r.trimTrailingZero) && RhinoScriptRuntime.toBoolean(r.trimTrailingZero)
        val text = valueOf(resolved).setScale(resolved.fractionDigits, RoundingMode.HALF_UP).toPlainString().let {
            if (trim) it.replace(TRAILING_ZEROS, "$1").removeSuffix(".") else it
        }
        val (core, iec) = when (resolved.toUnit) {
            AUTO -> {
                val base = autoBase(resolved)
                UNITS.take(autoExponent(resolved.bytes, base, resolved.autoCarryThreshold)).takeLast(1) to (if (r.strict) base == BI_IEC else useIec)
            }
            else -> targetUnit(resolved).let { it.core to (if (r.strict) it.iec else useIec) }
        }
        val suffix = when {
            core.isEmpty() -> "B"
            iec -> "${core}iB"
            else -> "${core}B"
        }
        return "$text$space$suffix"
    }

    private enum class Kind { OPTIONS, STRING, BOOLEAN, NUMBER, OTHER }

    private class Resolved(
        val function: String,
        val strict: Boolean,
        val bytes: BigDecimal,
        val base: BigInteger,
        val toUnit: String,
        val toUnitText: String,
        val fractionDigits: Int,
        val autoCarryThreshold: BigDecimal,
    )

    private class TargetUnit(val core: String, val iec: Boolean, val index: Int)

    /** Script objects (not Java objects or functions) are options; wrapped Java values count as what they wrap. */
    private fun kindOf(value: Any?): Kind {
        if (value is Scriptable && value !is Wrapper && value !is Callable) return Kind.OPTIONS
        return when (if (value is Wrapper) value.unwrap() else value) {
            is CharSequence -> Kind.STRING
            is Boolean -> Kind.BOOLEAN
            is Number -> Kind.NUMBER
            else -> Kind.OTHER
        }
    }

    private fun lastArgumentError(function: String, last: Any?, expected: String): RuntimeException =
        typeError(function, ScriptMessages.argumentType("options", expected, ScriptArgs.describe(last)))

    private fun strictOf(function: String, mode: Mode, option: Any?): Boolean = when (mode) {
        Mode.DEFAULT -> option != null && RhinoScriptRuntime.toBoolean(option)
        else -> {
            if (option != null) throw typeError(function, ScriptMessages.argumentCannotBe("options.strict", ScriptArgs.describe(option)))
            mode == Mode.STRICT
        }
    }

    private fun resolve(r: Request): Resolved {
        val toUnitText = textOf(r.toUnit, AUTO)
        val toUnit = normalize(toUnitText, r.strict).ifEmpty { AUTO }
        val fractionDigits = numberOption(r.function, "fractionDigits", r.fractionDigits, DEFAULT_FRACTION_DIGITS).also {
            if (it != floor(it) || it > Int.MAX_VALUE) throw rangeError(r.function, ScriptMessages.argumentInteger("fractionDigits", 32, numberText(it)))
            if (it < 0) throw rangeError(r.function, ScriptMessages.argumentCannotBe("fractionDigits", numberText(it)))
        }.toInt()
        if (!ScriptArgs.isNullish(r.autoCarryThreshold) && !toUnit.equals(AUTO, ignoreCase = true)) {
            throw typeError(r.function, ScriptMessages.argumentCannotBe("autoCarryThreshold", ScriptArgs.describe(r.autoCarryThreshold)))
        }
        val threshold = numberOption(r.function, "autoCarryThreshold", r.autoCarryThreshold, DEFAULT_AUTO_CARRY_THRESHOLD).also {
            if (it <= 0) throw rangeError(r.function, ScriptMessages.argumentPositive("autoCarryThreshold", numberText(it)))
        }.let { BigDecimal.valueOf(it) }
        val (value, fromUnit) = sourceOf(r)
        val coreRaw = fromUnit.removeSuffix("B")
        val iec = r.strict && coreRaw.endsWith("I")
        val core = if (iec) coreRaw.removeSuffix("I") else coreRaw
        val base = if (!r.strict || iec) BI_IEC else BI_SI
        val index = unitIndex(core) ?: throw rangeError(r.function, ScriptMessages.argumentCannotBe("fromUnit", quoted(textOf(r.fromUnit, fromUnit))))
        val factor = if (index < 0) BigInteger.ONE else pow(base, index + 1)
        val resolved = Resolved(r.function, r.strict, BigDecimal.valueOf(value).multiply(BigDecimal(factor)), base, toUnit.uppercase().takeIf { it == AUTO } ?: toUnit, toUnitText, fractionDigits, threshold)
        if (resolved.toUnit != AUTO) targetUnit(resolved)
        return resolved
    }

    /** -1 for no prefix, null for an unknown one: a prefix is one letter of [UNITS]. */
    private fun unitIndex(core: String): Int? = when {
        core.isEmpty() -> -1
        core.length == 1 && core[0] in UNITS -> UNITS.indexOf(core[0])
        else -> null
    }

    private fun targetUnit(resolved: Resolved): TargetUnit {
        val coreRaw = resolved.toUnit.uppercase().removeSuffix("B")
        val iec = resolved.strict && coreRaw.endsWith("I")
        val core = if (iec) coreRaw.removeSuffix("I") else coreRaw
        val index = unitIndex(core) ?: throw rangeError(resolved.function, ScriptMessages.argumentCannotBe("toUnit", quoted(resolved.toUnitText)))
        return TargetUnit(core, iec, index)
    }

    /** Strict `AUTO` keeps the base of the source unit (SI for `B`), loose mode always uses 1024. */
    private fun autoBase(resolved: Resolved): BigInteger = if (resolved.strict) resolved.base else BI_IEC

    private fun valueOf(resolved: Resolved): BigDecimal {
        val divisor = when (resolved.toUnit) {
            AUTO -> autoBase(resolved).let { base -> pow(base, autoExponent(resolved.bytes, base, resolved.autoCarryThreshold)) }
            else -> targetUnit(resolved).let { unit ->
                if (unit.index < 0) BigInteger.ONE else pow(if (!resolved.strict || unit.iec) BI_IEC else BI_SI, unit.index + 1)
            }
        }
        return resolved.bytes.divide(BigDecimal(divisor), resolved.fractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    /** The exponent of the `AUTO` unit, carried one step early when the value reaches [threshold] (exact, no Double logs). */
    private fun autoExponent(bytes: BigDecimal, base: BigInteger, threshold: BigDecimal): Int {
        if (bytes.signum() == 0) return 0
        var exponent = 0
        for (i in 1..UNITS.length) {
            if (bytes >= BigDecimal(pow(base, i))) exponent = i else break
        }
        if (exponent in 1 until UNITS.length) {
            val current = bytes.divide(BigDecimal(pow(base, exponent)), 6, RoundingMode.HALF_UP)
            if (current >= threshold) exponent += 1
        }
        return exponent
    }

    /** The source value and its unit (the unit of a string source or an explicit `fromUnit`, `B` without either). */
    private fun sourceOf(r: Request): Pair<Double, String> {
        val source = r.source
        if (ScriptArgs.isNullish(source)) throw typeError(r.function, ScriptMessages.argumentType("source", "number / string", ScriptArgs.describe(source)))
        val unwrapped = if (source is Wrapper) source.unwrap() else source
        val value: Double
        val parsedUnit: String
        if (unwrapped is Number && unwrapped !is BigInteger) {
            value = unwrapped.toDouble()
            if (value.isNaN() || value.isInfinite() || value < 0) throw rangeError(r.function, ScriptMessages.argumentCannotBe("source", numberText(value)))
            parsedUnit = ""
        } else {
            val text = RhinoScriptRuntime.toString(unwrapped).trim()
            val match = SOURCE_TEXT.find(text) ?: throw rangeError(r.function, ScriptMessages.argumentCannotBe("source", quoted(text)))
            value = RhinoScriptRuntime.toNumber(match.groupValues[1])
            parsedUnit = match.groupValues[2]
        }
        val incoming = normalize(textOf(r.fromUnit, ""), r.strict)
        val parsed = normalize(parsedUnit, r.strict)
        val unit = when {
            incoming.isEmpty() -> parsed
            parsed.isEmpty() -> incoming
            incoming.equals(parsed, ignoreCase = !r.strict) -> incoming
            else -> throw rangeError(r.function, ScriptMessages.argumentCannotBe("fromUnit", quoted(textOf(r.fromUnit, ""))))
        }.ifEmpty { DEFAULT_FROM_UNIT }.uppercase()
        return value to unit
    }

    private fun textOf(value: Any?, default: String): String =
        if (ScriptArgs.isNullish(value)) default else RhinoScriptRuntime.toString(if (value is Wrapper) value.unwrap() else value)

    /** Strict mode keeps the letters (`KiB`), loose mode upper-cases them and reads `KiB` as `KB`. */
    private fun normalize(unit: String, strict: Boolean): String = if (strict) unit.trim() else unit.trim().uppercase().replace(IEC_INFIX, "")

    /**
     * A finite number option (numbers and numeric strings, as other numeric parameters, T4). The documentation requires a
     * non-negative integer `fractionDigits` and a positive `autoCarryThreshold`; the current module truncated both.
     */
    private fun numberOption(function: String, param: String, value: Any?, default: Double): Double {
        if (ScriptArgs.isNullish(value)) return default
        val d = ScriptArgs.double(value, function, param)
        if (d.isNaN() || d.isInfinite()) throw rangeError(function, ScriptMessages.argumentCannotBe(param, numberText(d)))
        return d
    }

    private fun pow(base: BigInteger, exponent: Int): BigInteger = when {
        base == BI_IEC && exponent in 0..UNITS.length -> POW_IEC[exponent]
        base == BI_SI && exponent in 0..UNITS.length -> POW_SI[exponent]
        else -> base.pow(exponent)
    }

    private fun numberText(d: Double): String = RhinoScriptRuntime.numberToString(d, 10)

    private fun quoted(text: String) = "\"$text\""

    private fun typeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, cause))

    private fun rangeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, cause))

}
