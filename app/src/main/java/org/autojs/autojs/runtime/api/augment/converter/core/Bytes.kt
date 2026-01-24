@file:Suppress("MayBeConstant")

package org.autojs.autojs.runtime.api.augment.converter.core

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.util.RhinoUtils.MAX_SAFE_INT_IEEE754_BD
import org.autojs.autojs.util.RhinoUtils.MIN_SAFE_INT_IEEE754_BD
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.Double.Companion.NaN
import kotlin.text.RegexOption.IGNORE_CASE

object Bytes {

    // @formatter:off
    /**
     * ```md
     * |  Value  | IEC |        Term        |
     * |---------|-----|--------------------|
     * | 1024^1  | KiB | Kibibyte (Kilo)    |
     * | 1024^2  | MiB | Mebibyte (Mega)    |
     * | 1024^3  | GiB | Gibibyte (Giga)    |
     * | 1024^4  | TiB | Tebibyte (Tera)    |
     * | 1024^5  | PiB | Pebibyte (Peta)    |
     * | 1024^6  | EiB | Exbibyte (Exa)     |
     * | 1024^7  | ZiB | Zebibyte (Zetta)   |
     * | 1024^8  | YiB | Yobibyte (Yotta)   |
     * | 1024^9  | RiB | Robibyte (Ronna)   |
     * | 1024^10 | QiB | Quebibyte (Quetta) |
     * ```
     */
    @Suppress("SpellCheckingInspection")
    @JvmField val UNITS = "KMGTPEZYRQ"
    @JvmField val AUTO = "AUTO"

    @JvmField val IEC_DIV = 1024L
    @JvmField val SI_DIV = 1000L

    @JvmField val DEFAULT_BYTES_FROM_UNIT = "B"
    @JvmField val DEFAULT_BYTES_TO_UNIT = AUTO
    @JvmField val DEFAULT_BYTES_USE_IEC_IDENTIFIER = false
    @JvmField val DEFAULT_BYTES_USE_IEC_IDENTIFIER_FOR_STRICT = true
    @JvmField val DEFAULT_BYTES_USE_SPACE = true
    @JvmField val DEFAULT_BYTES_FRACTION_DIGITS = 2
    @JvmField val DEFAULT_BYTES_TRIM_TRAILING_ZERO = false
    @JvmField val DEFAULT_BYTES_AUTO_CARRY_THRESHOLD = IEC_DIV
    @JvmField val DEFAULT_BYTES_STRICT = false
    // @formatter:on

    private val BI_IEC = BigInteger.valueOf(IEC_DIV)
    private val BI_SI = BigInteger.valueOf(SI_DIV)
    private val POW_IEC = Array(UNITS.length + 1) { i -> BI_IEC.pow(i) }
    private val POW_SI = Array(UNITS.length + 1) { i -> BI_SI.pow(i) }

    @JvmStatic
    fun numberRhino(
        source: Any? = null, /* Double */
        fromUnit: Any? = null, /* String */
        toUnit: Any? = null, /* String */
        fractionDigits: Any? = null, /* Int */
        autoCarryThreshold: Any? = null, /* Double */
        strict: Any? = null, /* Boolean */
        signature: String? = null,
    ): Any {
        val r = NumberRetrievalHandler(source, fromUnit, toUnit, fractionDigits, autoCarryThreshold, strict, signature).retrieve()
        return number(
            r.niceSource, r.niceFromUnit, r.niceToUnit, r.niceFractionDigits, r.niceAutoCarryThreshold, r.niceStrict, signature ?: "bytes.number",
        ).toJavaScriptBigIntOrNumber()
    }

    @JvmStatic
    @JvmOverloads
    fun number(
        source: Double,
        fromUnit: String = DEFAULT_BYTES_FROM_UNIT,
        toUnit: String = DEFAULT_BYTES_TO_UNIT,
        fractionDigits: Int = DEFAULT_BYTES_FRACTION_DIGITS,
        autoCarryThreshold: Long = DEFAULT_BYTES_AUTO_CARRY_THRESHOLD,
        strict: Boolean = DEFAULT_BYTES_STRICT,
        signature: String = "Bytes.number",
    ) = parseArguments(source, fromUnit, toUnit, fractionDigits, autoCarryThreshold, strict, signature).let { (bytesBD, baseBI, toUnit) ->
        computeNumberValue(bytesBD, baseBI, toUnit, fractionDigits, autoCarryThreshold, strict, signature)
    }

