package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.runtime.api.Util as ApiUtil
import org.autojs.autojs.runtime.api.augment.util.Inspect as UtilInspect
import org.autojs.autojs.runtime.api.augment.util.Java as UtilJava
import org.autojs.autojs.runtime.api.augment.util.MorseCode as UtilMorseCode
import org.autojs.autojs.runtime.api.augment.util.Version as UtilVersion
import org.autojs.autojs.runtime.api.augment.util.VersionCodes as UtilVersionCodes

/**
 * Obsolete `__util__.js` against the Kotlin [Util] augmentable and its sub-modules, mounted as
 * `ScriptRuntime.augment` does (roadmap P0.5). The cases cover the pure logic: `format`, `inspect`, the type
 * predicates and the string helpers.
 */
class UtilDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsUtil: Any
    private lateinit var ktUtil: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsUtil = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("util")!!
        val runtime = rhino.ktRuntime
        ktUtil = Util.augmentWithRuntime(rhino.ktScope.globalThis, runtime, ApiUtil).also { util ->
            UtilJava.augmentWithRuntime(util, runtime)
            UtilVersion.augmentWithRuntime(util, runtime)
            UtilVersionCodes.augmentWithRuntime(util, runtime, VersionCodesInfo.obj)
            UtilInspect.augmentWithRuntime(util, runtime)
            UtilMorseCode.augmentWithRuntime(util, runtime)
        }
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsUtil, rhino.jsScope.globalThis), SurfaceDiff.surface(ktUtil, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        val missing = report.onlyInFirst - KNOWN_MISSING
        assertTrue("Obsolete util members missing from the Kotlin module: $missing", missing.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        private val KNOWN_MISSING = emptySet<String>()

        private val FORMAT = listOf(
            // The d.ts examples.
            "util.format('%s%s%d', '0x10', ': ', '0x10')",
            "util.format('10%% is 0.1')",
            "util.format([1], 2, '3', { num: 4, age: 5 })",
            "util.format({ a: { b: { c: 'd' } } })",
            "util.format({ a: { b: { c: { d: { e: 'f' } } } } })",
            "util.format('%s:%s', '22', '59', 'Go to bed')",
            // Placeholders.
            "util.format('%j', { a: [1, 'x'] })",
            "util.format('%j')",
            "util.format('%j', (function () { var o = {}; o.o = o; return o; })())",
            "util.format('%d', '1.50')",
            "util.format('%d', 'x')",
            "util.format('%d', 1e21)",
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
            // Remaining arguments.
            "util.format('x', null, undefined, 1, 'y', true)",
            "util.format('x', { a: 1 }, [1])",
            "util.format('x', function foo() { return 1; })",
            "util.format('x', /re/g)",
            "util.format('x', new Error('e'))",
            // Non-string first argument.
            "util.format()",
            "util.format(null)",
            "util.format(undefined)",
            "util.format(1, 'a')",
            "util.format(1, '%s', 2)",
            "util.format(true, null)",
        )

        private val INSPECT = listOf(
            "util.inspect(1)",
            "util.inspect(-0)",
            "util.inspect(NaN)",
            "util.inspect('a\\'b')",
            "util.inspect('a\"b')",
            "util.inspect('line\\nbreak')",
            "util.inspect(true)",
            "util.inspect(null)",
            "util.inspect(undefined)",
            "util.inspect({})",
            "util.inspect([])",
            "util.inspect([1, 'a', null, undefined])",
            "util.inspect([1, , 3])",
            "util.inspect((function () { var a = [1, 2]; a.extra = 'x'; return a; })())",
            "util.inspect({ a: 1, 'b-c': 2, _d: 3, '1': 4 })",
            "util.inspect({ a: { b: { c: { d: 1 } } } })",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: 0 })",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, false, 0)",
            "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: null })",
            "util.inspect([[[[1]]]])",
            "util.inspect({ longPropertyName1: 'some long value', longPropertyName2: 'another long value' })",
            "util.inspect({ nested: { longPropertyName1: 'some long value', longPropertyName2: 'another long value' } })",
            "util.inspect((function () { var o = { a: 1 }; o.self = o; return o; })())",
            "util.inspect({ get x() { return 1; } })",
            "util.inspect({ set x(v) { } })",
            "util.inspect({ get x() { return 1; }, set x(v) { } })",
            "util.inspect(Object.defineProperty({ a: 1 }, 'hidden', { value: 2, enumerable: false }), { showHidden: true })",
            "util.inspect([1, 2], { showHidden: true })",
            "util.inspect(function foo() { })",
            "util.inspect(function () { })",
            "util.inspect(Object.assign(function bar() { }, { p: 1 }))",
            "util.inspect(/re/gi)",
            "util.inspect(Object.assign(/re/, { p: 1 }))",
            "util.inspect(new Date(0)) === new Date(0).toString()",
            "util.inspect(new Error('boom'))",
            "util.inspect(new TypeError('bad'))",
            "util.inspect(Object.assign(new Error('e'), { code: 1 }))",
            "util.inspect({ inspect: function () { return 'custom'; } })",
            "util.inspect({ inspect: function () { return { a: 1 }; } })",
            "util.inspect({ inspect: function () { return 'custom'; } }, { customInspect: false })",
            "util.inspect({ toString: function () { return 'TS'; } })",
            "util.inspect(Object.create(null))",
            "util.inspect(Object.create({ inherited: 1 }))",
            "util.inspect(new java.lang.StringBuilder('sb'))",
            "util.inspect(java.lang.Integer.valueOf(7))",
            "util.inspect(java.lang.String)",
            "util.inspect(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2))",
            "util.inspect(new java.util.ArrayList())",
            "util.inspect('x', { colors: true })",
            "util.inspect(1, false, 2, true)",
            "util.inspect({ a: 'x', b: null, c: undefined, d: 1, e: true }, { colors: true })",
            "util.inspect()",
            "util.inspect.colors && util.inspect.colors.red",
            "util.inspect.styles && util.inspect.styles.string",
        )

        private val PREDICATES_AND_HELPERS = listOf(
            "[util.isArray([]), util.isArray({ length: 0 })]",
            "[util.isBoolean(false), util.isBoolean(new Boolean(false)), util.isBoolean(0)]",
            "[util.isNull(null), util.isNull(undefined)]",
            "[util.isNullOrUndefined(null), util.isNullOrUndefined(undefined), util.isNullOrUndefined(0)]",
            "[util.isNumber(1), util.isNumber(NaN), util.isNumber('1'), util.isNumber(new Number(1))]",
            "[util.isString(''), util.isString(new String('')), util.isString(new java.lang.String('j'))]",
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
            "util.checkStringParam({ toString() { return 'abc'; } }, 'abc')",
            "util.checkStringParam(null, 'x')",
            "[util.assureStringStartsWith('bc', 'a'), util.assureStringStartsWith('abc', 'a')]",
            "[util.assureStringEndsWith('ab', 'c'), util.assureStringEndsWith('abc', 'c')]",
            "[util.assureStringSurroundsWith('b', '\"'), util.assureStringSurroundsWith('b', '[', ']'), util.assureStringSurroundsWith('[b]', '[', ']')]",
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
            "util.ensureFunctionType(function () { })",
            "util.ensureArrayType([], {})",
            "util.ensureUndefinedType(undefined)",
            "util.ensureBigintType(1)",
            "util.toRegularAndCall((a, b) => [a, b], 1, 2)",
            "util.toRegularAndApply((a, b) => a + b, [1, 2])",
            "util.toRegularAndApply((a, b) => a + b, 1)",
            "util.toRegular(1)",
            "(function () { function P() { } P.prototype.hi = function () { return 'hi'; }; function C() { } util.extend(C, P); return [new C().hi(), new C() instanceof P, Object.getPrototypeOf(C) === P]; })()",
            "String(util.getClassName(new java.lang.StringBuilder()))",
            "String(util.getClassName(java.lang.String))",
            "String(util.getClass(1))",
        )

        private val EXPRESSIONS = FORMAT + INSPECT + PREDICATES_AND_HELPERS

        private const val ERROR_FORMAT = "the obsolete module printed errors as [Name: message] and listed their own properties; Kotlin's " +
                "custom-toString check runs first and returns the bare Error.prototype.toString, although Inspect.formatError builds the " +
                "bracketed form; restore (P2.3)"

        private const val LEGACY_POSITIONAL = "the d.ts declares the legacy positional form inspect(value, showHidden, depth, colors) and the " +
                "obsolete module accepted it; Kotlin takes at most (value, options); restore, accepting both (P2.3)"

        private const val CUSTOM_INSPECT = "the documentation now gives customInspect = false by default (current Kotlin), while the d.ts " +
                "util.format examples rely on the obsolete default (true); keep Kotlin and update the d.ts examples"

        private const val CHECK_STRING_REGEXP = "the d.ts declares pattern: string | RegExp (with a RegExp example) and the obsolete module " +
                "tested RegExp.source; Kotlin only accepts kotlin.text.Regex, which a script cannot pass (the documentation copies that), and " +
                "throws java.lang.Error; restore RegExp (P2.3) and fix the documentation"

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "util.format('%d', 1e21)",
                js = "\"1e+21\"",
                kt = "\"1000000000000000000000\"",
                reason = "%d converts to a number and the obsolete module rendered it with the JS number-to-string; Kotlin's jsString prints plain digits (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.format('x', new Error('e'))",
                js = "\"x [Error: e]\"",
                kt = "\"x Error: e\"",
                reason = ERROR_FORMAT,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect('a\\'b')",
                js = "\"'a\\\\'b'\"",
                kt = "\"'a'b'\"",
                reason = "Kotlin drops the escaping of single quotes inside the quoted string (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect('a\"b')",
                js = "\"'a\\\"b'\"",
                kt = "\"'a'b'\"",
                reason = "Kotlin turns double quotes inside the string into single quotes, changing the content (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ a: 1, 'b-c': 2, _d: 3, '1': 4 })",
                js = "\"{ '1': 4, a: 1, 'b-c': 2, _d: 3 }\"",
                kt = "\"{ \\\"1\\\": 4, a: 1, \\\"b-c\\\": 2, _d: 3 }\"",
                reason = "the obsolete module quoted non-identifier keys with single quotes (Node legacy format); Kotlin keeps the JSON double quotes (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ a: { b: { c: { d: 1 } } } }, false, 0)",
                js = "\"{ a: [Object] }\"",
                kt = "throws(java) RuntimeException",
                reason = LEGACY_POSITIONAL,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ a: { b: { c: { d: 1 } } } }, { depth: null })",
                js = "\"{ a: { b: { c: { d: 1 } } } }\"",
                kt = "\"{ a: { b: { c: [Object] } } }\"",
                reason = "depth: null recursed without limit in the obsolete module (Node legacy semantics); Kotlin falls back to 2; " +
                        "the documentation is silent, restore per D3 (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ nested: { longPropertyName1: 'some long value', longPropertyName2: 'another long value' } })",
                js = "\"{ nested: \\n   { longPropertyName1: 'some long value',\\n     longPropertyName2: 'another long value' } }\"",
                kt = "\"{ nested:    { longPropertyName1: 'some long value',\\n     longPropertyName2: 'another long value' } }\"",
                reason = "Kotlin loses the line break before a multi-line nested value (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(function () { })",
                js = "\"[Function]\"",
                kt = "\"[Function ()]\"",
                reason = "an anonymous function has an empty name; Kotlin prints it with empty parentheses (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(Object.assign(/re/, { p: 1 }))",
                js = "\"{ /re/ p: 1 }\"",
                kt = "\"/re/\"",
                reason = "the obsolete module listed own properties after the RegExp; Kotlin prints the source only; documentation silent, restore per D3 (P2.3)",
            ),
            DifferentialCases.KnownDifference("util.inspect(new Error('boom'))", js = "\"[Error: boom]\"", kt = "\"Error: boom\"", reason = ERROR_FORMAT),
            DifferentialCases.KnownDifference("util.inspect(new TypeError('bad'))", js = "\"[TypeError: bad]\"", kt = "\"TypeError: bad\"", reason = ERROR_FORMAT),
            DifferentialCases.KnownDifference(
                "util.inspect(Object.assign(new Error('e'), { code: 1 }))",
                js = "\"{ [Error: e] code: 1 }\"",
                kt = "\"Error: e\"",
                reason = ERROR_FORMAT,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ inspect: function () { return 'custom'; } })",
                js = "\"custom\"",
                kt = "\"{ inspect: [Function inspect()] }\"",
                reason = CUSTOM_INSPECT,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect({ inspect: function () { return { a: 1 }; } })",
                js = "\"{ a: 1 }\"",
                kt = "\"{ inspect: [Function inspect()] }\"",
                reason = CUSTOM_INSPECT,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(java.lang.Integer.valueOf(7))",
                js = "\"[JavaObject: 7]\"",
                kt = "\"7\"",
                reason = "the obsolete module marked boxed Java values, which differ from JS numbers under typeof and ===; " +
                        "documentation silent, restore per D3 (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(java.lang.String)",
                js = "throws InternalError",
                kt = "\"class java.lang.String\"",
                reason = "the obsolete module read getClass on the NativeJavaClass and threw before its own [JavaClass ...] branch; keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2))",
                js = "\"[0, 0]\"",
                kt = "\"[ 0, 0 ]\"",
                reason = "the obsolete module printed Java arrays with Arrays.toString; Kotlin formats them like JS arrays; documentation silent, restore per D3 (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(new java.util.ArrayList())",
                js = "\"{}\"",
                kt = "\"[]\"",
                reason = "the obsolete module printed {} for any Java collection (no enumerable keys); Kotlin prints the elements; keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect(1, false, 2, true)",
                js = "\"\\u001b[33m1\\u001b[39m\"",
                kt = "throws(java) RuntimeException",
                reason = LEGACY_POSITIONAL,
            ),
            DifferentialCases.KnownDifference(
                "util.inspect()",
                js = "\"undefined\"",
                kt = "throws(java) RuntimeException",
                reason = "the d.ts declares value as optional; Kotlin requires one argument; restore (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect.colors && util.inspect.colors.red",
                js = "undefined",
                kt = "[31,39]",
                reason = "documented getters; the obsolete module attached colors / styles to the unbound inner function, so they were unreachable; keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "util.inspect.styles && util.inspect.styles.string",
                js = "undefined",
                kt = "\"green\"",
                reason = "documented getters, see util.inspect.colors; keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "[util.isString(''), util.isString(new String('')), util.isString(new java.lang.String('j'))]",
                js = "[true,false,false]",
                kt = "[true,false,true]",
                reason = "documented: whether the JavaScript type is string; Kotlin also accepts a java.lang.String object (typeof object); restore (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "[util.isError(new Error()), util.isError(new TypeError()), util.isError({ message: 'm' }), util.isError(Object.create(Error.prototype))]",
                js = "[true,true,false,true]",
                kt = "[true,true,false,false]",
                reason = "the d.ts source summary is isObject(e) && (objectToString(e) === '[object Error]' || e instanceof Error); " +
                        "Kotlin misses the instanceof branch; restore (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "[util.isEmptyObject({}), util.isEmptyObject({ a: 1 }), util.isEmptyObject([]), util.isEmptyObject(Object.create({ a: 1 }))]",
                js = "[true,false,true,false]",
                kt = "[true,false,true,true]",
                reason = "documented: an object without own properties; the obsolete for-in loop also saw inherited enumerable ones; keep Kotlin",
            ),
            DifferentialCases.KnownDifference("util.checkStringParam('abc', /b/)", js = "true", kt = "throws Error", reason = CHECK_STRING_REGEXP),
            DifferentialCases.KnownDifference("util.checkStringParam('abc', /^b\$/)", js = "false", kt = "throws Error", reason = CHECK_STRING_REGEXP),
            DifferentialCases.KnownDifference(
                "util.checkStringParam({ toString() { return 'abc'; } }, 'abc')",
                js = "true",
                kt = "throws Error",
                reason = "documented: checkStringParam takes any src and the d.ts allows { toString(): string }; Kotlin rejects objects with the " +
                        "message 'Param src must be non-nullish'; restore toString handling for this alias (P2.3)",
            ),
            DifferentialCases.KnownDifference(
                "util.toRegularAndCall((a, b) => [a, b], 1, 2)",
                js = "[1,2]",
                kt = "throws RhinoUtils\$ObsoletedRhinoFunctionException",
                reason = "documented as ABANDONED (always throws ObsoletedRhinoFunctionException); keep Kotlin",
            ),
            DifferentialCases.KnownDifference(
                "util.toRegularAndApply((a, b) => a + b, [1, 2])",
                js = "3",
                kt = "throws RhinoUtils\$ObsoletedRhinoFunctionException",
                reason = "documented as ABANDONED; keep Kotlin (its message names toRegularAndCall by mistake)",
            ),
        )

    }

}
