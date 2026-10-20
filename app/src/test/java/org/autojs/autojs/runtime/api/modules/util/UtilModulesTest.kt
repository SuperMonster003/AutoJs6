package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.core.image.ColorTable
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.util.Inspect
import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.runtime.api.augment.util.MorseCode
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.api.augment.util.Version
import org.autojs.autojs.runtime.api.augment.util.VersionCodes
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import kotlin.system.measureTimeMillis
import org.autojs.autojs.runtime.api.Util as ApiUtil

/**
 * Roadmap P2.3: `util` and its sub-modules (`java`, `version`, `versionCodes`, `inspect`, `morseCode`) of the new script
 * module layer beyond the differential test against the obsolete JS: one test per deviation fixed here (ROADMAP appendix
 * E), the behavior the current Kotlin module added and the documentation keeps (inspect limits, versionCodes, morseCode),
 * the D10 error types, the time of `util.inspect` on large values, and the surface against the current Kotlin modules in
 * the same JVM (names, order, attributes, kinds, prototypes; `length` may differ, appendix C Q9). Console functions
 * (`log`, `deprecate`, `debuglog`) and `vibrate` need Android and are only exported for the device run.
 */
class UtilModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("util")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
        exportForDevice(cases)
    }

    /** Appendix E: the legacy Node format of the obsolete module, restored. */
    @Test
    fun inspectRestoresTheObsoleteFormat() = check(
        listOf(
            "util.inspect('a\\'b')" to "\"'a\\\\'b'\"",
            "util.inspect('a\"b')" to "\"'a\\\"b'\"",
            "util.inspect('a' + 'b')" to "\"'ab'\"",
            "util.inspect({ a: 1, 'b-c': 2, _d: 3, '1': 4 })" to "\"{ '1': 4, a: 1, 'b-c': 2, _d: 3 }\"",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, false, 0)" to "\"{ a: [Object] }\"",
            "util.inspect(1, false, 2, true)" to "\"\\u001b[33m1\\u001b[39m\"",
            "util.inspect()" to "\"undefined\"",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: null })" to "\"{ a: { b: { c: { d: 1 } } } }\"",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: Infinity })" to "\"{ a: { b: { c: { d: 1 } } } }\"",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: undefined })" to "\"{ a: { b: { c: [Object] } } }\"",
            "util.inspect({ nested: { longPropertyName1: 'some long value', longPropertyName2: 'another long value' } })" to
                    "\"{ nested: \\n   { longPropertyName1: 'some long value',\\n     longPropertyName2: 'another long value' } }\"",
            "util.inspect(function () { })" to "\"[Function]\"",
            "util.inspect(function foo() { })" to "\"[Function foo()]\"",
            "util.inspect(Object.assign(/re/, { p: 1 }))" to "\"{ /re/ p: 1 }\"",
            "util.inspect(new Error('boom'))" to "\"[Error: boom]\"",
            "util.inspect(Object.assign(new Error('e'), { code: 1 }))" to "\"{ [Error: e] code: 1 }\"",
            "util.format('x', new Error('e'))" to "\"x [Error: e]\"",
            "util.inspect(java.lang.Integer.valueOf(7))" to "\"[JavaObject: 7]\"",
            "util.inspect(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2))" to "\"[0, 0]\"",
            "util.inspect({ inspect: function () { return 'custom'; } }, { customInspect: true })" to "\"custom\"",
            "util.inspect({ toString: function (radix) { return 'TS'; } })" to "\"TS\"",
        ),
    )

    /** Kept from the current module (appendix E "keep Kotlin" rows and the documented options). */
    @Test
    fun inspectKeepsTheCurrentAdditions() = check(
        listOf(
            "util.inspect(java.lang.String)" to "\"class java.lang.String\"",
            "util.inspect(new java.util.ArrayList())" to "\"[]\"",
            "util.inspect({ inspect: function () { return 'custom'; } })" to "\"{ inspect: [Function inspect()] }\"",
            "util.inspect.colors.red" to "[31,39]",
            "util.inspect.styles.string" to "\"green\"",
            "util.inspect('abcdef', { maxStringLength: 4 })" to "\"'abc... (4 more chars)\"",
            "util.inspect({ a: 1, b: 2, c: 3 }, { maxObjectKeys: 2 })" to "\"{ a: 1, b: 2 }\"",
            "(function () { var a = []; for (var i = 0; i < 150; i++) a.push(i); return util.inspect(a).indexOf('more items'); })()" to "-1",
            "(function () { var a = []; for (var i = 0; i < 150; i++) a.push(i); return util.inspect(a, { maxArrayItems: 120 }).indexOf('... 30 more items ...') > 0; })()" to "true",
            "util.inspect(new Proxy(function proxied() { }, { }))" to "\"[Function proxied()]\"",
            "util.inspect({ tag: 'proxy-to-string', toString: new Proxy(function () { return this.tag; }, { }) })" to "\"proxy-to-string\"",
            "util.inspect(util.versionCodes).split('\\n')[0]" to "\"CINNAMON_BUN: 37 / Android 17 / 17\"",
        ),
    )

    /** Appendix E: typeof semantics, instanceof Error, %d in JS number form, RegExp and toString in checkStringParam. */
    @Test
    fun predicatesAndHelpersFollowTheDocumentation() = check(
        listOf(
            "util.isString(new java.lang.String('j'))" to "false",
            "util.isString('a' + 'b')" to "true",
            "util.isError(Object.create(Error.prototype))" to "true",
            "util.isError({ message: 'm' })" to "false",
            "util.format('%d', 1e21)" to "\"1e+21\"",
            "util.format('%d', 0.1 + 0.2)" to "\"0.30000000000000004\"",
            "util.checkStringParam('abc', /b/)" to "true",
            "util.checkStringParam('abc', /^b$/)" to "false",
            "util.checkStringParam({ toString() { return 'abc'; } }, 'abc')" to "true",
            "util.checkStringParam('ab', 'a|b')" to "true",
            "util.checkStringArgument('abc', /B/)" to "true",
            "util.checkStringArgument(' ABC ', 'abc')" to "true",
            "util.checkStringArgument({ toString() { return 'abc'; } }, 'abc')" to "throws TypeError",
            "util.assureStringStartsWith('b' + 'c', 'a')" to "\"abc\"",
            "util.assureStringSurroundsWith('b', '[', null)" to "\"[b[\"",
            "util.toRegular((a, b) => [a, b])(1, 2)" to "[1,2]",
            "(function () { var f = util.toRegular(() => 1); return typeof f === 'function' && typeof f.call === 'function' && f.toString() === (() => 1).toString(); })()" to "true",
            "util.toRegularAndApply()" to "throws JavaException ObsoletedRhinoFunctionException",
            "(function () { try { util.toRegularAndApply(); } catch (e) { return String(e.javaException.getMessage()).indexOf('toRegularAndApply') >= 0; } })()" to "true",
        ),
    )

    /** Java values come back as Java objects (arrays, maps), wrapped Java arguments are unwrapped. */
    @Test
    fun javaInteroperability() = check(
        listOf(
            "String(util.java.array('string', 2).getClass().getName())" to "\"[Ljava.lang.String;\"",
            "String(util.java.array('int', 3, 4).getClass().getName())" to "\"[[I\"",
            "util.java.array('boolean', 1)[0]" to "false",
            "util.java.array('int')" to "throws TypeError",
            "(function () { var m = util.java.objectToMap({ a: 1 }); return [m.size(), m.containsKey('a')]; })()" to "[1,true]",
            "(function () { var m = new java.util.HashMap(); m.put('k', 'v'); return util.java.mapToObject(m).k; })()" to "\"v\"",
            "(function () { var l = new java.util.ArrayList(); l.add('x'); return util.java.toJsArray(l); })()" to "[\"x\"]",
            "util.java.toJsArray(undefined, true)" to "[]",
            "util.java.toJsArray(1)" to "throws TypeError",
            "util.java.mapToObject({ a: 1 }).a" to "1",
            "util.java.mapToObject(1)" to "throws TypeError",
            "util.java.objectToMap([])" to "throws TypeError",
            "util.java.instanceof(null, java.lang.Object)" to "throws TypeError",
            "util.java.instanceof('x', null)" to "false",
            "util.java.instanceOf === util.java.instanceof" to "true",
        ),
    )

    /** versionCodes as documented; a Date matches the release timestamp (the current module never matched it). */
    @Test
    fun versionCodes() = check(
        listOf(
            "util.versionCodes.TIRAMISU.apiLevel" to "33",
            "util.versionCodes.TIRAMISU.valueOf()" to "33",
            "util.versionCodes.S > util.versionCodes.R" to "true",
            "util.versionCodes.TIRAMISU === util.versionCodes.TIRAMISU" to "false",
            "Object.keys(util.versionCodes.TIRAMISU)" to "[\"versionCode\",\"apiLevel\",\"releaseName\",\"platformVersion\",\"internalCodename\",\"releaseDate\",\"releaseTimestamp\"]",
            "util.versionCodes.search(33).versionCode" to "\"TIRAMISU\"",
            "util.versionCodes.search('Tiramisu').apiLevel" to "33",
            "util.versionCodes.search('12').versionCode" to "\"S_V2\"",
            "util.versionCodes.search(null)" to "null",
            "util.versionCodes.search('no such version')" to "null",
            "util.versionCodes.searchAll('Oreo').map(v => v.versionCode)" to "[\"O_MR1\",\"O\"]",
            "util.versionCodes.searchAll(null)" to "[]",
            "util.versionCodes.search(new Date(util.versionCodes.TIRAMISU.releaseTimestamp)).versionCode" to "\"TIRAMISU\"",
            "util.versionCodes.search({})" to "throws TypeError",
            "util.versionCodes.summary().split('\\n').length === util.versionCodes.summary(true).split('\\n').length" to "true",
            "util.versionCodes.summary(true).split('\\n')[0].split(', ').length" to "8",
            "util.versionCodes.toString() === util.versionCodes.summary()" to "true",
            "typeof util.version.sdkInt" to "\"number\"",
        ),
    )

    @Test
    fun morseCode() = check(
        listOf(
            "util.morseCode.getCode('SOS')" to "\"\u00B7\u00B7\u00B7   ---   \u00B7\u00B7\u00B7\"",
            "util.morseCode.getPattern('E T')" to "[100,700,300]",
            "util.morseCode.getPattern('E', 249.6)" to "[250]",
            "util.morseCode.getPattern('E', 50)" to "[100]",
            "util.morseCode('sos').getPattern().length === util.morseCode('sos').pattern.length" to "true",
            "util.morseCode('E').toString()" to "\"MorseCode {\\n  code: '\u00B7',\\n  pattern: [100],\\n}\"",
            "util.morseCode('E').getCode(1)" to "throws TypeError",
            "util.morseCode('#')" to "throws RangeError",
            "util.morseCode('E', 'x')" to "throws RangeError",
            "util.morseCode()" to "throws TypeError",
        ),
    )

    /** D10 error types and T4 argument counts. */
    @Test
    fun argumentErrors() = check(
        listOf(
            "util.isArray()" to "throws TypeError",
            "util.isArray([], 1)" to "throws TypeError",
            "util.inspect(1, 'options')" to "throws TypeError",
            "util.inspect(1, false, 'deep')" to "throws TypeError",
            "util.inspect(1, false, 2, 'yes')" to "throws TypeError",
            "util.inspect(1, {}, 2, false, 'extra')" to "throws TypeError",
            "util.inspect({}, { depth: 'x' })" to "throws TypeError",
            "util.ensureType(1, 'string')" to "throws TypeError",
            "util.ensureType(1, 'nonsense')" to "throws RangeError",
            "util.ensureType(1, 2)" to "throws TypeError",
            "util.ensureStringType('a', 1)" to "throws TypeError",
            "util.ensureNonNullObjectType(null)" to "throws TypeError",
            "util.checkStringParam(null, 'x')" to "throws TypeError",
            "util.checkStringParam('x', 1)" to "throws TypeError",
            "util.assureStringStartsWith(1, 'a')" to "throws TypeError",
            "util.extend(1, null)" to "throws TypeError",
            "util.toRegular(1)" to "throws TypeError",
            "util.dpToPx('x')" to "throws TypeError",
            "util.__assignFunctions__({}, {}, ['nope'])" to "throws TypeError",
        ),
    )

    /**
     * `util.inspect` of a large value (100 objects with nested values, printed alike by both implementations); the time of
     * the current module in the same JVM is printed next to it.
     */
    @Test
    fun inspectOfLargeValues() {
        val value = rhino.evalKt(
            """
            (function () {
                var a = [];
                for (var i = 0; i < 100; i++) a.push({ id: i, name: ['item', i].join(' '), tags:['x', 'y'], nested: { ok: i % 2 === 0, list: [i, i + 1] } });
                return a;
            })()
            """.trimIndent(),
        )
        val inspect = rhino.evalKt("util.inspect") as org.mozilla.javascript.Callable
        fun runNew() = inspect.call(rhino.context, rhino.ktScope, rhino.ktScope.globalThis, arrayOf(value)).toString()
        val text = runNew()
        repeat(3) { runNew(); Inspect.inspectRhino(value) }
        val newMs = measureTimeMillis { repeat(10) { runNew() } }
        val oldMs = measureTimeMillis { repeat(10) { Inspect.inspectRhino(value) } }
        println("util.inspect of 100 nested objects, 10 runs: new layer $newMs ms, current module $oldMs ms")
        assertTrue(text, text.startsWith("[ { id: 0,\n    name: 'item 0',"))
        assertEquals(text.length, Inspect.inspectRhino(value).length)
    }

    /** Members, order, attributes and kinds against the current Kotlin modules in the same JVM (length aside). */
    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = rhino.evalKt(DESCRIBE).toString()
        val expected = DifferentialRhino().use { legacy ->
            val runtime = legacy.ktRuntime
            val util = Util.augmentWithRuntime(legacy.ktScope.globalThis, runtime, ApiUtil)
            Java.augmentWithRuntime(util, runtime)
            Version.augmentWithRuntime(util, runtime)
            VersionCodes.augmentWithRuntime(util, runtime, legacyVersionCodes(legacy))
            Inspect.augmentWithRuntime(util, runtime)
            MorseCode.augmentWithRuntime(util, runtime)
            legacy.evalKt(DESCRIBE).toString()
        }
        assertEquals(expected.replace(';', '\n'), actual.replace(';', '\n'))
    }

    /** The prototype the current module gives `util.versionCodes`: a read-only entry per version code. */
    private fun legacyVersionCodes(legacy: DifferentialRhino): NativeObject = (legacy.context.newObject(legacy.ktScope) as NativeObject).apply {
        VersionCodesInfo.list.forEach { info ->
            defineProperty(info.versionCode, { info.toNativeObject() }, null, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
    }

    /** Cases that need Android, exported for the device run with their expected values. */
    @Test
    fun deviceCases() {
        exportForDevice(
            listOf(
                "util.log('x %d', 1)" to "undefined",
                "util.deprecate(function () { })" to "undefined",
                "util.debuglog('x')" to "undefined",
                "util.version.sdkInt === android.os.Build.VERSION.SDK_INT" to "true",
                "util.versionCodes.search(android.os.Build.VERSION.SDK_INT).apiLevel === android.os.Build.VERSION.SDK_INT" to "true",
                "typeof util.dpToPx(10)" to "\"number\"",
                "Math.round(util.pxToDp(util.dpToPx(10)))" to "10",
                "util.morseCode.vibrate('E', -5)" to "undefined",
                "util.morseCode('E').vibrate()" to "undefined",
                "util.inspect(colors).indexOf('toInt') > 0" to "true",
            ),
        )
        assertTrue(ColorTable.Android.RED != 0)
    }

    companion object {

        private val exported = LinkedHashMap<String, String>()

        @Synchronized
        private fun exportForDevice(cases: List<Pair<String, String>>) {
            cases.forEach { (expression, expected) -> exported[expression] = expected }
            val dir = File("build/script-modules-device").apply { mkdirs() }
            val entries = exported.entries.joinToString(",\n") { (expression, expected) ->
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P2.3)\"}"
            }
            File(dir, "UtilModulesTest.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
        }

        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                when {
                    c == '"' || c == BACKSLASH -> append(BACKSLASH).append(c)
                    c.code > 0x7e || c < ' ' -> append(BACKSLASH).append("u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

        private const val BACKSLASH = '\\'

        private val DESCRIBE = """
            (function () {
                var out = [];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                function kind(v) {
                    if (typeof v === 'function') return 'function ' + v.name;
                    if (v === null) return 'null';
                    if (typeof v === 'object') return Object.prototype.toString.call(v) === '[object JavaObject]' ? 'java ' + String(v.getClass().getName()) : 'object';
                    return typeof v + ' ' + String(v);
                }
                function walk(label, o, only) {
                    (only || Object.getOwnPropertyNames(o)).forEach(function (k) {
                        if (['length', 'arity', 'name'].indexOf(k) >= 0) return;
                        var d = Object.getOwnPropertyDescriptor(o, k);
                        out.push(label + '.' + k + ' ' + attrs(d) + ' ' + kind(d.value));
                    });
                }
                function chain(label, o) {
                    var names = [];
                    for (var p = Object.getPrototypeOf(o); p; p = Object.getPrototypeOf(p)) {
                        var cls = Object.prototype.toString.call(p);
                        names.push(cls === '[object JavaObject]' ? String(p.getClass().getName()) : p === Object.prototype ? 'Object.prototype' : p === Function.prototype ? 'Function.prototype' : cls + '(' + Object.getOwnPropertyNames(p).length + ')');
                    }
                    out.push(label + ' proto ' + names.join(' > '));
                }
                walk('global', globalThis, ['util', '${'$'}util']);
                out.push('util keys ' + Object.keys(util).join());
                walk('util', util);
                chain('util', util);
                ['java', 'version', 'versionCodes', 'inspect', 'morseCode'].forEach(function (n) {
                    walk('util.' + n, util[n]);
                    out.push('util.' + n + ' keys ' + Object.keys(util[n]).join());
                    chain('util.' + n, util[n]);
                });
                out.push('util.versionCodes proto keys ' + Object.getOwnPropertyNames(Object.getPrototypeOf(util.versionCodes)).join());
                return out.join(';');
            })()
        """.trimIndent()

    }

}