    @JvmStatic
    fun stringRhino(
        source: Any? = null,
        fromUnit: Any? = null,
        toUnit: Any? = null,
        useIecIdentifier: Any? = null,
        useSpace: Any? = null,
        fractionDigits: Any? = null,
        trimTrailingZero: Any? = null,
        autoCarryThreshold: Any? = null,
        strict: Any? = null,
        signature: String? = null,
    ): String {
        val r = StringRetrievalHandler(source, fromUnit, toUnit, useIecIdentifier, useSpace, fractionDigits, trimTrailingZero, autoCarryThreshold, strict, signature).retrieve()
        return string(r.niceSource, r.niceFromUnit, r.niceToUnit, r.niceUseIecIdentifier, r.niceUseSpace, r.niceFractionDigits, r.niceTrimTrailingZero, r.niceAutoCarryThreshold, r.niceStrict, signature ?: "bytes.string")
    }

    @JvmStatic
    @JvmOverloads
    fun string(
        source: Double,
        fromUnit: String = DEFAULT_BYTES_FROM_UNIT,
        toUnit: String = DEFAULT_BYTES_TO_UNIT,
        useIecIdentifier: Boolean? = null,
        useSpace: Boolean = DEFAULT_BYTES_USE_SPACE,
        fractionDigits: Int = DEFAULT_BYTES_FRACTION_DIGITS,
        trimTrailingZero: Boolean = DEFAULT_BYTES_TRIM_TRAILING_ZERO,
        autoCarryThreshold: Long = DEFAULT_BYTES_AUTO_CARRY_THRESHOLD,
        strict: Boolean = DEFAULT_BYTES_STRICT,
        signature: String = "Bytes.string",
    ): String {
        val (bytesBD, baseBI, niceToUnit) = parseArguments(source, fromUnit, toUnit, fractionDigits, autoCarryThreshold, strict, signature)
        val bd = computeNumberValue(bytesBD, baseBI, niceToUnit, fractionDigits, autoCarryThreshold, strict, signature)
        val bdFormatted = bd.setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString().let {
            if (!trimTrailingZero) it else it.replace(Regex("(\\.\\d*?)0+$"), "$1").removeSuffix(".")
        }
        val withIec = useIecIdentifier ?: when {
            strict -> DEFAULT_BYTES_USE_IEC_IDENTIFIER_FOR_STRICT
            else -> DEFAULT_BYTES_USE_IEC_IDENTIFIER
        }
        val space = if (useSpace) " " else ""

        return when {
            strict -> when (niceToUnit) {
                AUTO -> {
                    val (_, core) = autoPickBig(bytesBD, baseBI, BigDecimal.valueOf(autoCarryThreshold))
                    val suffix = if (core.isEmpty()) "B" else "${core}iB"
                    "$bdFormatted$space$suffix"
                }
                else -> {
                    val coreRaw = niceToUnit.uppercase().removeSuffix("B")
                    val isIEC = coreRaw.endsWith("I")
                    when (val core = if (isIEC) coreRaw.removeSuffix("I") else coreRaw) {
                        "" -> "$bdFormatted${space}B"
                        else -> {
                            val idx = UNITS.indexOf(core)
                            require(idx != -1) { "Argument \"toUnit\" ${toUnit.jsBrief()} is invalid${signature.toSignatureSuffix()}" }
                            when {
                                isIEC -> "$bdFormatted${space}${core}iB"
                                else -> "$bdFormatted${space}${core}B"
                            }
                        }
                    }
                }
            }
            else -> when (niceToUnit) {
                AUTO -> {
                    val (_, core) = autoPickBig(bytesBD, BI_IEC, BigDecimal.valueOf(autoCarryThreshold))
                    val suffix = if (core.isEmpty()) "B" else if (withIec) "${core}iB" else "${core}B"
                    "$bdFormatted$space$suffix"
                }
                else -> when (val core = niceToUnit.removeSuffix("B")) {
                    "" -> "$bdFormatted${space}B"
                    else -> {
                        val idx = UNITS.indexOf(core)
                        require(idx != -1) { "Argument \"toUnit\" ${toUnit.jsBrief()} is invalid${signature.toSignatureSuffix()}" }
                        val suffix = if (withIec) "${core}iB" else "${core}B"
                        "$bdFormatted$space$suffix"
                    }
                }
            }
        }
    }

