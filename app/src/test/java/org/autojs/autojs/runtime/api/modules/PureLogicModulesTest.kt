package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.base64.Base64
import org.autojs.autojs.runtime.api.augment.converter.Converter
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.formatter.Formatter
import org.autojs.autojs.runtime.api.augment.global.IsNullish
import org.autojs.autojs.runtime.api.augment.global.Legacy
import org.autojs.autojs.runtime.api.augment.global.Species
import org.autojs.autojs.runtime.api.augment.mime.Mime
import org.autojs.autojs.runtime.api.augment.nanoid.NanoID
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject
import java.io.File
import org.autojs.autojs.runtime.api.Mime as ApiMime
import org.autojs.autojs.runtime.api.augment.converter.Bytes as BytesCvt
import org.autojs.autojs.runtime.api.augment.formatter.Bytes as BytesFmt

/**
 * Roadmap P2.2: the pure logic modules of the new script module layer (`isNullish`, `species`, `s13n`, `cvt`, `fmt`,
 * `base64`, `mime`, `nanoid`) beyond the differential tests: one test per deviation of the current Kotlin modules fixed
 * here (ROADMAP appendix E and the rows P2.2 added), the documented examples of the modules the obsolete JS did not have
 * (`s13n.bytes`, `cvt`, `fmt`, `nanoid`), the D10 error types, and the surface against the current Kotlin modules in the
 * same JVM (names, `$` aliases, order on the global object, attributes, kinds and prototypes; `length` may differ,
 * appendix C Q9).
 */
class PureLogicModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        mountInOrder(rhino.ktScope.globalThis, rhino.ktRuntime, newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /**
     * Checks [cases] and adds them to `build/script-modules-device/PureLogicModulesTest.json` for
     * `device_eval.py --new-modules on --strict-errors` (cases that need Android at run time are left out with
     * [export] false; they run on the device in [deviceOnlyColorCases]).
     */
    private fun check(cases: List<Pair<String, String>>, export: Boolean = true) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
        if (export) exportForDevice(cases)
    }

    /** Appendix E: a TimeUnit unit was taken for an options object (ClassCastException); P2.2 rows for s13n. */
    @Test
    fun s13nTimeAcceptsTimeUnits() = check(
        listOf(
            "s13n.time(60, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.TimeUnit.MINUTES)" to "1",
            "s13n.time(1, java.util.concurrent.TimeUnit.DAYS, 'h')" to "24",
            "s13n.time(90, 's', java.util.concurrent.TimeUnit.MINUTES)" to "1.5",
            "s13n.time(1500, java.util.concurrent.TimeUnit.MILLISECONDS)" to "1500",
            "s13n.time(120000, { fromUnit: 'ms', toUnit: java.util.concurrent.TimeUnit.MINUTES })" to "2",
            "s13n.time(2, 'seconds')" to "2000",
            "s13n.time(120000, { fromUnit: 'ms', toUnit: 'minutes' })" to "2",
            "s13n.time(1, 'x')" to "throws TypeError",
            "s13n.time(1, 's', {})" to "throws TypeError",
            "s13n.time(null)" to "throws TypeError",
            "s13n.time('abc')" to "NaN",
            "s13n.time(1, 'ms', 's', 'extra')" to "throws TypeError",
            "s13n.time()" to "throws TypeError",
        )
    )

    /** P2.2 rows: the second coordinate is truncated like the first (the obsolete module), runtime-built strings. */
    @Test
    fun s13nPointsAndThrowables() = check(
        listOf(
            "(function (p) { return [p.x, p.y]; })(s13n.point(1, 2.5))" to "[1,2]",
            "(function (p) { return [p.x, p.y]; })(s13n.point(1.9, -2.9))" to "[1,-2]",
            "(function (p) { return [p.x, p.y]; })(s13n.point(10))" to "[10,10]",
            "(function (p) { return [p.x, p.y]; })(s13n.point({ x: 10, y: 20 }))" to "[10,20]",
            "s13n.point(1, 2, 3)" to "throws TypeError",
            "s13n.point(NaN)" to "throws TypeError",
            "s13n.point(1e10)" to "throws RangeError",
            "s13n.point('1')" to "throws TypeError",
            "(function (s) { return String(s13n.throwable(s + 'x').getMessage()); })('a')" to "\"ax\"",
            "s13n.throwable(123)" to "throws TypeError",
            "s13n.throwable(null)" to "throws TypeError",
            "s13n.color(null)" to "throws TypeError",
            "s13n.color(true)" to "throws TypeError",
            "s13n.color({})" to "throws TypeError",
            "s13n.color()" to "throws TypeError",
            "s13n.color('red', 'extra')" to "throws TypeError",
        )
    )

    /** s13n.bytes (6.7.0, no obsolete JS): documented examples; the result is an integer byte count (P2.2 row). */
    @Test
    fun s13nBytes() = check(
        listOf(
            "s13n.bytes('1.5 KiB')" to "1536",
            "s13n.bytes(2, 'MB')" to "2097152",
            "s13n.bytes(1.5)" to "2",
            "s13n.bytes('1.4 B')" to "1",
            "s13n.bytes('1 KB', 'KiB')" to "1024",
            "s13n.bytes(1, 'MB') === s13n.bytes('1 MB')" to "true",
            "typeof s13n.bytes(1, 'QB')" to "\"bigint\"",
            "String(s13n.bytes(1, 'QB'))" to "\"1267650600228229401496703205376\"",
            "s13n.bytes(null)" to "throws TypeError",
            "s13n.bytes(1, 5)" to "throws TypeError",
            "s13n.bytes(-1)" to "throws RangeError",
            "s13n.bytes('1 XB')" to "throws RangeError",
            "s13n.bytes('1 KB', 'MB')" to "throws RangeError",
            "s13n.bytes(1, 'B', 'extra')" to "throws TypeError",
        )
    )

    /** Documented examples of cvt (api/cvt.md), constants and the forced modes. */
    @Test
    fun cvtDocumentedExamples() = check(
        listOf(
            "cvt.bytes('1.5 MiB', 'KiB')" to "1536",
            "cvt.bytes.strict('1 KB', 'B')" to "1000",
            "cvt.bytes.strict('1 KiB', 'B')" to "1024",
            "cvt.bytes(1536)" to "1.5",
            "cvt.bytes('1536 B')" to "1.5",
            "cvt.bytes('1.5 MiB')" to "1.5",
            "cvt.bytes(1536, 'KB')" to "1.5",
            "cvt.bytes('1.5 MB', 'KB')" to "1536",
            "cvt.bytes('1 KiB', 'B')" to "1024",
            "cvt.bytes(1536, { fromUnit: 'B', toUnit: 'KB', fractionDigits: 3 })" to "1.5",
            "cvt.bytes(1536, 0)" to "2",
            "cvt.bytes(1536, 'KB', 3)" to "1.5",
            "cvt.bytes(1536, 'MB', { toUnit: 'KB' })" to "1.5",
            "cvt.bytes(2, 'MB', 'KB')" to "2048",
            "cvt.bytes.strict(2, 'MB', 'KB')" to "2000",
            "cvt.bytes.strict(2, 'MiB', 'KiB')" to "2048",
            "cvt.bytes.strict(1000, 'B', 'KB')" to "1",
            "cvt.bytes.strict(1024, 'B', 'KiB')" to "1",
            "cvt.bytes.loose('1 KB', 'B')" to "1024",
            "cvt.bytes.loose('1 KiB', 'B')" to "1024",
            "[cvt.bytes.UNITS, cvt.bytes.AUTO, cvt.bytes.IEC_DIV, cvt.bytes.SI_DIV]" to "[\"KMGTPEZYRQ\",\"AUTO\",1024,1000]",
            "cvt.bytes(1536, { strict: true, toUnit: 'KiB' })" to "1.5",
            "cvt.bytes(1048576 * 900, { autoCarryThreshold: 800 })" to "0.88",
            "cvt.bytes(1536, true)" to "1.5",
        )
    )

    /** Documented examples of fmt (api/fmt.md). */
    @Test
    fun fmtDocumentedExamples() = check(
        listOf(
            "fmt.bytes(1536)" to "\"1.50 KB\"",
            "fmt.bytes.strict(1000, 'B', 'KB')" to "\"1.00 KB\"",
            "fmt.bytes.strict(1024, 'B', 'KiB')" to "\"1.00 KiB\"",
            "fmt.bytes('1536 B')" to "\"1.50 KB\"",
            "fmt.bytes('1.5 MiB')" to "\"1.50 MB\"",
            "fmt.bytes(1536, 'KB')" to "\"1.50 KB\"",
            "fmt.bytes('1.5 MB', 'KB')" to "\"1536.00 KB\"",
            "fmt.bytes('1 KiB', 'B')" to "\"1024.00 B\"",
            "fmt.bytes(1536, { toUnit: 'KB', fractionDigits: 3, useSpace: false })" to "\"1.500KB\"",
            "fmt.bytes(1536, 0)" to "\"2 KB\"",
            "fmt.bytes(1536, true)" to "\"1.50 KiB\"",
            "fmt.bytes(1536, 'KB', 3)" to "\"1.500 KB\"",
            "fmt.bytes(1536, 'KB', true)" to "\"1.50 KiB\"",
            "fmt.bytes(1536, 'MB', { toUnit: 'KB' })" to "\"1.50 KB\"",
            "fmt.bytes(2, 'MB', 'KB')" to "\"2048.00 KB\"",
            "fmt.bytes.strict(2, 'MB', 'KB')" to "\"2000.00 KB\"",
            "fmt.bytes.strict(2, 'MiB', 'KiB')" to "\"2048.00 KiB\"",
            "fmt.bytes.strict('1 KB', 'B')" to "\"1000.00 B\"",
            "fmt.bytes.strict('1 KiB', 'B')" to "\"1024.00 B\"",
            "fmt.bytes.loose('1 KB', 'B')" to "\"1024.00 B\"",
            "fmt.bytes.loose(1024, 'KB')" to "\"1.00 KB\"",
            "fmt.bytes.loose(1024, 'KB', true)" to "\"1.00 KiB\"",
            "fmt.bytes(1536, { trimTrailingZero: true })" to "\"1.5 KB\"",
            "fmt.bytes(1024, { trimTrailingZero: true })" to "\"1 KB\"",
            "fmt.bytes(0)" to "\"0.00 B\"",
            "[fmt.bytes.UNITS, fmt.bytes.AUTO, fmt.bytes.IEC_DIV, fmt.bytes.SI_DIV]" to "[\"KMGTPEZYRQ\",\"AUTO\",1024,1000]",
        )
    )

    /** P2.2 rows for cvt / fmt: the deviations of the current Kotlin module fixed here and the D10 error types. */
    @Test
    fun bytesFixesAndErrors() = check(
        listOf(
            // A nullish source was read as 0.
            "cvt.bytes(null)" to "throws TypeError",
            "fmt.bytes(undefined)" to "throws TypeError",
            // fractionDigits applies to the target B (and below 1 KB with AUTO) too.
            "cvt.bytes(1.555)" to "1.56",
            "cvt.bytes(1.555, 'B', 'B')" to "1.56",
            "cvt.bytes.strict(1.555)" to "1.56",
            // A prefix is one letter of UNITS (KMB matched as a substring).
            "cvt.bytes(1, 'KMB', 'B')" to "throws RangeError",
            "cvt.bytes(1, 'B', 'MGB')" to "throws RangeError",
            // Strict AUTO labels the unit by the base it computed with.
            "fmt.bytes.strict(1500000)" to "\"1.50 MB\"",
            "fmt.bytes.strict('1536 KiB')" to "\"1.50 MiB\"",
            "cvt.bytes.strict(1500000)" to "1.5",
            // Beyond 2^53 - 1 an integer is a bigint (documented: number | bigint).
            "typeof cvt.bytes(1, 'QB', 'B')" to "\"bigint\"",
            "typeof cvt.bytes(1, 'TB', 'B')" to "\"number\"",
            // fractionDigits must be a non-negative integer, autoCarryThreshold a positive number (documented).
            "cvt.bytes(1536, { fractionDigits: 1.5 })" to "throws RangeError",
            "cvt.bytes(1536, { fractionDigits: -1 })" to "throws RangeError",
            "cvt.bytes(1536, { fractionDigits: 'x' })" to "throws TypeError",
            "cvt.bytes(1536, { autoCarryThreshold: 0 })" to "throws RangeError",
            "typeof cvt.bytes(1536, { autoCarryThreshold: 0.5 })" to "\"number\"",
            "cvt.bytes(1536, { autoCarryThreshold: 900, toUnit: 'KB' })" to "throws TypeError",
            // Unusable values are RangeError, wrong types TypeError (D10).
            "cvt.bytes(-1)" to "throws RangeError",
            "cvt.bytes(NaN)" to "throws RangeError",
            "cvt.bytes(Infinity)" to "throws RangeError",
            "cvt.bytes('abc')" to "throws RangeError",
            "cvt.bytes('-1 KB')" to "throws RangeError",
            "cvt.bytes(1, 'XB')" to "throws RangeError",
            "cvt.bytes('1 KB', 'MB', 'B')" to "throws RangeError",
            "cvt.bytes(1, null)" to "throws TypeError",
            "cvt.bytes(1, 'KB', 'MB', 'x')" to "throws TypeError",
            "cvt.bytes.strict(1, { strict: true })" to "throws TypeError",
            "cvt.bytes.loose(1, { strict: false })" to "throws TypeError",
            "fmt.bytes.strict(1536, 'KB', true)" to "throws TypeError",
            "fmt.bytes.strict(1536, { useIecIdentifier: false })" to "throws TypeError",
            "cvt.bytes()" to "throws TypeError",
            "cvt.bytes(1, 2, 3, 4, 5)" to "throws TypeError",
            "fmt.bytes(1, 'B', 'KB', 2, 'extra')" to "throws TypeError",
            "(function () { try { cvt.bytes(-1); } catch (e) { return e instanceof RangeError; } })()" to "true",
        )
    )

    /** Appendix E for mime: a script object again, the obsolete toString, TypeError for non-strings. */
    @Test
    fun mimeResults() = check(
        listOf(
            "Object.keys(mime('text/plain'))" to "[\"raw\",\"type\",\"subtype\",\"mimeType\",\"mimeTypeRefined\",\"parameters\"]",
            "JSON.stringify(mime('text/plain; a=1'))" to "\"{\\\"raw\\\":\\\"text/plain; a=1\\\",\\\"type\\\":\\\"text\\\",\\\"subtype\\\":\\\"plain\\\",\\\"mimeType\\\":\\\"text/plain; a=1\\\",\\\"mimeTypeRefined\\\":\\\"text/plain\\\",\\\"parameters\\\":{\\\"a\\\":\\\"1\\\"}}\"",
            "String(mime('text/html; charset=UTF-8'))" to "\"JsMime {\\n  type: 'text',\\n  subtype: 'html',\\n  mimeType: 'text/html; charset=UTF-8',\\n  mimeTypeRefined: 'text/html',\\n  parameters: { charset: 'UTF-8' },\\n  raw: 'text/html; charset=UTF-8',\\n}\"",
            "mime('text/plain').toStringReadable() === String(mime('text/plain'))" to "true",
            "mime('text/plain').constructor.name" to "\"JsMime\"",
            "mime('text/plain') instanceof mime('text/plain').constructor" to "true",
            "Object.getPrototypeOf(mime('a/b')) === Object.getPrototypeOf(mime('c/d'))" to "true",
            "(function (d) { return [d.enumerable, d.writable]; })(Object.getOwnPropertyDescriptor(mime('text/plain'), 'type'))" to "[true,false]",
            "mime('application/json; charset=utf-8').parameters.charset" to "\"utf-8\"",
            "mime('text/plain; 1=a').parameters[1]" to "\"a\"",
            "mime(123)" to "throws TypeError",
            "mime(null)" to "throws TypeError",
            "mime()" to "throws TypeError",
            "mime('text/plain', 'extra')" to "throws TypeError",
            "mime.TEXT_PLAIN" to "\"text/plain\"",
            "mime.WILDCARD" to "\"*/*\"",
            "String(mime.getType('text/html'))" to "\"text\"",
            "\$mime === mime" to "true",
            "typeof mime" to "\"function\"",
        )
    )

    /** nanoid (6.6.0, no obsolete JS): documented behavior and the D10 errors (P2.2 rows). */
    @Test
    fun nanoidSizes() = check(
        listOf(
            "nanoid().length" to "21",
            "nanoid(8).length" to "8",
            "nanoid(32).length" to "32",
            "nanoid(0)" to "\"\"",
            "nanoid(8.7).length" to "8",
            "nanoid('5').length" to "5",
            "nanoid(undefined).length" to "21",
            "nanoid(null).length" to "21",
            "/^[A-Za-z0-9_-]{64}$/.test(nanoid(64))" to "true",
            "nanoid() !== nanoid()" to "true",
            "nanoid(-1)" to "throws RangeError",
            "nanoid(NaN)" to "throws RangeError",
            "nanoid(Infinity)" to "throws RangeError",
            "nanoid('abc')" to "throws TypeError",
            "nanoid(true)" to "throws TypeError",
            "nanoid(1, 2)" to "throws TypeError",
            "\$nanoid === nanoid" to "true",
        )
    )

    /** species and isNullish (no obsolete JS): results as the current module, the global functions, D10 errors. */
    @Test
    fun speciesAndIsNullish() = check(
        listOf(
            "[null, undefined, true, 'a', 1, [], {}, function () {}, new Date(0), /x/, new Error('e')].map(function (v) { return species(v); })" to
                    "[\"Null\",\"Undefined\",\"Boolean\",\"String\",\"Number\",\"Array\",\"Object\",\"Function\",\"Date\",\"RegExp\",\"Error\"]",
            "(function (s) { return species(s + 'x'); })('a')" to "\"String\"",
            "[species(Symbol('s')), species.isJavaObject(Symbol('s'))]" to "[\"Symbol\",false]",
            "[species(java.lang.String), species(java), species(new java.lang.Object())]" to "[\"JavaClass\",\"JavaPackage\",\"JavaObject\"]",
            "[species.isArray([]), species.isObject({}), species.isObject([]), species.isString('a'), species.isNull(null)]" to "[true,true,false,true,true]",
            "[isJavaClass(java.lang.String), isJavaPackage(java), isObject({}), isObject(1)]" to "[true,true,true,false]",
            "isObject === species.isObject && isJavaClass === species.isJavaClass" to "true",
            "species()" to "throws TypeError",
            "species(undefined)" to "\"Undefined\"",
            "species.isArray()" to "throws TypeError",
            "species.isArray([], 1)" to "throws TypeError",
            "[isNullish(), isNullish(null), isNullish(undefined), isNullish(0), isNullish('')]" to "[true,true,true,false,false]",
            "[1, null, 2].some(isNullish)" to "true",
            "[1, 2].filter(isNullish).length" to "0",
            "typeof \$isNullish" to "\"undefined\"",
            "\$species === species" to "true",
        )
    )

    /**
     * Color conversion needs `android.graphics.Color` (no JVM fake, appendix E), so these cases only run on the device;
     * the values follow the documentation (`s13n.color('red') === s13n.color('#F00')`, ColorInt of red is -65536).
     */
    @Test
    fun deviceOnlyColorCases() {
        exportForDevice(
            listOf(
                "s13n.color('red') === s13n.color('#F00')" to "true",
                "s13n.color('#FB663399') === s13n.color(0xFB663399)" to "true",
                "s13n.color('red')" to "-65536",
                "s13n.color(0xFF0000)" to "16711680",
                "s13n.color(Color('orange')) === s13n.color('orange')" to "true",
                "s13n.color(Color('orange').toHex()) === s13n.color('orange')" to "true",
                "s13n.color('no-such-color')" to "throws RangeError",
                "typeof s13n.color(autojs.themeColor)" to "\"number\"",
            ),
        )
    }

    /** Surface against the current Kotlin modules in the same JVM (see the class comment); function lengths are left out. */
    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = rhino.evalKt(DESCRIBE).toString()
        val expected = DifferentialRhino().use { legacy ->
            mountInOrder(legacy.ktScope.globalThis, legacy.ktRuntime, newLayer = false)
            legacy.evalKt(DESCRIBE).toString()
        }
        assertEquals(expected.replace(';', '\n'), actual.replace(';', '\n'))
    }

    companion object {

        /** The top-level modules of this stage in their mounting order, after `legacy` (which binds `isObject` first). */
        private val MODULES = listOf("legacy", "isNullish", "species", "s13n", "cvt", "fmt", "base64", "mime", "nanoid")

        /**
         * Mounts [MODULES] in the order of `ScriptModuleLayer.ORDER` the way `ScriptRuntime.augment()` does: from the new
         * layer when [newLayer] is true (`legacy` too since P3.0), otherwise all from their old augment calls.
         */
        private fun mountInOrder(global: ScriptableObject, runtime: ScriptRuntime, newLayer: Boolean) {
            val mounter = if (newLayer) ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global, order = ScriptModuleLayer.ORDER) else null
            ScriptModuleLayer.TOP_LEVEL_ORDER.filter { it in MODULES }.forEach { name ->
                ScriptModuleLayer.mountOr(mounter, name) {
                    when (name) {
                        "legacy" -> Legacy(runtime).assignWithRuntime(global, runtime)
                        "isNullish" -> IsNullish.augmentWithRuntime(global, runtime)
                        "species" -> Species.augmentWithRuntime(global, runtime)
                        "s13n" -> S13n.augmentWithRuntime(global, runtime)
                        "cvt" -> Converter.augmentWithRuntime(global, runtime).also { BytesCvt.augmentWithRuntime(it, runtime) }
                        "fmt" -> Formatter.augmentWithRuntime(global, runtime).also { BytesFmt.augmentWithRuntime(it, runtime) }
                        "base64" -> Base64.augmentWithRuntime(global, runtime)
                        "mime" -> Mime(runtime).augment(global, ApiMime)
                        "nanoid" -> NanoID.augmentWithRuntime(global, runtime)
                        else -> throw AssertionError(name)
                    }
                }
            }
        }

        /** Every exported case so far (JUnit creates an instance per test method). */
        private val exported = LinkedHashMap<String, String>()

        /** Writes the exported cases in the format of `tools/augment/device_eval.py` (expected values in "kt"). */
        @Synchronized
        private fun exportForDevice(cases: List<Pair<String, String>>) {
            cases.forEach { (expression, expected) -> exported[expression] = expected }
            val dir = File("build/script-modules-device").apply { mkdirs() }
            val entries = exported.entries.joinToString(",\n") { (expression, expected) ->
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P2.2)\"}"
            }
            File(dir, "PureLogicModulesTest.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
        }

        /** A JSON string literal; the expressions are ASCII, so only quotes, backslashes and newlines need escaping. */
        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                when (c) {
                    '"', BACKSLASH -> append(BACKSLASH).append(c)
                    '\n' -> append(BACKSLASH).append('n')
                    else -> append(c)
                }
            }
            append('"')
        }

        private const val BACKSLASH = '\\'

        private val DESCRIBE = """
            (function () {
                var out = [];
                var names = ['isObject', 'isObjectSpecies', 'isNullish', '${'$'}isNullish', 'isJavaClass', 'isJavaPackage', 'species', '${'$'}species',
                    's13n', '${'$'}s13n', 'cvt', '${'$'}cvt', 'fmt', '${'$'}fmt', 'base64', '${'$'}base64', 'mime', '${'$'}mime', 'nanoid', '${'$'}nanoid'];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                function tag(v) { return v === null ? 'null' : v === Object.prototype ? 'Object.prototype' : v === Function.prototype ? 'Function.prototype' : Object.prototype.toString.call(v); }
                function chain(v) { var c = []; for (var p = Object.getPrototypeOf(v), i = 0; p !== null && i < 4; p = Object.getPrototypeOf(p), i++) c.push(tag(p)); return c.join(' > '); }
                function own(v) {
                    return Object.getOwnPropertyNames(v).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; }).map(function (k) {
                        return k + ' ' + attrs(Object.getOwnPropertyDescriptor(v, k));
                    });
                }
                function kind(v) {
                    if (typeof v === 'function') {
                        var extra = own(v);
                        return 'function ' + v.name + (extra.length ? ' [' + extra.join(',') + ']' : '');
                    }
                    return typeof v === 'object' ? 'object' : typeof v + ' ' + String(v);
                }
                names.forEach(function (n) {
                    var d = Object.getOwnPropertyDescriptor(globalThis, n);
                    out.push(n + ' ' + (d ? attrs(d) + ' ' + kind(d.value) : 'missing'));
                });
                out.push('keys ' + Object.keys(globalThis).filter(function (k) { return names.indexOf(k) >= 0; }).join(','));
                [['species', species], ['s13n', s13n], ['cvt', cvt], ['cvt.bytes', cvt.bytes], ['fmt', fmt], ['fmt.bytes', fmt.bytes], ['base64', base64],
                    ['mime', mime], ['nanoid', nanoid], ['isNullish', isNullish]].forEach(function (e) {
                    var label = e[0], o = e[1];
                    out.push(label + ' keys ' + Object.keys(o).join(','));
                    out.push(label + ' proto ' + chain(o));
                    Object.getOwnPropertyNames(o).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; }).forEach(function (k) {
                        var d = Object.getOwnPropertyDescriptor(o, k);
                        out.push(label + '.' + k + ' ' + attrs(d) + ' ' + kind(d.value));
                    });
                });
                out.push('aliases ' + [${'$'}species === species, ${'$'}s13n === s13n, ${'$'}cvt === cvt, ${'$'}fmt === fmt, ${'$'}base64 === base64, ${'$'}mime === mime, ${'$'}nanoid === nanoid].join(','));
                out.push('mime members ' + ['TEXT_PLAIN', 'WILDCARD', 'fromFile', 'getMediaType', 'parseMediaType', 'getType'].map(function (k) { return k + ':' + typeof mime[k]; }).join(','));
                return out.join(';');
            })()
        """.trimIndent()

    }

}
