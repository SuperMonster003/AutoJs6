package org.autojs.autojs.runtime.api.modules.cvt

import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.augment.converter.Converter
import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.formatter.Formatter
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.runtime.api.augment.converter.Bytes as BytesCvt
import org.autojs.autojs.runtime.api.augment.formatter.Bytes as BytesFmt

/**
 * `cvt.bytes`, `fmt.bytes` and `s13n.bytes` (6.7.0, no obsolete JS, no unit tests in the `augment` package) of the new
 * script module layer against the current Kotlin modules (roadmap P2.2): the "js" side of the [DifferentialRhino] mounts
 * the current `augment` modules, the "kt" side the new layer as `ScriptRuntime.augment()` does with the switch on. The
 * expressions are the documented examples (api/cvt.md, api/fmt.md, api/s13n.md) and the edge cases of the port; the
 * known differences are the deviations of the current module fixed by the new layer (ROADMAP appendix E, "P2.2").
 * Error types are not compared here, since every error of the current module is an InternalError (D10);
 * `PureLogicModulesTest` checks the new layer's error types, and the device run (`--strict-errors`) checks the exported
 * values of the new layer including them.
 */
class BytesModulesDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val current = RhinoTestRuntime.create(rhino.jsScope)
        val global = rhino.jsScope.globalThis
        S13n.augmentWithRuntime(global, current)
        Converter.augmentWithRuntime(global, current).also { BytesCvt.augmentWithRuntime(it, current) }
        Formatter.augmentWithRuntime(global, current).also { BytesFmt.augmentWithRuntime(it, current) }
        rhino.mountNewModules("s13n", "cvt", "fmt")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val EXPRESSIONS = listOf(
            // api/cvt.md
            "cvt.bytes('1.5 MiB', 'KiB')",
            "cvt.bytes.strict('1 KB', 'B')",
            "cvt.bytes.strict('1 KiB', 'B')",
            "cvt.bytes(1536)",
            "cvt.bytes('1536 B')",
            "cvt.bytes('1.5 MiB')",
            "cvt.bytes(1536, 'KB')",
            "cvt.bytes('1.5 MB', 'KB')",
            "cvt.bytes('1 KiB', 'B')",
            "cvt.bytes(1536, { fromUnit: 'B', toUnit: 'KB', fractionDigits: 3 })",
            "cvt.bytes(1536, 0)",
            "cvt.bytes(1536, 'KB', 3)",
            "cvt.bytes(1536, 'MB', { toUnit: 'KB' })",
            "cvt.bytes(2, 'MB', 'KB')",
            "cvt.bytes.strict(2, 'MB', 'KB')",
            "cvt.bytes.strict(2, 'MiB', 'KiB')",
            "cvt.bytes.strict(1000, 'B', 'KB')",
            "cvt.bytes.strict(1024, 'B', 'KiB')",
            "cvt.bytes.loose('1 KB', 'B')",
            "cvt.bytes.loose('1 KiB', 'B')",
            "[cvt.bytes.UNITS, cvt.bytes.AUTO, cvt.bytes.IEC_DIV, cvt.bytes.SI_DIV]",
            // further cvt cases
            "cvt.bytes(0)",
            "cvt.bytes(1023)",
            "cvt.bytes(1024)",
            "cvt.bytes(1048575)",
            "cvt.bytes(123456789, 'MB', 4)",
            "cvt.bytes('  42 kb ')",
            "cvt.bytes('1 kib', 'b')",
            "cvt.bytes(1536, true)",
            "cvt.bytes(1536, 'KB', true)",
            "cvt.bytes(1, 'KB', 'B', true)",
            "cvt.bytes(1, 'MB', 'KB', 1)",
            "cvt.bytes(1, 'MB', 'KB', { fromUnit: 'GB' })",
            "cvt.bytes(1536, { strict: true })",
            "cvt.bytes(1536, { strict: true, toUnit: 'KiB' })",
            "cvt.bytes(1536, { strict: 1 })",
            "cvt.bytes(1048576 * 900, { autoCarryThreshold: 800 })",
            "cvt.bytes(1500, { strict: true, autoCarryThreshold: 1 })",
            "cvt.bytes.strict(1500000)",
            "cvt.bytes.strict('1.5 GiB')",
            "cvt.bytes.strict(1, 'kib', 'B')",
            "cvt.bytes.strict('1 kib', 'KiB', 'B')",
            "cvt.bytes([1536])",
            "cvt.bytes({ toString() { return '2 KB'; } }, 'B')",
            "cvt.bytes(1, 'QB', 'YB')",
            "cvt.bytes(1, 'TB', 'B')",
            "cvt.bytes(-1)",
            "cvt.bytes(NaN)",
            "cvt.bytes('abc')",
            "cvt.bytes('-1 KB')",
            "cvt.bytes('1e3')",
            "cvt.bytes(1, 'XB')",
            "cvt.bytes('1 KB', 'MB', 'B')",
            "cvt.bytes(1, null)",
            "cvt.bytes(1, 'KB', 'MB', 'x')",
            "cvt.bytes(1536, { fractionDigits: -1 })",
            "cvt.bytes(1536, { fractionDigits: 'x' })",
            "cvt.bytes(1536, { autoCarryThreshold: 0 })",
            "cvt.bytes(1536, { autoCarryThreshold: 900, toUnit: 'KB' })",
            "cvt.bytes.strict(1, { strict: true })",
            "cvt.bytes.loose(1, { strict: false })",
            "cvt.bytes()",
            "cvt.bytes(1, 2, 3, 4, 5)",
            // fixed deviations (known differences)
            "cvt.bytes(null)",
            "cvt.bytes(undefined)",
            "cvt.bytes(1.555)",
            "cvt.bytes(1.555, 'B', 'B')",
            "cvt.bytes.strict(1.555)",
            "cvt.bytes(1, 'KMB', 'B')",
            "cvt.bytes(1, 'B', 'MGB')",
            "typeof cvt.bytes(1, 'QB', 'B')",
            "cvt.bytes(1536, { fractionDigits: 1.5 })",
            "cvt.bytes(1536, { fractionDigits: true })",
            "typeof cvt.bytes(1536, { autoCarryThreshold: 0.5 })",
            "cvt.bytes(1536, undefined)",
            // api/fmt.md
            "fmt.bytes(1536)",
            "fmt.bytes.strict(1000, 'B', 'KB')",
            "fmt.bytes.strict(1024, 'B', 'KiB')",
            "fmt.bytes('1536 B')",
            "fmt.bytes('1.5 MiB')",
            "fmt.bytes(1536, 'KB')",
            "fmt.bytes('1.5 MB', 'KB')",
            "fmt.bytes('1 KiB', 'B')",
            "fmt.bytes(1536, { toUnit: 'KB', fractionDigits: 3, useSpace: false })",
            "fmt.bytes(1536, 0)",
            "fmt.bytes(1536, true)",
            "fmt.bytes(1536, 'KB', 3)",
            "fmt.bytes(1536, 'KB', true)",
            "fmt.bytes(1536, 'MB', { toUnit: 'KB' })",
            "fmt.bytes(2, 'MB', 'KB')",
            "fmt.bytes.strict(2, 'MB', 'KB')",
            "fmt.bytes.strict(2, 'MiB', 'KiB')",
            "fmt.bytes.strict('1 KB', 'B')",
            "fmt.bytes.strict('1 KiB', 'B')",
            "fmt.bytes.loose('1 KB', 'B')",
            "fmt.bytes.loose(1024, 'KB')",
            "fmt.bytes.loose(1024, 'KB', true)",
            // further fmt cases
            "fmt.bytes(0)",
            "fmt.bytes(1.555, 'B')",
            "fmt.bytes(1536, { trimTrailingZero: true })",
            "fmt.bytes(1024, { trimTrailingZero: true, useIecIdentifier: true })",
            "fmt.bytes(1536, 'KB', { useSpace: false, fractionDigits: 0 })",
            "fmt.bytes(1, 'MB', 'KB', true)",
            "fmt.bytes('1536 KiB', { strict: true })",
            "fmt.bytes.strict('1536 KiB')",
            "fmt.bytes.strict(1536, 'KB', true)",
            "fmt.bytes.strict(1536, { useIecIdentifier: false })",
            "fmt.bytes(-1)",
            "fmt.bytes(1, 'XB')",
            "fmt.bytes()",
            "fmt.bytes.strict(1500000)",
            "fmt.bytes(null)",
            // s13n.bytes (api/s13n.md)
            "s13n.bytes('1.5 KiB')",
            "s13n.bytes(2, 'MB')",
            "s13n.bytes('1 KB', 'KiB')",
            "s13n.bytes('1 KB', 'MB')",
            "s13n.bytes(-1)",
            "s13n.bytes(1, 5)",
            "s13n.bytes(1.5)",
            "s13n.bytes('1.4 B')",
            "typeof s13n.bytes(1, 'QB')",
        )

        /**
         * How a failure of the current module renders on the JVM when building its message needs Android resources the test
         * context does not fake: the Java exception escapes the script (an InternalError on a device).
         */
        private const val JVM_ERROR = "throws(java) RuntimeException"

        private const val NULLISH = "the current module read a nullish source as \"0\" (0 bytes); documented sources are numbers and strings, " +
                "so the new layer rejects it with a TypeError"
        private const val DIGITS = "documented: fractionDigits (default 2) applies to every result; the current module skipped the rounding " +
                "when the target was B (or AUTO below the first unit)"
        private const val PREFIX = "the current module looked the unit prefix up with UNITS.indexOf(prefix), so a prefix of several letters " +
                "matched as a substring (KM as K, MG as M); a prefix is one letter of UNITS"
        private const val BIGINT = "documented result type number | bigint: beyond 2^53 - 1 an integer is a bigint on the new layer; the current " +
                "module returned a Java BigDecimal, a lossy number to scripts"
        private const val OPTION = "documented: fractionDigits is a non-negative integer and autoCarryThreshold a positive number; the current module " +
                "truncated both (1.5 digits as 1, a threshold of 0.5 as 0, which then failed) and converted booleans with ToNumber"

        /** The deviations of the current Kotlin modules fixed by the new layer (ROADMAP appendix E, "P2.2"). */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference("cvt.bytes(null)", js = "0", kt = "throws TypeError", reason = NULLISH),
            DifferentialCases.KnownDifference("cvt.bytes(undefined)", js = "0", kt = "throws TypeError", reason = NULLISH),
            DifferentialCases.KnownDifference("fmt.bytes(null)", js = "\"0.00 B\"", kt = "throws TypeError", reason = NULLISH),
            DifferentialCases.KnownDifference("cvt.bytes(1.555)", js = "1.555", kt = "1.56", reason = DIGITS),
            DifferentialCases.KnownDifference("cvt.bytes(1.555, 'B', 'B')", js = "1.555", kt = "1.56", reason = DIGITS),
            DifferentialCases.KnownDifference("cvt.bytes.strict(1.555)", js = "1.555", kt = "1.56", reason = DIGITS),
            DifferentialCases.KnownDifference("s13n.bytes(1.5)", js = "1.5", kt = "2", reason = "documented: s13n.bytes returns an integer byte count; $DIGITS"),
            DifferentialCases.KnownDifference("s13n.bytes('1.4 B')", js = "1.4", kt = "1", reason = "documented: s13n.bytes returns an integer byte count; $DIGITS"),
            DifferentialCases.KnownDifference("cvt.bytes(1, 'KMB', 'B')", js = "1024", kt = "throws RangeError", reason = PREFIX),
            DifferentialCases.KnownDifference("cvt.bytes(1, 'B', 'MGB')", js = "0", kt = "throws RangeError", reason = PREFIX),
            DifferentialCases.KnownDifference("typeof cvt.bytes(1, 'QB', 'B')", js = "\"number\"", kt = "\"bigint\"", reason = BIGINT),
            DifferentialCases.KnownDifference("typeof s13n.bytes(1, 'QB')", js = "\"number\"", kt = "\"bigint\"", reason = BIGINT),
            DifferentialCases.KnownDifference("cvt.bytes(1536, { fractionDigits: 1.5 })", js = "1.5", kt = "throws RangeError", reason = OPTION),
            DifferentialCases.KnownDifference("cvt.bytes(1536, { fractionDigits: true })", js = "1.5", kt = "throws TypeError", reason = OPTION),
            DifferentialCases.KnownDifference("typeof cvt.bytes(1536, { autoCarryThreshold: 0.5 })", js = JVM_ERROR, kt = "\"number\"", reason = OPTION),
            DifferentialCases.KnownDifference(
                "fmt.bytes.strict(1500000)",
                js = "\"1.50 MiB\"",
                kt = "\"1.50 MB\"",
                reason = "strict AUTO computes with the base of the source unit (SI for B, as cvt.bytes.strict(1500000) is 1.5 MB); the current " +
                        "fmt module labelled the result with an IEC unit (1.5 MiB is 1572864 bytes)",
            ),
            DifferentialCases.KnownDifference(
                "cvt.bytes(1536, undefined)",
                js = JVM_ERROR,
                kt = "1.5",
                reason = "T4: trailing undefined arguments count as omitted on the new layer; the current module took the undefined for an " +
                        "invalid second argument",
            ),
        )

    }

}