    private fun parseArguments(
        source: Double,
        fromUnit: String = DEFAULT_BYTES_FROM_UNIT,
        toUnit: String = DEFAULT_BYTES_TO_UNIT,
        fractionDigits: Int = DEFAULT_BYTES_FRACTION_DIGITS,
        autoCarryThreshold: Long = DEFAULT_BYTES_AUTO_CARRY_THRESHOLD,
        strict: Boolean = DEFAULT_BYTES_STRICT,
        signature: String,
    ): UnitConversionDetails {
        val signatureSuffix = signature.toSignatureSuffix()

        require(!source.isNaN() && source >= 0.0) {
            "Argument \"source\" ${source.jsBrief()} must be non-negative number$signatureSuffix"
        }

        require(fractionDigits >= 0) {
            "Argument \"fractionDigits\" ${fractionDigits.jsBrief()} must be non-negative$signatureSuffix"
        }

        val niceToUnit = when {
            strict -> toUnit.trim().uppercase()
            else -> toUnit.toSiUnit()
        }.takeUnless { it.isBlank() } ?: DEFAULT_BYTES_TO_UNIT

        require(autoCarryThreshold > 0) {
            "Argument \"autoCarryThreshold\" ${autoCarryThreshold.jsBrief()} must be a positive finite number$signatureSuffix"
        }
        require(autoCarryThreshold == DEFAULT_BYTES_AUTO_CARRY_THRESHOLD || niceToUnit.equals(AUTO, ignoreCase = true)) {
            "Argument \"autoCarryThreshold\" is only allowed when argument \"toUnit\" is \"$AUTO\"$signatureSuffix"
        }

        val (bytesBD, baseBI) = run parseExactBytes@{
            val valueBD = BigDecimal.valueOf(source)
            val niceFromUnit = when {
                strict -> fromUnit.trim()
                else -> fromUnit.toSiUnit()
            }.takeUnless { it.isBlank() }?.uppercase() ?: DEFAULT_BYTES_FROM_UNIT
            val coreRaw = when {
                niceFromUnit.endsWith("B") -> niceFromUnit.dropLast(1)
                else -> niceFromUnit
            }
            val isIec = strict && coreRaw.endsWith("I")
            val core = if (isIec) coreRaw.removeSuffix("I") else coreRaw
            val base = if (!strict) BI_IEC else if (isIec) BI_IEC else BI_SI
            val idx = if (core.isEmpty()) -1 else UNITS.indexOf(core)
            require(core.isEmpty() || idx != -1) { "Argument \"fromUnit\" ${fromUnit.jsBrief()} is invalid$signatureSuffix" }
            val factor = if (idx == -1) BigInteger.ONE else powBI(base, idx + 1)
            valueBD.multiply(BigDecimal(factor)) to base
        }

        return UnitConversionDetails(bytesBD, baseBI, niceToUnit)
    }

    // Calculate the order of AUTO (avoiding errors from ln/Double), determine whether to carry early (autoCarryThreshold)
    // zh-CN: 求 AUTO 的阶 (避免 ln/Double 带来的误差), 决策是否提前进位 (autoCarryThreshold).
    private fun autoPickBig(bytes: BigDecimal, base: BigInteger, carryThreshold: BigDecimal): Pair<Int, String> {
        if (bytes.compareTo(BigDecimal.ZERO) == 0) return 0 to ""
        var exp = 0
        val core = fun() = UNITS.substring((exp - 1).coerceAtLeast(0), exp)
        // Find max exp such that bytes >= base^exp.
        // zh-CN: 找到最大的 exp 使 bytes >= base^exp.
        for (i in 1..UNITS.length) {
            val th = BigDecimal(powBI(base, i))
            if (bytes >= th) exp = i else break
        }
        if (exp > 0) {
            val currentVal = bytes.divide(BigDecimal(powBI(base, exp)), DEFAULT_BYTES_FRACTION_DIGITS + 4, RoundingMode.HALF_UP)
            if (currentVal >= carryThreshold && exp < UNITS.length) {
                exp += 1
                return exp to core()
            }
        }
        return exp to core()
    }

    private fun parseSource(source: Any?, fromUnit: Any?, strict: Boolean, signatureSuffix: String): Pair<Double, String> {
        var bytes: Double
        var parsedUnitRaw = ""
        when {
            source.isJsNumber() -> {
                val d = coerceNumber(source, NaN)
                require(!d.isNaN() && d >= 0.0) { "Argument \"source\" ${source.jsBrief()} must be non-negative number for $signatureSuffix" }
                bytes = d
            }
            else -> {
                val s = coerceString(source, "0").trim()
                val m = Regex("^(\\d+(?:\\.\\d+)?)\\s*([A-Za-z]*)$", IGNORE_CASE).find(s)
                    ?: throw IllegalArgumentException("Invalid bytes value: \"$source\"")
                bytes = try {
                    Context.toNumber(m.groupValues[1])
                } catch (_: Throwable) {
                    throw IllegalArgumentException("Invalid bytes value: \"${m.groupValues[1]}\"")
                }
                parsedUnitRaw = m.groupValues[2]
            }
        }
        val niceFromUnit = run parseFromUnit@{
            val incoming = coerceString(fromUnit, "").let { if (strict) it.trim() else it.toSiUnit() }
            val parsed = if (strict) parsedUnitRaw.trim() else parsedUnitRaw.toSiUnit()
            when {
                incoming.isBlank() -> parsed
                parsed.isBlank() -> incoming
                incoming.equals(parsed, ignoreCase = !strict) -> incoming
                else -> throw IllegalArgumentException("Ambiguous \"fromUnit\" values: [ $incoming, $parsed ]$signatureSuffix")
            }.ifBlank { DEFAULT_BYTES_FROM_UNIT }.uppercase()
        }
        return bytes to niceFromUnit
    }

