package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Obsolete `__util__.js` against `util` of the new script module layer (roadmap P2.3 / P2.4) with its sub-modules,
 * mounted the way `ScriptRuntime.augment()` does with the switch on. Error types are compared (D10); the differences
 * left are the ones ROADMAP appendix E keeps from the current Kotlin module (or fixes in both) and the new rows of
 * "P2.3 new deviations". The obsolete module's own `versionCodes` (a different structure) and the console functions are
 * covered by [UtilModulesTest].
 */
class UtilModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsUtil: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsUtil = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("util")!!
        rhino.mountNewModules("util")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktUtil = rhino.evalKt("util")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsUtil, rhino.jsScope.globalThis), SurfaceDiff.surface(ktUtil, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete util members missing from the new module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        private val KNOWN_MISSING = emptySet<String>()

        private val FORMAT = listOf(
            "util.format('%s%s%d', '0x10', ': ', '0x10')",
            "util.format('10%% is 0.1')",
            "util.format([1], 2, '3', { num: 4, age: 5 })",
            "util.format({ a: { b: { c: 'd' } } })",
            "util.format({ a: { b: { c: { d: { e: 'f' } } } } })",
            "util.format('%s:%s', '22', '59', 'Go to bed')",
            "util.format('%j', { a: [1, 'x'] })",
            "util.format('%j')",
            "util.format('%j', undefined)",
            "util.format('%j', (function () { var o = {}; o.o = o; return o; })())",
            "util.format('%d', '1.50')",
            "util.format('%d', 'x')",
            "util.format('%d', 1e21)",
            "util.format('%d', 0.1 + 0.2)",
            "util.format('%d %d', 1)",
            "util.format('%x %s', 1)",
            "util.format('%', 1)",
            "util.format('100%')",
            "util.format('a%%b', 1)",
            "util.format('%s', null)",
            "util.format('%s', undefined)",
            "util.format('%s', { a: 1 })",
            "util.format('%s', [1, [2, 3]])",
            "util.format('%s', 'a\\nb')",
            "util.format('x', null, undefined, 1, 'y', true)",
            "util.format('x', { a: 1 }, [1])",
            "util.format('x', function foo() { return 1; })",
            "util.format('x', /re/g)",
            "util.format('x', new Error('e'))",
            "util.format()",
            "util.format(null)",
            "util.format(undefined)",
            "util.format(1, 'a')",
            "util.format(1, '%s', 2)",
            "util.format(true, null)",
            "util.format('%s' + '-%d', 'a', 2)",
        )

        private val INSPECT = listOf(
            "util.inspect(1)",
            "util.inspect(-0)",
            "util.inspect(NaN)",
            "util.inspect('a\\'b')",
            "util.inspect('a\"b')",
            "util.inspect('line\\nbreak')",
            "util.inspect('con' + 'cat')",
            "util.inspect(true)",
            "util.inspect(null)",
            "util.inspect(undefined)",
            "util.inspect({})",
            "util.inspect([])",
            "util.inspect([1, 'a', null, undefined])",
            "util.inspect([1, , 3])",
            "util.inspect((function () { var a = [1, 2]; a.extra = 'x'; return a; })())",
            "util.inspect({ a: 1, 'b-c': 2, _d: 3, '1': 4 })",
            "util.inspect({ \"it's\": 1 })",
            "util.inspect({ a: { b: { c: { d: 1 } } } })",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: 0 })",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, false, 0)",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: null })",
            "util.inspect({ a: { b: { c: { d: { e: 1 } } } } }, false, Infinity)",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, false, null)",
            "util.inspect({ a: { b: 1 } }, {}, 0)",
            "util.inspect([[[[1]]]])",
            "util.inspect({ longPropertyName1: 'some long value', longPropertyName2: 'another long value' })",
            "util.inspect({ nested: { longPropertyName1: 'some long value', longPropertyName2: 'another long value' } })",
            "util.inspect([{ longPropertyName1: 'some long value', longPropertyName2: 'another long value' }])",
            "util.inspect((function () { var o = { a: 1 }; o.self = o; return o; })())",
            "util.inspect({ get x() { return 1; } })",
            "util.inspect({ set x(v) { } })",
            "util.inspect({ get x() { return 1; }, set x(v) { } })",
            "util.inspect(Object.defineProperty({ a: 1 }, 'hidden', { value: 2, enumerable: false }), { showHidden: true })",
            "util.inspect(Object.defineProperty({ a: 1 }, 'hidden', { value: 2, enumerable: false }), true)",
            "util.inspect([1, 2], { showHidden: true })",
            "util.inspect(function foo() { })",
            "util.inspect(function () { })",
            "util.inspect(Object.assign(function bar() { }, { p: 1 }))",
            "util.inspect(/re/gi)",
            "util.inspect(Object.assign(/re/, { p: 1 }))",
            "util.inspect(new Date(0)) === new Date(0).toString()",
            "util.inspect(Object.assign(new Date(0), { p: 1 }))",
            "util.inspect(new Error('boom'))",
            "util.inspect(new TypeError('bad'))",
            "util.inspect(Object.assign(new Error('e'), { code: 1 }))",
            "util.inspect({ inspect: function () { return 'custom'; } })",
            "util.inspect({ inspect: function () { return { a: 1 }; } })",
            "util.inspect({ inspect: function () { return 'custom'; } }, { customInspect: false })",
            "util.inspect({ inspect: function () { return 'custom'; } }, { customInspect: true })",
            "util.inspect({ inspect: function () { return { a: 1 }; } }, { customInspect: true })",
            "util.inspect({ toString: function () { return 'TS'; } })",
            "util.inspect({ toString: function (radix) { return 'TS' + (radix || ''); } })",
            "util.inspect(Object.create(null))",
            "util.inspect(Object.create({ inherited: 1 }))",
            "util.inspect(Object.create(Error.prototype))",
            "util.inspect(new java.lang.StringBuilder('sb'))",
            "util.inspect(java.lang.Integer.valueOf(7))",
            "util.inspect(java.lang.Double.valueOf(1.5))",
            "util.inspect(java.lang.Boolean.TRUE)",
            "util.inspect(new java.lang.String('j'))",
            "util.inspect(java.lang.String)",
            "util.inspect(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2))",
            "util.inspect(java.lang.reflect.Array.newInstance(java.lang.String, 2))",
            "util.inspect(new java.util.ArrayList())",
            "util.inspect('x', { colors: true })",
            "util.inspect(1, false, 2, true)",
            "util.inspect({ a: 'x', b: null, c: undefined, d: 1, e: true }, { colors: true })",
            "util.inspect()",
            "util.inspect.colors && util.inspect.colors.red",
            "util.inspect.styles && util.inspect.styles.string",
            "util.inspect(1, 'options')",
            "util.inspect(1, false, 'deep')",
            "util.inspect(1, {}, 2, false, 'extra')",
        )

        private val PREDICATES_AND_HELPERS = listOf(
            "[util.isArray([]), util.isArray({ length: 0 })]",
            "[util.isBoolean(false), util.isBoolean(new Boolean(false)), util.isBoolean(0)]",
            "[util.isNull(null), util.isNull(undefined)]",
            "[util.isNullOrUndefined(null), util.isNullOrUndefined(undefined), util.isNullOrUndefined(0)]",
            "[util.isNumber(1), util.isNumber(NaN), util.isNumber('1'), util.isNumber(new Number(1))]",
            "[util.isString(''), util.isString(new String('')), util.isString(new java.lang.String('j')), util.isString('a' + 'b')]",
            "[util.isSymbol(Symbol()), util.isSymbol('s')]",
            "[util.isUndefined(undefined), util.isUndefined(null)]",
            "[util.isRegExp(/x/), util.isRegExp(new RegExp('x')), util.isRegExp('/x/')]",
            "[util.isObject({}), util.isObject(null), util.isObject([]), util.isObject(function () { })]",
            "[util.isDate(new Date()), util.isDate(Date.now())]",
            "[util.isError(new Error()), util.isError(new TypeError()), util.isError({ message: 'm' }), util.isError(Object.create(Error.prototype))]",
            "[util.isFunction(function () { }), util.isFunction(() => 1), util.isFunction({})]",
            "[util.isJavaObject(new java.lang.Object()), util.isJavaObject({}), util.isJavaObject(java.lang.String)]",
            "[util.isJavaArray(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 1)), util.isJavaArray([])]",
            "[util.isInteger(1), util.isInteger(1.5), util.isInteger('1')]",
            "[util.isPrimitive(1), util.isPrimitive('s'), util.isPrimitive(null), util.isPrimitive({}), util.isPrimitive(function () { })]",
            "[util.isReference({}), util.isReference(function () { }), util.isReference(1)]",
            "[util.isEmptyObject({}), util.isEmptyObject({ a: 1 }), util.isEmptyObject([]), util.isEmptyObject(Object.create({ a: 1 }))]",
            "[util.unwrapJavaObject(java.lang.Integer.valueOf(3)), util.unwrapJavaObject(new java.lang.String('s')), util.unwrapJavaObject(5)]",
            "[util.checkStringParam('abc', 'a.c'), util.checkStringParam('abc', 'b'), util.checkStringParam(' ABC ', 'abc'), util.checkStringParam(12, '1\\\\d')]",
            "util.checkStringParam('abc', /b/)",
            "util.checkStringParam('abc', /^b$/)",
            "util.checkStringParam('ab', 'a|b')",
            "util.checkStringParam({ toString() { return 'abc'; } }, 'abc')",
            "util.checkStringParam(null, 'x')",
            "util.checkStringParam('x', 1)",
            "util.checkStringArgument('abc', /B/)",
            "[util.assureStringStartsWith('bc', 'a'), util.assureStringStartsWith('abc', 'a')]",
            "[util.assureStringEndsWith('ab', 'c'), util.assureStringEndsWith('abc', 'c')]",
            "[util.assureStringSurroundsWith('b', '\"'), util.assureStringSurroundsWith('b', '[', ']'), util.assureStringSurroundsWith('[b]', '[', ']')]",
            "util.assureStringStartsWith('b' + 'c', 'a')",
            "util.assureStringStartsWith(1, 'a')",
            "util.ensureType(1, 'number')",
            "util.ensureType(1, 'string')",
            "util.ensureType(1, 'nonsense')",
            "util.ensureStringType('a', 'b')",
            "util.ensureStringType('a', 1)",
            "util.ensureNumberType(1, NaN)",
            "util.ensureNumberType('1')",
            "util.ensureBooleanType(true)",
            "util.ensureObjectType(null)",
            "util.ensureNonNullObjectType(null)",
            "util.ensureNonNullObjectType({})",
            "util.ensureFunctionType(function () { })",
            "util.ensureArrayType([], {})",
            "util.ensureUndefinedType(undefined)",
            "util.ensureBigintType(1)",
            "util.toRegularAndCall((a, b) => [a, b], 1, 2)",
            "util.toRegularAndApply((a, b) => a + b, [1, 2])",
            "util.toRegularAndApply((a, b) => a + b, 1)",
            "util.toRegular(1)",
            "util.toRegular(function f(a) { return a; })(5)",
            "util.toRegular((a, b) => [a, b])(1, 2)",
            "(function () { function P() { } P.prototype.hi = function () { return 'hi'; }; function C() { } util.extend(C, P); return [new C().hi(), new C() instanceof P, Object.getPrototypeOf(C) === P]; })()",
            "(function () { var src = { v: 7, get: function () { return this.v; } }; var dst = {}; util.__assignFunctions__(src, dst, ['get']); var f = dst.get; return f(); })()",
            "String(util.getClassName(new java.lang.StringBuilder()))",
            "String(util.getClassName(java.lang.String))",
            "String(util.getClass(1))",
        )

        private val JAVA_AND_MORSE = listOf(
            "util.java.instanceof(new java.util.ArrayList(), java.util.List)",
            "util.java.instanceOf(new java.util.ArrayList(), 'java.util.ArrayList')",
            "util.java.instanceof(new java.util.ArrayList(), java.lang.String)",
            "util.java.array('int', 2).length",
            "String(util.java.array('string', 2).getClass().getName())",
            "String(util.java.array('int', 3, 4).getClass().getName())",
            "String(util.java.array(java.lang.Object, 2).getClass().getName())",
            "(function () { var l = new java.util.ArrayList(); l.add('a'); l.add('b'); var a = util.java.toJsArray(l); return [Array.isArray(a), a.length, String(a[1])]; })()",
            "util.java.toJsArray(null)",
            "util.java.toJsArray(null, true)",
            "(function () { var m = util.java.objectToMap({ a: 1, b: 'x' }); return [m.size(), m.containsKey('a'), String(m.get('b'))]; })()",
            "util.java.objectToMap(null)",
            "(function () { var m = new java.util.HashMap(); m.put('k', 'v'); var o = util.java.mapToObject(m); return [Object.keys(o), String(o.k)]; })()",
            "util.java.mapToObject(null)",
            "util.morseCode.getCode('SOS')",
            "util.morseCode.getPattern('E T')",
            "util.morseCode.getPattern('E', 200)",
            "util.morseCode.getPattern('E', 50)",
            "util.morseCode('SOS').code",
            "util.morseCode('sos').pattern.length",
            "util.morseCode.getCode('#')",
            "typeof util.version.sdkInt",
        )

        private val EXPRESSIONS = FORMAT + INSPECT + PREDICATES_AND_HELPERS + JAVA_AND_MORSE

        /** Differences kept from the current Kotlin module or fixed on purpose, see ROADMAP appendix E. */
        private const val CUSTOM_INSPECT_REASON = "documented: customInspect defaults to false (current Kotlin module, appendix E: keep the Kotlin " +
                "behavior); the obsolete module called the object's own inspect by default"

        private const val OBSOLETE_JAVA_REASON = "obsolete JS bug: a JavaClass failed on getClass before its own branch, a Java collection showed no " +
                "elements (appendix E: keep the Kotlin behavior)"

        private const val GETTERS_REASON = "documented getters util.inspect.colors / styles; the obsolete module put them on an internal function " +
                "scripts could not reach (appendix E: keep the Kotlin behavior)"

        private const val OPTION_TYPE_REASON = "Q12: the obsolete module ignored options of the wrong type, the current Kotlin module throws; the new " +
                "layer throws a TypeError (D10)"

        private const val SURPLUS_REASON = "surplus arguments: ignored by the obsolete module, a TypeError on the new layer (appendix C Q7)"

        private const val EMPTY_OBJECT_REASON = "documented: \"no own properties\" (appendix E: keep the Kotlin behavior); the obsolete module " +
                "counted inherited properties through for-in"

        private const val D10_REASON = "D10: the obsolete module threw a plain Error (TypeError for an unknown type name) for an argument it " +
                "could not use; the new layer throws a TypeError for the wrong type and a RangeError for an unusable value"

        private const val KOTLIN_MEMBER_REASON = "a member of the current Kotlin module (documented name, or the alias next to the obsolete " +
                "instanceOf) that the obsolete module did not have"

        private const val ABANDONED_REASON = "documented as ABANDONED, always throws (appendix E: keep the Kotlin behavior)"

        private const val MORSE_TIME_SPAN_REASON = "obsolete JS bug: morseCode.getPattern dropped its time span argument and always used 100 ms; " +
                "the current Kotlin module and the new layer use the given time span (documented)"

        private const val OBSOLETED = "throws JavaException ObsoletedRhinoFunctionException"

        /** Differences kept or fixed on purpose, see ROADMAP appendix E (P0 rows and "P2.3 new deviations"), plus D10, Q7 and Q12. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference("util.inspect({ inspect: function () { return 'custom'; } })", js = "\"custom\"", kt = "\"{ inspect: [Function inspect()] }\"", reason = CUSTOM_INSPECT_REASON),
            DifferentialCases.KnownDifference("util.inspect({ inspect: function () { return { a: 1 }; } })", js = "\"{ a: 1 }\"", kt = "\"{ inspect: [Function inspect()] }\"", reason = CUSTOM_INSPECT_REASON),
            DifferentialCases.KnownDifference("util.inspect(java.lang.String)", js = "throws InternalError", kt = "\"class java.lang.String\"", reason = OBSOLETE_JAVA_REASON),
            DifferentialCases.KnownDifference("util.inspect(new java.util.ArrayList())", js = "\"{}\"", kt = "\"[]\"", reason = OBSOLETE_JAVA_REASON),
            DifferentialCases.KnownDifference("util.inspect.colors && util.inspect.colors.red", js = "undefined", kt = "[31,39]", reason = GETTERS_REASON),
            DifferentialCases.KnownDifference("util.inspect.styles && util.inspect.styles.string", js = "undefined", kt = "\"green\"", reason = GETTERS_REASON),
            DifferentialCases.KnownDifference("util.inspect(1, 'options')", js = "\"1\"", kt = "throws TypeError", reason = OPTION_TYPE_REASON),
            DifferentialCases.KnownDifference("util.inspect(1, false, 'deep')", js = "\"1\"", kt = "throws TypeError", reason = OPTION_TYPE_REASON),
            DifferentialCases.KnownDifference("util.inspect(1, {}, 2, false, 'extra')", js = "\"1\"", kt = "throws TypeError", reason = SURPLUS_REASON),
            DifferentialCases.KnownDifference(
                "[util.isEmptyObject({}), util.isEmptyObject({ a: 1 }), util.isEmptyObject([]), util.isEmptyObject(Object.create({ a: 1 }))]",
                js = "[true,false,true,false]", kt = "[true,false,true,true]", reason = EMPTY_OBJECT_REASON,
            ),
            DifferentialCases.KnownDifference("util.checkStringParam(null, 'x')", js = "throws Error", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference("util.checkStringParam('x', 1)", js = "throws Error", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference("util.checkStringArgument('abc', /B/)", js = "throws TypeError", kt = "true", reason = KOTLIN_MEMBER_REASON),
            DifferentialCases.KnownDifference("util.ensureType(1, 'nonsense')", js = "throws TypeError", kt = "throws RangeError", reason = D10_REASON),
            DifferentialCases.KnownDifference("util.ensureNonNullObjectType(null)", js = "throws Error", kt = "throws TypeError", reason = D10_REASON),
            DifferentialCases.KnownDifference("util.toRegularAndCall((a, b) => [a, b], 1, 2)", js = "[1,2]", kt = OBSOLETED, reason = ABANDONED_REASON),
            DifferentialCases.KnownDifference("util.toRegularAndApply((a, b) => a + b, [1, 2])", js = "3", kt = OBSOLETED, reason = ABANDONED_REASON),
            DifferentialCases.KnownDifference("util.toRegularAndApply((a, b) => a + b, 1)", js = "throws Error", kt = OBSOLETED, reason = ABANDONED_REASON),
            DifferentialCases.KnownDifference("util.java.instanceof(new java.util.ArrayList(), java.util.List)", js = "throws TypeError", kt = "true", reason = KOTLIN_MEMBER_REASON),
            DifferentialCases.KnownDifference("util.java.instanceof(new java.util.ArrayList(), java.lang.String)", js = "throws TypeError", kt = "false", reason = KOTLIN_MEMBER_REASON),
            DifferentialCases.KnownDifference("util.morseCode.getPattern('E', 200)", js = "[100]", kt = "[200]", reason = MORSE_TIME_SPAN_REASON),
            DifferentialCases.KnownDifference("util.morseCode.getCode('#')", js = "throws Error", kt = "throws RangeError", reason = D10_REASON),
        )

    }

}
