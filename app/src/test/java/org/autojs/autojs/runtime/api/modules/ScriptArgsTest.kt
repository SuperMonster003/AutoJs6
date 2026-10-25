package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.util.concurrent.TimeUnit

/**
 * Roadmap P1.4 / T4: the conversion table of the generated layer ([ScriptArgs]), case by case: what each parameter
 * kind accepts and rejects, the error type (`TypeError` for a value of the wrong kind, `RangeError` for a number that
 * is not an Int / Long), trailing `undefined` arguments counted as omitted, the overload guards and the messages.
 * Messages are the English fallbacks (no Android resources on the JVM, see [ScriptMessagesTest]). The overload and
 * count rules are also checked end to end through the generated binding of the fixture `SampleModule`.
 */
class ScriptArgsTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, rhino.ktScope.globalThis).install("sample")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun js(source: String): Any? = rhino.evalKt(source)

    private fun wrapped(value: Any): Any = NativeJavaObject(rhino.ktScope, value, value.javaClass)

    /** The JS error [block] throws (name and message), or "ok". */
    private fun errorOf(block: () -> Any?): String = try {
        block()
        "ok"
    } catch (e: EcmaError) {
        "${e.name}: ${e.errorMessage}"
    }

    private fun errorName(block: () -> Any?): String = errorOf(block).substringBefore(':')

    private val undefined: Any = Undefined.instance

    @Test
    fun trailingUndefinedArgumentsAreOmitted() {
        assertEquals(0, ScriptArgs.count(arrayOf(), 0))
        assertEquals(1, ScriptArgs.count(arrayOf("a", undefined, undefined), 1))
        assertEquals(0, ScriptArgs.count(arrayOf(undefined, undefined), 0))
        // Never below the required count: inspect(undefined) still passes one argument.
        assertEquals(1, ScriptArgs.count(arrayOf(undefined), 1))
        assertEquals(2, ScriptArgs.count(arrayOf(undefined, undefined), 2))
        // Only trailing ones, and null is a value.
        assertEquals(2, ScriptArgs.count(arrayOf(undefined, "b"), 0))
        assertEquals(3, ScriptArgs.count(arrayOf(undefined, undefined, null), 0))
    }

    @Test
    fun strings() {
        val accepted = listOf<Pair<Any?, String>>(
            "a" to "a",
            ConsString("con", "cat") to "concat",
            StringBuilder("sb") to "sb",
            js("123") to "123",
            js("1.5") to "1.5",
            js("-0") to "0",
            123.0 to "123",
            true to "true",
            js("({ toString() { return 'obj'; } })") to "obj",
            js("[1, 2]") to "1,2",
            wrapped("java") to "java",
            TimeUnit.SECONDS to "SECONDS",
        )
        accepted.forEach { (value, expected) -> assertEquals("string($value)", expected, ScriptArgs.string(value, "f", "p")) }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type string, got null ]", errorOf { ScriptArgs.string(null, "f", "p") })
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type string, got undefined ]", errorOf { ScriptArgs.string(undefined, "f", "p") })
        assertEquals("TypeError", errorName { ScriptArgs.string(js("Symbol('s')"), "f", "p") })
        assertNull(ScriptArgs.stringOrNull(null, "f", "p"))
        assertNull(ScriptArgs.stringOrNull(undefined, "f", "p"))
        assertEquals("1", ScriptArgs.stringOrNull(1, "f", "p"))
    }

    @Test
    fun booleansAreNotConvertedByTruthiness() {
        assertEquals(true, ScriptArgs.boolean(true, "f", "p"))
        assertEquals(false, ScriptArgs.boolean(js("false"), "f", "p"))
        assertEquals(true, ScriptArgs.boolean(wrapped(java.lang.Boolean.TRUE), "f", "p"))
        listOf(1, "true", js("({})"), null, undefined).forEach { value ->
            assertEquals("boolean($value)", "TypeError", errorName { ScriptArgs.boolean(value, "f", "p") })
        }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type boolean, got number ]", errorOf { ScriptArgs.boolean(1, "f", "p") })
        assertNull(ScriptArgs.booleanOrNull(undefined, "f", "p"))
    }

    @Test
    fun numbersAcceptNumbersBoxedJavaNumbersAndNumericStrings() {
        val accepted = listOf<Pair<Any?, Double>>(
            js("1") to 1.0,
            2.5 to 2.5,
            3L to 3.0,
            7 to 7.0,
            wrapped(Integer.valueOf(7)) to 7.0,
            "3" to 3.0,
            " 4 " to 4.0,
            "0x10" to 16.0,
            "-1e3" to -1000.0,
            ConsString("1", "2") to 12.0,
            // JS ToNumber of an empty or blank string.
            "" to 0.0,
        )
        accepted.forEach { (value, expected) -> assertEquals("double($value)", expected, ScriptArgs.double(value, "f", "p"), 0.0) }
        assertTrue(ScriptArgs.double("NaN", "f", "p").isNaN())
        assertTrue(ScriptArgs.double(Double.NaN, "f", "p").isNaN())
        listOf("abc", "1px", true, js("({})"), js("[1]"), null, undefined).forEach { value ->
            assertEquals("double($value)", "TypeError", errorName { ScriptArgs.double(value, "f", "p") })
        }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type number, got string ]", errorOf { ScriptArgs.double("abc", "f", "p") })
        assertEquals(1.5f, ScriptArgs.float("1.5", "f", "p"))
        assertNull(ScriptArgs.doubleOrNull(null, "f", "p"))
        assertNull(ScriptArgs.floatOrNull(undefined, "f", "p"))
    }

    @Test
    fun intsMustBeIntegersInRange() {
        assertEquals(3, ScriptArgs.int(3.0, "f", "n"))
        assertEquals(7, ScriptArgs.int("7", "f", "n"))
        assertEquals(0, ScriptArgs.int(-0.0, "f", "n"))
        assertEquals(Int.MIN_VALUE, ScriptArgs.int(-2147483648.0, "f", "n"))
        assertEquals(Int.MAX_VALUE, ScriptArgs.int(2147483647.0, "f", "n"))
        listOf(2147483648.0, -2147483649.0, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertEquals("int($value)", "RangeError", errorName { ScriptArgs.int(value, "f", "n") })
        }
        assertEquals("RangeError: Failed to call method \"f\": [ Argument \"n\" must be a 32-bit integer, got 1.5 ]", errorOf { ScriptArgs.int(1.5, "f", "n") })
        assertEquals("RangeError: Failed to call method \"f\": [ Argument \"n\" must be a 32-bit integer, got 2147483648 ]", errorOf { ScriptArgs.int(2147483648.0, "f", "n") })
        // A value of the wrong kind is a TypeError before any range check.
        assertEquals("TypeError", errorName { ScriptArgs.int("abc", "f", "n") })
        assertNull(ScriptArgs.intOrNull(undefined, "f", "n"))
    }

    @Test
    fun longsMustBeIntegersInRange() {
        assertEquals(9007199254740992L, ScriptArgs.long(9007199254740992.0, "f", "n"))
        assertEquals(Long.MIN_VALUE, ScriptArgs.long(-9.223372036854775808E18, "f", "n"))
        assertEquals(-5L, ScriptArgs.long("-5", "f", "n"))
        // 2^63 is the double nearest to Long.MAX_VALUE and already out of range (it must not saturate silently).
        listOf(9.223372036854775808E18, 1e19, -1e19, 0.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertEquals("long($value)", "RangeError", errorName { ScriptArgs.long(value, "f", "n") })
        }
        assertEquals("RangeError: Failed to call method \"f\": [ Argument \"n\" must be a 64-bit integer, got 9223372036854776000 ]", errorOf { ScriptArgs.long(9.223372036854775808E18, "f", "n") })
        assertNull(ScriptArgs.longOrNull(null, "f", "n"))
    }

    @Test
    fun bytes() {
        val raw = byteArrayOf(1, 2)
        assertSame(raw, ScriptArgs.bytes(raw, "f", "p"))
        assertSame(raw, ScriptArgs.bytes(wrapped(raw), "f", "p"))
        // Numbers narrow like Number.byteValue(), other elements are 0.
        assertArrayEquals(byteArrayOf(104, 105, 44, 0, -1), ScriptArgs.bytes(js("[104, 105, 300, 'x', 255]"), "f", "p"))
        listOf("ab", js("({})"), 1, null).forEach { value ->
            assertEquals("bytes($value)", "TypeError", errorName { ScriptArgs.bytes(value, "f", "p") })
        }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type byte[] / number[], got string ]", errorOf { ScriptArgs.bytes("ab", "f", "p") })
        assertNull(ScriptArgs.bytesOrNull(undefined, "f", "p"))
    }

    @Test
    fun lists() {
        assertEquals(listOf("a", "b"), ScriptArgs.list(js("['a', 'b']"), "f", "p"))
        // Holes of a sparse array become undefined, never Scriptable.NOT_FOUND.
        assertEquals(listOf("a", undefined, "c"), ScriptArgs.list(js("['a', , 'c']"), "f", "p"))
        val javaList = arrayListOf<Any?>(1, 2)
        assertSame(javaList, ScriptArgs.list(javaList, "f", "p"))
        assertSame(javaList, ScriptArgs.list(wrapped(javaList), "f", "p"))
        listOf("a", js("({ length: 1, 0: 'x' })"), 1, null, undefined).forEach { value ->
            assertEquals("list($value)", "TypeError", errorName { ScriptArgs.list(value, "f", "p") })
        }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type Array, got Object ]", errorOf { ScriptArgs.list(js("({})"), "f", "p") })
        assertNull(ScriptArgs.listOrNull(null, "f", "p"))
    }

    @Test
    fun maps() {
        val map = ScriptArgs.map(js("({ b: 'x', a: 1, 1: true, [Symbol('s')]: 0 })"), "f", "p")
        // Own enumerable keys as strings, index keys included; symbol keys are not string keys.
        assertEquals(setOf("1", "a", "b"), map.keys)
        assertEquals("x", map["b"])
        assertEquals(true, map["1"])
        assertEquals(setOf("a"), ScriptArgs.map(js("Object.defineProperty({ a: 1 }, 'hidden', { value: 2 })"), "f", "p").keys)
        assertEquals(emptySet<String>(), ScriptArgs.map(js("Object.create({ inherited: 1 })"), "f", "p").keys)
        assertEquals(mapOf("1" to "x"), ScriptArgs.map(hashMapOf(1 to "x"), "f", "p"))
        assertEquals(mapOf("k" to 2), ScriptArgs.map(wrapped(hashMapOf("k" to 2)), "f", "p"))
        listOf(js("[1]"), "a", 1, null).forEach { value ->
            assertEquals("map($value)", "TypeError", errorName { ScriptArgs.map(value, "f", "p") })
        }
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type Object, got Array ]", errorOf { ScriptArgs.map(js("[1]"), "f", "p") })
        assertNull(ScriptArgs.mapOrNull(undefined, "f", "p"))
    }

    @Test
    fun typedValues() {
        assertSame(TimeUnit.SECONDS, ScriptArgs.typed(TimeUnit.SECONDS, TimeUnit::class.java, "f", "p"))
        assertSame(TimeUnit.SECONDS, ScriptArgs.typed(wrapped(TimeUnit.SECONDS), TimeUnit::class.java, "f", "p"))
        val obj = js("({})")
        assertSame(obj, ScriptArgs.typed(obj, Scriptable::class.java, "f", "p"))
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type java.util.concurrent.TimeUnit, got string ]", errorOf { ScriptArgs.typed("SECONDS", TimeUnit::class.java, "f", "p") })
        // P2.1: Rhino types are named the script way in messages.
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" must be of type Array, got number ]", errorOf { ScriptArgs.typed(1.0, NativeArray::class.java, "f", "p") })
        assertEquals(listOf("Array", "Object", "function", "function", "object", "object"), listOf(
            NativeArray::class.java, NativeObject::class.java, org.mozilla.javascript.Callable::class.java, org.mozilla.javascript.BaseFunction::class.java,
            Scriptable::class.java, org.mozilla.javascript.ScriptableObject::class.java,
        ).map { ScriptArgs.scriptTypeName(it) })
        assertEquals("TypeError", errorName { ScriptArgs.typed(null, TimeUnit::class.java, "f", "p") })
        assertNull(ScriptArgs.typedOrNull(undefined, TimeUnit::class.java, "f", "p"))
        assertEquals("TypeError: Failed to call method \"f\": [ Argument \"p\" cannot be undefined ]", errorOf { ScriptArgs.nonNullish(undefined, "f", "p") })
        assertSame(obj, ScriptArgs.nonNullish(obj, "f", "p"))
    }

    @Test
    fun variableArguments() {
        val args = arrayOf<Any?>("sep", "a", 2, "3")
        assertArrayEquals(arrayOf<Any?>(2, "3"), ScriptArgs.rest(args, 2, "f", "rest"))
        assertArrayEquals(arrayOf("a", "2", "3"), ScriptArgs.strings(args, 1, "f", "parts"))
        assertArrayEquals(doubleArrayOf(2.0, 3.0), ScriptArgs.doubles(args, 2, "f", "values"), 0.0)
        assertArrayEquals(intArrayOf(2, 3), ScriptArgs.ints(args, 2, "f", "values"))
        assertEquals(0, ScriptArgs.rest(args, 4, "f", "rest").size)
        assertEquals(0, ScriptArgs.strings(args, 9, "f", "parts").size)
        assertEquals("TypeError", errorName { ScriptArgs.doubles(args, 1, "f", "values") })
        assertEquals("RangeError", errorName { ScriptArgs.ints(arrayOf(1.5), 0, "f", "values") })
    }

    @Test
    fun overloadGuards() {
        assertTrue(ScriptArgs.isString("a") && ScriptArgs.isString(ConsString("a", "b")) && ScriptArgs.isString(wrapped("j")))
        assertFalse(ScriptArgs.isString(1) || ScriptArgs.isString(null) || ScriptArgs.isString(js("({})")))
        assertTrue(ScriptArgs.isNumber(1.0) && ScriptArgs.isNumber(wrapped(Integer.valueOf(1))))
        assertFalse(ScriptArgs.isNumber("1") || ScriptArgs.isNumber(true))
        assertTrue(ScriptArgs.isBoolean(false) && ScriptArgs.isBoolean(wrapped(java.lang.Boolean.FALSE)))
        assertFalse(ScriptArgs.isBoolean(0) || ScriptArgs.isBoolean("false"))
        assertTrue(ScriptArgs.isBytes(byteArrayOf()) && ScriptArgs.isBytes(js("[1]")) && ScriptArgs.isBytes(wrapped(byteArrayOf())))
        assertFalse(ScriptArgs.isBytes("ab") || ScriptArgs.isBytes(js("({})")))
        assertTrue(ScriptArgs.isList(js("[]")) && ScriptArgs.isList(arrayListOf<Any>()) && ScriptArgs.isList(wrapped(arrayListOf<Any>())))
        assertFalse(ScriptArgs.isList(js("({})")) || ScriptArgs.isList("a"))
        assertTrue(ScriptArgs.isMap(js("({})")) && ScriptArgs.isMap(hashMapOf<Any, Any>()))
        assertFalse(ScriptArgs.isMap(js("[]")) || ScriptArgs.isMap(null))
        assertTrue(ScriptArgs.isInstance(TimeUnit.DAYS, TimeUnit::class.java) && ScriptArgs.isInstance(wrapped(TimeUnit.DAYS), TimeUnit::class.java))
        assertFalse(ScriptArgs.isInstance("DAYS", TimeUnit::class.java))
        assertTrue(ScriptArgs.isNullish(null) && ScriptArgs.isNullish(undefined))
        assertFalse(ScriptArgs.isNullish(0) || ScriptArgs.isNullish(""))
    }

    @Test
    fun errorsOfTheGeneratedLayer() {
        assertEquals("TypeError: Failed to call method \"f\": [ Method only accepts a number of arguments in the range [1..2] ]", errorOf { throw ScriptArgs.arityError("f", 1, 2, 3) })
        assertEquals("TypeError: Failed to call method \"f\": [ Method only accepts 1 argument ]", errorOf { throw ScriptArgs.arityError("f", 1, 1, 0) })
        assertEquals("TypeError: Failed to call method \"f\": [ Method only accepts 0 arguments ]", errorOf { throw ScriptArgs.arityError("f", 0, 0, 1) })
        assertEquals("TypeError: Failed to call method \"f\": [ Method only accepts no less than 2 arguments ]", errorOf { throw ScriptArgs.arityError("f", 2, -1, 1) })
        assertEquals(
            "TypeError: Failed to call method \"f\": [ No overload accepts the arguments (boolean, null, Array). Candidates: f(a: kotlin.String) ]",
            errorOf { throw ScriptArgs.noOverload("f", arrayOf(true, null, js("[]")), "f(a: kotlin.String)") },
        )
        assertTrue(ScriptArgs.unknownMember("m", 9) is IllegalStateException)
        assertSame(Undefined.instance, ScriptArgs.undefined(Unit))
    }

    @Test
    fun descriptions() {
        val cases = listOf<Pair<Any?, String>>(
            null to "null",
            undefined to "undefined",
            "s" to "string",
            ConsString("a", "b") to "string",
            1.5 to "number",
            7 to "number",
            true to "boolean",
            js("[1]") to "Array",
            js("({})") to "Object",
            js("(function () { })") to "function",
            js("new Date(0)") to "Date",
            js("/x/") to "RegExp",
            js("Symbol('s')") to "symbol",
            wrapped(TimeUnit.SECONDS) to "java.util.concurrent.TimeUnit",
            TimeUnit.SECONDS to "java.util.concurrent.TimeUnit",
        )
        cases.forEach { (value, expected) -> assertEquals("describe($value)", expected, ScriptArgs.describe(value)) }
    }

    /** The same rules end to end through the generated binding of `SampleModule`. */
    @Test
    fun generatedDispatch() {
        val cases = listOf(
            // Trailing undefined: omitted down to the required count, then the default applies.
            "sample.add(1, undefined, undefined)" to "2",
            "sample.flag(undefined, undefined)" to "false:7",
            "(function () { try { sample.add(undefined); return 'ok'; } catch (e) { return e.name; } })()" to "TypeError",
            // Overloads: by count, then by the kind of the arguments; Scriptable candidates are tried before
            // primitive ones, so a wrapped Java number (a Scriptable) takes the object overload.
            "sample.pick([1])" to "object:Array",
            "sample.pick(2)" to "number:2.0",
            "sample.pick(java.lang.Integer.valueOf(3))" to "object:JavaObject",
            "sample.pick(1, 2)" to "strings:12",
            "(function () { try { sample.pick('3'); } catch (e) { return e.name + ': ' + e.message; } })()" to
                "TypeError: Failed to call method \"sample.pick\": [ No overload accepts the arguments (string). Candidates: pick(o: org.mozilla.javascript.Scriptable) | pick(n: kotlin.Double) ]",
            // Integer parameters and their ranges.
            "sample.flag(true, '3')" to "true:3",
            "(function () { try { sample.flag(true, Math.pow(2, 63)); } catch (e) { return e.name; } })()" to "RangeError",
            "(function () { try { sample.repeat('a', Math.pow(2, 31)); } catch (e) { return e.name; } })()" to "RangeError",
            // Variable arguments convert every element.
            "sample.join('-', 1, 'b')" to "1-b",
            "(function () { try { sample.sum(1, 'x'); } catch (e) { return e.name; } })()" to "TypeError",
            // Extra arguments are refused (appendix C Q7).
            "(function () { try { sample.touch('a', 'b'); } catch (e) { return e.name + ': ' + e.message; } })()" to
                "TypeError: Failed to call method \"sample.touch\": [ Method only accepts 1 argument ]",
            "sample.keys({ 2: 'x', a: 1 }).join()" to "2,a",
            "sample.count([1, , 3])" to "3",
            // The module object called or constructed is named like the mounter names it (appendix C Q19, P2.4).
            "(function () { try { sample(); } catch (e) { return e.name + ': ' + e.message; } })()" to
                "TypeError: Failed to call method \"sample\": [ Method only accepts 1 argument ]",
            "(function () { try { new sample(); } catch (e) { return e.name + ': ' + e.message; } })()" to
                "TypeError: Failed to call method \"new sample\": [ Method only accepts 1 argument ]",
        )
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = js(expression).let { if (it is CharSequence) it.toString() else org.mozilla.javascript.Context.toString(it) }
            if (actual == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * P2.4: [ScriptArgs.toJs] returns the values `RhinoUtils.toJsValue` leaves unchanged without entering it, and gives
     * every other value the same conversion; [ScriptArgs.rest] from index 0 is the argument array itself.
     */
    @Test
    fun resultsMatchTheRhinoUtilsConversion() {
        val scope = rhino.ktScope
        val unchanged = listOf(null, "s", true, 1.5, 7, js("({ a: 1 })"), js("[1]"), undefined)
        unchanged.forEach { value -> assertSame("toJs($value)", RhinoUtils.toJsValue(scope, value), ScriptArgs.toJs(scope, value)) }
        val converted = listOf(7L, 2.5f, ConsString("a", "b"), Unit, Scriptable.NOT_FOUND, listOf(1, "x"), arrayOf("a"), mapOf("k" to 1), TimeUnit.SECONDS)
        converted.forEach { value ->
            val expected = RhinoUtils.toJsValue(scope, value)
            val actual = ScriptArgs.toJs(scope, value)
            assertEquals("toJs($value)", expected?.javaClass, actual?.javaClass)
            assertEquals("toJs($value)", org.mozilla.javascript.Context.toString(expected), org.mozilla.javascript.Context.toString(actual))
        }
        val args = arrayOf<Any?>("a", 1.0)
        assertSame(args, ScriptArgs.rest(args, 0, "f", "p"))
        assertArrayEquals(arrayOf<Any?>(1.0), ScriptArgs.rest(args, 1, "f", "p"))
        assertEquals(0, ScriptArgs.rest(args, 2, "f", "p").size)
    }

    @Test
    fun conversionsOfArraysAndObjectsKeepRawScriptValues() {
        val list = ScriptArgs.list(js("[{ a: 1 }, [2]]"), "f", "p")
        assertTrue(list[0] is NativeObject && list[1] is NativeArray)
        assertThrows(EcmaError::class.java) { ScriptArgs.list(js("Symbol('s')"), "f", "p") }
    }

}