    private fun powBI(base: BigInteger, exp: Int): BigInteger = when {
        base == BI_IEC && exp in 0..UNITS.length -> POW_IEC[exp]
        base == BI_SI && exp in 0..UNITS.length -> POW_SI[exp]
        else -> base.pow(exp)
    }

    private fun computeNumberValue(
        bytesBD: BigDecimal,
        baseBI: BigInteger,
        toUnit: String,
        fractionDigits: Int,
        autoCarryThreshold: Long,
        strict: Boolean,
        signature: String,
    ): BigDecimal = when {
        strict -> when (toUnit) {
            AUTO -> {
                val (exp, _) = autoPickBig(bytesBD, baseBI, BigDecimal.valueOf(autoCarryThreshold))
                val value = if (exp == 0) bytesBD else bytesBD.divide(BigDecimal(powBI(baseBI, exp)), fractionDigits, RoundingMode.HALF_UP)
                value.stripTrailingZeros()
            }
            else -> {
                val coreRaw = toUnit.uppercase().removeSuffix("B")
                val isIEC = coreRaw.endsWith("I")
                when (val core = if (isIEC) coreRaw.removeSuffix("I") else coreRaw) {
                    "" -> bytesBD.stripTrailingZeros()
                    else -> {
                        val idx = UNITS.indexOf(core)
                        require(idx != -1) { "Argument \"toUnit\" ${toUnit.jsBrief()} is invalid${signature.toSignatureSuffix()}" }
                        val base = if (isIEC) BI_IEC else BI_SI
                        val value = bytesBD.divide(BigDecimal(powBI(base, idx + 1)), fractionDigits, RoundingMode.HALF_UP)
                        value.stripTrailingZeros()
                    }
                }
            }
        }
        else -> when (toUnit) {
            AUTO -> {
                val (exp, _) = autoPickBig(bytesBD, BI_IEC, BigDecimal.valueOf(autoCarryThreshold))
                val value = if (exp == 0) bytesBD else bytesBD.divide(BigDecimal(powBI(BI_IEC, exp)), fractionDigits, RoundingMode.HALF_UP)
                value.stripTrailingZeros()
            }
            else -> when (val core = toUnit.removeSuffix("B")) {
                "" -> bytesBD
                else -> {
                    val idx = UNITS.indexOf(core)
                    require(idx != -1) { "Argument \"toUnit\" ${toUnit.jsBrief()} is invalid${signature.toSignatureSuffix()}" }
                    val value = bytesBD.divide(BigDecimal(powBI(BI_IEC, idx + 1)), fractionDigits, RoundingMode.HALF_UP)
                    value.stripTrailingZeros()
                }
            }
        }
    }

    private fun String.toSiUnit() = this.trim().uppercase().replace(Regex("I(?=B$)"), "")

    private fun String.toSignatureSuffix() = this.takeUnless { it.isBlank() }?.let { " for $it" } ?: ""

    private fun BigDecimal.toJavaScriptBigIntOrNumber(): Any = when (this) {
        in MIN_SAFE_INT_IEEE754_BD..MAX_SAFE_INT_IEEE754_BD -> {
            this.toDouble()
        }
        else -> this
    }

    private interface RetrievalHandler {
        fun retrieve(): Any
    }

