package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.ScriptModuleLayer
import org.autojs.autojs.runtime.api.modules.ScriptModuleMounter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Roadmap P2.1: the jsox modules of the new script module layer (`Arrayx`, `Numberx`, `Mathx`, `jsox`) beyond the
 * differential tests against the obsolete JS: one test per deviation of the current Kotlin module fixed here (ROADMAP
 * appendix E and the rows P2.1 added), the documented behavior of the members the obsolete JS did not have, the
 * built-in extension (`jsox` and the "extend JavaScript built-in objects" option), the D10 error types, and the surface
 * against the current Kotlin modules in the same JVM (names, order, attributes, kinds; `length` may differ, appendix C Q9).
 */
class JsoxModulesTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var mounter: ScriptModuleMounter

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        mounter = rhino.mountNewModules("Arrayx", "Numberx", "Mathx", "jsox")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /**
     * Checks [cases] and, with [export], adds them to `build/script-modules-device/JsoxModulesTest.json` for
     * `device_eval.py --new-modules on --strict-errors` (all exported cases run in one script on the device, so only cases
     * that do not depend on what ran before are exported; cases after the built-in extension are prefixed with
     * `jsox.extendAll()`, which is idempotent).
     */
    private fun check(cases: List<Pair<String, String>>, export: Boolean = true, extended: Boolean = false) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression)
            if (actual.key == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
        if (export) exportForDevice(cases.map { (expression, expected) -> (if (extended) "(jsox.extendAll(), $expression)" else expression) to expected })
    }

    private fun extendAll() = ScriptModuleLayer.extendBuiltInsOr(mounter) { throw AssertionError("jsox must be served by the new layer") }

    /** Appendix E: calcBy passed the arguments as one vararg element, so the fraction was summed as a number. */
    @Test
    fun mathxAppliesTheFractionInsteadOfSummingIt() = check(
        listOf(
            "Mathx.sum([1.111, 2.222], 1)" to "3.3",
            "Mathx.sum([1.01, 2.02], 2)" to "3.03",
            "Mathx.avg([1, 2], 3)" to "1.5",
            "Mathx.mean([1.01, 2.02], 1)" to "1.5",
            "Mathx.median([1.25, 2.5, 3.75], 1)" to "2.5",
            "Mathx.var([1, 2, 3, 4], 2)" to "1.25",
            "Mathx.std([1, 2, 3, 4], 3)" to "1.118",
            "Mathx.cv([1, 2, 3, 4], 3)" to "0.516",
            "Mathx.mode([1.25, 1.25, 2], 1)" to "1.3",
            "Mathx.max([1.26, 5.55], 1)" to "5.5",
            "Mathx.min([1.26, 5.55], 1)" to "1.3",
            "Mathx.max([0.1, 0.2, 0.309], 2)" to "0.31",
            "Mathx.sum([1, 2], undefined)" to "3",
            "Mathx.sum([1.5, 2], '1')" to "3.5",
            "Mathx.sum(1, 2)" to "3",
            "Mathx.sum([1.5, 2], -1)" to "throws RangeError",
        )
    )

    /** Appendix E (median) and appendix C Q11 (sum / max / min, by the documentation): empty inputs. */
    @Test
    fun mathxEmptyInputs() = check(
        listOf(
            "Mathx.median([])" to "NaN",
            "Mathx.median()" to "NaN",
            "Mathx.mode([])" to "NaN",
            "Mathx.mode()" to "NaN",
            "Mathx.sum([])" to "NaN",
            "Mathx.sum()" to "NaN",
            "Mathx.avg([])" to "NaN",
            "Mathx.var([])" to "NaN",
            "Mathx.std([])" to "NaN",
            "Mathx.cv([])" to "NaN",
            "Mathx.max([])" to "-Infinity",
            "Mathx.max()" to "-Infinity",
            "Mathx.min([])" to "Infinity",
            "Mathx.min()" to "Infinity",
        )
    )

    /** P2.1 rows of appendix E: elements as Number(x), holes skipped, NaN in ranges ignored, rounding of NaN / Infinity. */
    @Test
    fun mathxElementConversionRangesAndRounding() = check(
        listOf(
            "Mathx.sum(1, null)" to "1",
            "Mathx.sum([1, , 2])" to "3",
            "Mathx.sum(true, '2', [3])" to "6",
            "Mathx.max(7, 8, 9, NaN)" to "NaN",
            "Mathx.randInt(1, NaN)" to "1",
            "(function () { var r = Mathx.randomInt(4, null, NaN); return r >= 0 && r <= 4; })()" to "true",
            "Mathx.randomInt(NaN, NaN)" to "throws RangeError",
            "Mathx.randomFloat('x', NaN)" to "throws RangeError",
            "Mathx.randInt(1.2, 1.8)" to "throws RangeError",
            "Mathx.randInt('x')" to "throws TypeError",
            "Mathx.randInt(null)" to "throws TypeError",
            "Mathx.roundLog(2, -1)" to "NaN",
            "Mathx.roundLog(1, 2)" to "Infinity",
            "Mathx.roundPow(2, -1)" to "NaN",
            "Mathx.roundLog(2, 1500)" to "11",
        )
    )

    /** Appendix E (Numberx): parsePercent ignores inner whitespace, clamp / clampTo ignore bounds that are not numbers. */
    @Test
    fun numberxRestoresTheObsoleteParsingAndBounds() = check(
        listOf(
            "Numberx.parsePercent(' 5 0 % ')" to "0.5",
            "Numberx.parsePercent('1\\t0\\n0%')" to "1",
            "Numberx.clamp(5, 'x', 3)" to "3",
            "Numberx.clamp(5, [null, 3])" to "3",
            "Numberx.clamp(10, [false, 'Hi', [-1], '7'])" to "7",
            "Numberx.clamp(10, ['50%', 0])" to "0.5",
            "Numberx.clamp(-1, [{ valueOf: () => 2 }, 4])" to "2",
            "Numberx.clamp(5, 'x')" to "5",
            "Numberx.clampTo(372, [0, 'x', 360])" to "12",
            "Numberx.clampTo(5, ['x'])" to "5",
        )
    )

    /** P2.1 rows of appendix E (Numberx), by the documentation and the obsolete behavior. */
    @Test
    fun numberxDocumentedBehavior() = check(
        listOf(
            "Numberx.check('9', '9')" to "false",
            "Numberx.check(9, 2 + 7)" to "true",
            "Numberx.check(1, '1')" to "false",
            "Numberx.toFixedNum(1.23456e3)" to "1235",
            "Numberx.toFixedNum(9.5995)" to "10",
            "Numberx.padStart(1e21, 9)" to "\"00001e+21\"",
            "Numberx.padStart(1e-7, 6)" to "\"001e-7\"",
            "Numberx.padStart(7, 2.9)" to "\"07\"",
            "Numberx.padEnd(5, 3, { toString: () => '*' })" to "\"5**\"",
            "Numberx.parseRatio('3px:2')" to "1.5",
            "Numberx.parseRatio('0x10:2')" to "0",
            "Numberx.parsePercent(1e21)" to "1e+21",
            "Numberx.ensureNumber()" to "undefined",
            "Numberx.ensureNumber(NaN, 0, Infinity)" to "undefined",
            "Numberx.ensureNumberLike('50%')" to "0.5",
            "Numberx.ensureNumberLike('3:2')" to "1.5",
            "Numberx.ensureNumberLike('12')" to "12",
            "Numberx.ensureNumberLike(NaN, true)" to "NaN",
            "Numberx.ensureNumberLike(NaN)" to "throws RangeError",
            "Numberx.ensureNumberLike('abc')" to "throws TypeError",
            "Numberx.ensureNumberLike(null)" to "throws TypeError",
            "Numberx.ensureNumbersLike(['25%', '3:2', 4])" to "[0.25,1.5,4]",
            "Numberx.ensureNumbersLike([1, NaN, 2], true)" to "[1,2]",
            "Numberx.ensureNumbersLike([1, NaN, 2], true, true)" to "[1,null,2]",
            "Numberx.ensureNumbersLike([1, NaN])" to "throws RangeError",
            "Numberx.ensureNumbersLike(1)" to "throws TypeError",
            "Numberx.clampTo(30, [0, 0])" to "throws RangeError",
        )
    )

    /** Members without obsolete code: documented examples. */
    @Test
    fun documentedExamplesOfNewerMembers() = check(
        listOf(
            "Arrayx.subtract([1, 2, 2, 3], [2, 4])" to "[1,3]",
            "Arrayx.differ([1, 2, 3], [2, 4])" to "[1,3,4]",
            "Arrayx.differ([1, 1], [2, 2])" to "[1,2]",
            "(function () { var a = 'a'; return Arrayx.subtract(['ab', 'c'], [a + 'b']); })()" to "[\"c\"]",
            "Arrayx.subtract([0], [-0])" to "[]",
            "Mathx.mean(1, 2, 3)" to "2",
            "Mathx.mean([1, 2, 3], 2)" to "2",
            "Mathx.mode(1, 2, 2, 3)" to "2",
            "Mathx.mode([4, 4], [5, 6])" to "4",
            "randomInt(3, 3)" to "3",
            "Mathx.randomInt([1, 5], 10) >= 1" to "true",
            "Mathx.random(2, 2)" to "2",
            "Mathx.randFloat(0)" to "0",
            "(function () { var r = randomFloat(-1, 1); return r >= -1 && r < 1; })()" to "true",
            "(function () { var r = Mathx.random(); return r >= 0 && r < 1; })()" to "true",
            "Mathx.logMn(3, 2187)" to "7",
            "Mathx.logMn(10, 2, 4)" to "0.301",
            "Mathx.logMn(3, 2187, -1)" to "throws RangeError",
            "Mathx.ceilLog(2, 1500)" to "11",
            "Mathx.floorPow(2, 1500)" to "1024",
            "Mathx.floorLog(NaN, 2)" to "throws RangeError",
            "Mathx.floorLog('x', 2)" to "throws TypeError",
            "Mathx.dist(1)" to "throws TypeError",
            "Mathx.dist([0, 0], [3, 'x'])" to "throws TypeError",
            "Mathx.dist([0, 0], [3, 4], 1, 2)" to "throws TypeError",
            "Mathx.dist(new android.graphics.Point(0, 0), new android.graphics.Point(3, 4))" to "5",
            "Mathx.dist(new android.graphics.Rect(0, 0, 3, 4), 0)" to "5",
        )
    )

    /** D10: argument errors are TypeError / RangeError that scripts can test with instanceof. */
    @Test
    fun argumentErrorsAreTypeAndRangeErrors() = check(
        listOf(
            "Arrayx.distinct()" to "throws TypeError",
            "Arrayx.distinct([1], 2)" to "throws TypeError",
            "Arrayx.sortBy([1, 2], 1)" to "throws TypeError",
            "Arrayx.sortBy('ab', x => x)" to "throws TypeError",
            "Arrayx.ensureArray([], 9)" to "throws TypeError",
            "Numberx.clamp()" to "throws TypeError",
            "Numberx.clamp(null, 1)" to "throws TypeError",
            "Numberx.padStart(1, 'x')" to "throws TypeError",
            "Numberx.check(1, '?', 2)" to "throws TypeError",
            "Numberx.check(1, '<', 2, '<')" to "throws TypeError",
            "Numberx.toFixedNum(1, 101)" to "throws RangeError",
            "Mathx.logMn()" to "throws TypeError",
            "(function () { try { Numberx.clamp(); } catch (e) { return e instanceof TypeError; } })()" to "true",
            "(function () { try { Mathx.randInt(NaN, NaN); } catch (e) { return e instanceof RangeError; } })()" to "true",
            "jsox.extendAll(1)" to "throws TypeError",
            "jsox('Foo')" to "throws RangeError",
            "jsox.extend(null)" to "throws RangeError",
        )
    )

    /** The module objects, their names on the global object and the function lengths of the Kotlin signatures (Q9). */
    @Test
    fun moduleObjectsAndLengths() = check(
        listOf(
            "typeof Arrayx + typeof Numberx + typeof Mathx + typeof jsox" to "\"objectobjectobjectfunction\"",
            "\$jsox === jsox" to "true",
            "typeof \$Arrayx + typeof \$Numberx + typeof \$Mathx" to "\"undefinedundefinedundefined\"",
            "typeof Mathx.maxi + typeof Mathx.mini" to "\"undefinedundefined\"",
            "Object.keys(globalThis).filter(k => ['Arrayx', 'Numberx', 'randomInt', 'randomFloat', 'Mathx', 'jsox', '\$jsox'].indexOf(k) >= 0).join()" to "\"Arrayx,Numberx,randomInt,randomFloat,Mathx,jsox,\$jsox\"",
            "[Arrayx.distinct.length, Arrayx.sortBy.length, Arrayx.union.length, Arrayx.ensureArray.length]" to "[1,2,1,0]",
            "[Numberx.clamp.length, Numberx.toFixedNum.length, Numberx.padStart.length, Numberx.parseFloat.length]" to "[1,1,2,1]",
            "[Mathx.sum.length, Mathx.logMn.length, Mathx.dist.length, jsox.length, jsox.extend.length]" to "[0,2,0,0,0]",
            "Numberx.ICU" to "996",
        )
    )

    @Test
    fun extensionBindsTheBuiltIns() {
        check(listOf("typeof [].sorted" to "\"undefined\"", "typeof Math.sum" to "\"undefined\"", "typeof Number.ICU" to "\"undefined\""), export = false)
        extendAll()
        check(extended = true, cases =
            listOf(
                "[3, 1, 2].sorted()" to "[1,2,3]",
                "[1, 1, 2].distinct()" to "[1,2]",
                "[1, 2, 3].union(0, 5, [2, 3, 4])" to "[1,2,3,0,5,4]",
                "[1, 2, 2, 3].subtract([2, 4])" to "[1,3]",
                "[1, 2, 3].differ([2, 4])" to "[1,3,4]",
                "(function () { var a = [3, 1, 2]; return a.sortBy(x => x) === a && a.join() === '1,2,3'; })()" to "true",
                "['a', 'b', 'ab'].distinctBy(a => a.length)" to "[\"a\",\"ab\"]",
                "Array.ensureArray([])" to "undefined",
                "Array.ensureArray([], 9)" to "throws TypeError",
                "(5).clamp(1, 3)" to "3",
                "(5).clamp()" to "5",
                "(12).padStart(4, 0)" to "\"0012\"",
                "(99.1).padEnd(5)" to "\"99.10\"",
                "(390).clampTo([0, 360])" to "30",
                "(1.23456e3).toFixedNum()" to "1235",
                "(123.456).toFixedNum(2)" to "123.46",
                "new Number(5).clamp(1, 3)" to "3",
                "Number.ICU" to "996",
                "Number.parsePercent('1%')" to "0.01",
                "Number.check(1, '<', 2)" to "true",
                "Number.parseFloat('0.8', 16)" to "0.5",
                "parseFloat('0.101', 2)" to "0.625",
                "parseFloat('12.5px')" to "12.5",
                "parseFloat(' -Infinity')" to "-Infinity",
                "Math.sum(1, 2, 3)" to "6",
                "Math.avg(1, 2, 3)" to "2",
                "Math.median(1, 4, 2, 3)" to "2.5",
                "Math.maxi([5, 23])" to "23",
                "Math.mini([5, 23])" to "5",
                "Math.max([5, 23])" to "NaN",
                "Math.max(1, 2)" to "2",
                "Math.random(5, 5)" to "5",
                "Math.randomInt(3, 3)" to "3",
                "typeof Mathx.maxi" to "\"undefined\"",
                "[].sortBy(1)" to "throws TypeError",
                "[1].distinct(2)" to "throws TypeError",
                "(function () { try { [].sortBy(1); } catch (e) { return e instanceof TypeError; } })()" to "true",
                "Array.prototype.distinct.call(5)" to "throws TypeError",
                "[Array.prototype.distinct.length, Array.prototype.sortBy.length, Number.prototype.clamp.length, Number.prototype.padStart.length]" to "[0,1,0,1]",
                "(function () { var d = Object.getOwnPropertyDescriptor(Array.prototype, 'distinct'); return [d.enumerable, d.writable, d.configurable]; })()" to "[true,true,false]",
                "(function () { var d = Object.getOwnPropertyDescriptor(Math, 'sum'); return [d.enumerable, d.writable, d.configurable]; })()" to "[true,true,false]",
                "(function () { var d = Object.getOwnPropertyDescriptor(globalThis, 'parseFloat'); return [d.enumerable, d.writable, d.configurable]; })()" to "[true,true,false]",
                // Number.parseFloat exists already (not enumerable): redefining it keeps its place in the key order.
                "Object.keys(Number).join()" to "\"parseFloat,ICU,ensureNumber,ensureNumberLike,ensureNumbersLike,check,parsePercent,parseRatio,parseAny\"",
            )
        )
    }

    @Test
    fun jsoxExtendsByName() {
        check(export = false, cases =
            listOf(
                "jsox('Math')" to "undefined",
                "typeof Math.sum + typeof [].sorted + typeof Number.ICU" to "\"functionundefinedundefined\"",
                "jsox.extend(['arrayx', ['Number']])" to "undefined",
                "typeof [].sorted + typeof Number.ICU" to "\"functionnumber\"",
                "jsox.extend('Mathx', 'Math')" to "undefined",
                "jsox.extend()" to "undefined",
                "jsox.extendAll()" to "undefined",
                "jsox()" to "undefined",
                "Math.sum([1.01, 2.02], 2)" to "3.03",
            )
        )
    }

    /**
     * Surface against the current Kotlin modules (`augment` package) in the same JVM: own property names in order,
     * attributes, kinds and function names of the module objects, of their names on the global object, and of what the
     * built-in extension adds to Array, Array.prototype, Math, Number, Number.prototype and the global object. Function
     * lengths are left out (appendix C Q9).
     */
    @Test
    fun surfaceMatchesTheCurrentModules() {
        extendAll()
        val actual = rhino.evalKt(DESCRIBE).toString()
        val expected = ScriptModuleExpectations.obsolete("describe") {
            DifferentialRhino().use { legacy ->
                val global = legacy.ktScope.globalThis
                Arrayx(legacy.ktRuntime).augment(global)
                Numberx(legacy.ktRuntime).augment(global)
                Mathx(legacy.ktRuntime).augment(global)
                Jsox(legacy.ktRuntime).augment(global)
                // Jsox.extendAllRhinoWithRuntime reaches StringUtils (Android static initialization), so the extensions of its
                // preset list (Mathx, Numberx, Arrayx) are applied directly.
                Mathx(legacy.ktRuntime).extendBuildInObject()
                Numberx(legacy.ktRuntime).extendBuildInObject()
                Arrayx(legacy.ktRuntime).extendBuildInObject()
                legacy.evalKt(DESCRIBE).toString()
            }
        }
        assertEquals(expected.replace(';', '\n'), actual.replace(';', '\n'))
    }

    companion object {

        /** Every exported case so far (JUnit creates an instance per test method). */
        private val exported = LinkedHashMap<String, String>()

        /** Writes the exported cases in the format of `tools/augment/device_eval.py` (expected values in "kt"). */
        @Synchronized
        private fun exportForDevice(cases: List<Pair<String, String>>) {
            cases.forEach { (expression, expected) -> exported[expression] = expected }
            val dir = File("build/script-modules-device").apply { mkdirs() }
            val entries = exported.entries.joinToString(",\n") { (expression, expected) ->
                "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer (P2.1)\"}"
            }
            File(dir, "JsoxModulesTest.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
        }

        /** A JSON string literal; the expressions are ASCII, so only quotes and backslashes need escaping. */
        private fun json(value: String): String = buildString {
            append('"')
            value.forEach { c ->
                if (c == '"' || c == BACKSLASH) append(BACKSLASH)
                append(c)
            }
            append('"')
        }

        private const val BACKSLASH = '\\'

        private val DESCRIBE = """
            (function () {
                var out = [];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                function own(v) {
                    return Object.getOwnPropertyNames(v).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; }).map(function (k) {
                        var d = Object.getOwnPropertyDescriptor(v, k);
                        var inner = k === 'prototype' && d.value && typeof d.value === 'object' && d.value.constructor === v ? ' {' + Object.getOwnPropertyNames(d.value).map(function (p) {
                            var pd = Object.getOwnPropertyDescriptor(d.value, p);
                            return p + ' ' + attrs(pd) + (pd.value === v ? ' self' : '');
                        }).join(',') + '}' : '';
                        return k + ' ' + attrs(d) + inner;
                    });
                }
                function kind(v) {
                    if (typeof v === 'function') {
                        var extra = own(v);
                        return 'function ' + v.name + (extra.length ? ' [' + extra.join(',') + ']' : '');
                    }
                    return typeof v === 'object' ? 'object' : typeof v + ' ' + String(v);
                }
                function walk(label, o, names) {
                    (names || Object.getOwnPropertyNames(o)).forEach(function (k) {
                        var d = Object.getOwnPropertyDescriptor(o, k);
                        if (!d) { out.push(label + '.' + k + ' missing'); return; }
                        out.push(label + '.' + k + ' ' + attrs(d) + ' ' + kind(d.value));
                    });
                }
                var names = ['Arrayx', 'Numberx', 'randomInt', 'randomFloat', 'Mathx', 'jsox', '${'$'}jsox', 'parseFloat'];
                out.push('keys ' + Object.keys(globalThis).filter(function (k) { return names.indexOf(k) >= 0; }).join());
                walk('global', globalThis, names);
                ['Arrayx', 'Numberx', 'Mathx', 'jsox'].forEach(function (n) {
                    walk(n, globalThis[n], Object.getOwnPropertyNames(globalThis[n]).filter(function (k) { return ['length', 'arity', 'name', 'prototype'].indexOf(k) < 0; }));
                    out.push(n + ' proto ' + (Object.getPrototypeOf(globalThis[n]) === Object.prototype ? 'Object.prototype' : Object.getPrototypeOf(globalThis[n]) === Function.prototype ? 'Function.prototype' : 'other'));
                });
                walk('Array', Array);
                walk('Array.prototype', Array.prototype);
                walk('Math', Math);
                walk('Number', Number);
                walk('Number.prototype', Number.prototype);
                return out.join(';');
            })()
        """.trimIndent()

    }

}