    private open class NumberRetrievalHandler(
        private val source: Any? = null, /* Double */
        private val fromUnit: Any? = null, /* String */
        private val toUnit: Any? = null, /* String */
        private val fractionDigits: Any? = null, /* Int */
        private val autoCarryThreshold: Any? = null, /* Double */
        private val strict: Any? = null, /* Boolean */
        private val signature: String? = null,
    ) : RetrievalHandler {

        override fun retrieve(): NumberRhinoStandardizationDetails {
            val signatureSuffix = signature?.toSignatureSuffix() ?: ""
            val niceStrict = coerceBoolean(strict, DEFAULT_BYTES_STRICT)
            val niceToUnit = when {
                niceStrict -> coerceString(toUnit, DEFAULT_BYTES_TO_UNIT).trim().uppercase()
                else -> coerceString(toUnit, DEFAULT_BYTES_TO_UNIT).toSiUnit()
            }.takeIf { it.isNotEmpty() } ?: DEFAULT_BYTES_TO_UNIT

            val (niceSource, niceFromUnit) = parseSource(source, fromUnit, niceStrict, signatureSuffix)

            val niceFractionDigits = coerceIntNumber(fractionDigits, DEFAULT_BYTES_FRACTION_DIGITS).also {
                require(it >= 0) { "Argument \"fractionDigits\" ${fractionDigits.jsBrief()} must be non-negative$signatureSuffix" }
            }

            require(autoCarryThreshold.isJsNullish() || niceToUnit.equals(AUTO, ignoreCase = true)) {
                "Option \"autoCarryThreshold\" is only allowed when argument \"toUnit\" is \"$AUTO\"$signatureSuffix"
            }
            val niceAutoCarryThreshold = coerceLongNumber(autoCarryThreshold, DEFAULT_BYTES_AUTO_CARRY_THRESHOLD).also {
                require(it > 0) {
                    "Option \"autoCarryThreshold\" ${autoCarryThreshold.jsBrief()} must be a positive finite number$signatureSuffix"
                }
            }
            return NumberRhinoStandardizationDetails(niceSource, niceFromUnit, niceToUnit, niceFractionDigits, niceAutoCarryThreshold, niceStrict)
        }

    }

    private class StringRetrievalHandler(
        source: Any?,
        fromUnit: Any?,
        toUnit: Any?,
        private val useIecIdentifier: Any?,
        private val useSpace: Any?,
        fractionDigits: Any?,
        private val trimTrailingZero: Any?,
        autoCarryThreshold: Any?,
        strict: Any?,
        private val signature: String? = null,
    ) : NumberRetrievalHandler(source, fromUnit, toUnit, fractionDigits, autoCarryThreshold, strict, signature) {

        override fun retrieve(): StringRhinoStandardizationDetails {
            val sp = super.retrieve()

            val niceSource = sp.niceSource
            val niceFromUnit = sp.niceFromUnit
            val niceToUnit = sp.niceToUnit
            val niceFractionDigits = sp.niceFractionDigits
            val niceAutoCarryThreshold = sp.niceAutoCarryThreshold
            val niceStrict = sp.niceStrict
            val signatureSuffix = signature?.toSignatureSuffix() ?: ""

            require(!niceStrict || useIecIdentifier.isJsNullish()) {
                "Argument \"useIecIdentifier\" ${useIecIdentifier.jsBrief()} must be nullish when in strict mode$signatureSuffix"
            }

            val niceUseSpace = coerceBoolean(useSpace, DEFAULT_BYTES_USE_SPACE)

            val niceTrimTrailingZero = coerceBoolean(trimTrailingZero, DEFAULT_BYTES_TRIM_TRAILING_ZERO)
            val niceUseIecIdentifier = if (useIecIdentifier.isJsNullish()) null else Context.toBoolean(useIecIdentifier)

            return StringRhinoStandardizationDetails(niceSource, niceFromUnit, niceToUnit, niceUseIecIdentifier, niceUseSpace, niceFractionDigits, niceTrimTrailingZero, niceAutoCarryThreshold, niceStrict)
        }
    }

    private data class UnitConversionDetails(val bytesBD: BigDecimal, val baseBI: BigInteger, val toUnit: String)

    private open class NumberRhinoStandardizationDetails(
        open val niceSource: Double,
        open val niceFromUnit: String,
        open val niceToUnit: String,
        open val niceFractionDigits: Int,
        open val niceAutoCarryThreshold: Long,
        open val niceStrict: Boolean,
    )

    private class StringRhinoStandardizationDetails(
        override val niceSource: Double,
        override val niceFromUnit: String,
        override val niceToUnit: String,
        val niceUseIecIdentifier: Boolean?,
        val niceUseSpace: Boolean,
        override val niceFractionDigits: Int,
        val niceTrimTrailingZero: Boolean,
        override val niceAutoCarryThreshold: Long,
        override val niceStrict: Boolean,
    ) : NumberRhinoStandardizationDetails(niceSource, niceFromUnit, niceToUnit, niceFractionDigits, niceAutoCarryThreshold, niceStrict)

    enum class Tough { STRICT, LOOSE, NONE }

}
