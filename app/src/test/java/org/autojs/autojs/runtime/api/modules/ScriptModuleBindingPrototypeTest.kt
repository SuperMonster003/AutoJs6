package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject
import java.io.File

/**
 * Roadmap P1.1: the bindings that `ksp-script-modules-processor` generates for the test fixture `SampleModule` (every
 * annotation and parameter kind) and for the example `Base64Module` (main source set) compile, mount on a global object
 * and dispatch calls, getters, setters, overloads and errors as declared. Since P1.2 they are mounted on demand from
 * the generated registries, and the base64 cases are also written to `build/script-modules-device/` for
 * `tools/augment/device_eval.py --new-modules on`.
 */
class ScriptModuleBindingPrototypeTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val global = rhino.ktScope.globalThis
        ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, global).install("sample")
        ScriptModuleMounter(ScriptModules, rhino.ktRuntime, rhino.ktScope, global).install("base64")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun eval(expression: String): String = rhino.render(DifferentialRhino.Side.KT, expression).key

    private fun check(cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = eval(expression)
            if (actual == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun functionsOverloadsAndConversions() = check(
        listOf(
            "sample.add(1, 2)" to "3",
            "sample.add(1)" to "2",
            "sample.add(1, undefined)" to "2",
            "sample.plus(2)" to "3",
            "sample.add('3', 4)" to "7",
            "sample.add()" to "throws TypeError",
            "sample.add(1, 2, 3)" to "throws TypeError",
            "sample.add({}, 1)" to "throws TypeError",
            "sample.sum()" to "0",
            "sample.sum(1, 2, 3)" to "6",
            "sample.join('-', 'a', 'b')" to "\"a-b\"",
            "sample.join('-')" to "\"\"",
            "sample.pick({})" to "\"object:Object\"",
            "sample.pick(3)" to "\"number:3.0\"",
            "sample.pick('a', 'b')" to "\"strings:ab\"",
            "sample.pick(true)" to "throws TypeError",
            "sample.repeat('ab', 2)" to "\"abab\"",
            "sample.repeat('ab', 1.5)" to "throws RangeError",
            "sample.describe(1).split(':')[0]" to "\"200\"",
            "sample.touch('x')" to "undefined",
            "sample.size([1, 2, 3])" to "3",
            "sample.keys({ a: 1, b: 2 })" to "[\"a\",\"b\"]",
            "sample.count([1, 2])" to "2",
            "sample.unit(sample.TimeUnit.SECONDS)" to "\"SECONDS\"",
            "sample.unit('x')" to "throws TypeError",
            "sample.flag()" to "\"false:7\"",
            "sample.flag(true, 3)" to "\"true:3\"",
            "sample.flag(1)" to "throws TypeError",
            "sample.add.length" to "1",
            "sample.plus === sample.add" to "true",
        )
    )

    @Test
    fun accessorsPlacementAndModuleObject() = check(
        listOf(
            "[sample.reading, sample.reading]" to "[1,2]",
            "(sample.touch('x'), sample.lastTouched)" to "\"x\"",
            "(sample.lastTouched = 'y', sample.lastTouched)" to "\"y\"",
            "sample.runtimeName" to "\"ScriptRuntime\"",
            "sample.VERSION" to "\"1.0\"",
            "Object.getOwnPropertyDescriptor(sample, 'VERSION').writable" to "false",
            "sample.sampleEverywhere() + sampleEverywhere()" to "\"everywhereeverywhere\"",
            "typeof sample.sampleGlobalOnly" to "\"undefined\"",
            "sampleGlobalOnly()" to "\"global\"",
            "Object.getOwnPropertyDescriptor(globalThis, 'sampleGlobalOnly').enumerable" to "false",
            "typeof SampleTimeUnit" to "\"function\"",
            "sample(2)" to "\"called:2.0\"",
            "new sample('L').label" to "\"L\"",
            "sample.fromProto()" to "\"proto\"",
            "sample === sampleAlias && sample === \$sample && sample === \$sampleAlias" to "true",
            "Object.getOwnPropertyDescriptor(globalThis, 'sample').configurable" to "false",
        )
    )

    /** The example module fixes the two base64 deviations recorded in ROADMAP appendix E. */
    @Test
    fun base64Example() {
        check(BASE64_CASES)
        exportForDevice("Base64Module", BASE64_CASES)
    }

    @Test
    fun attributeConstantsMatchRhino() {
        assertEquals(ScriptableObject.READONLY, ScriptAttributes.READONLY)
        assertEquals(ScriptableObject.DONTENUM, ScriptAttributes.DONTENUM)
        assertEquals(ScriptableObject.PERMANENT, ScriptAttributes.PERMANENT)
        assertEquals(ScriptableObject.EMPTY, ScriptAttributes.EMPTY)
    }

    @Test
    fun metadataIsGenerated() {
        val files = javaClass.classLoader!!.getResources("autojs6/script-modules.json").toList().map { it.readText() }
        assertTrue("script-modules.json of the test fixtures is missing", files.any { "\"class\": \"org.autojs.autojs.runtime.api.modules.sample.SampleModule\"" in it })
        val sample = files.first { "SampleModule" in it }
        assertTrue(sample.contains("\"name\": \"pick\"") && sample.contains("\"kind\": \"TYPED\"") && sample.contains("\"global\": \"ONLY\""))
        assertTrue(sample.contains("\"toStringRole\": \"FUNCTIONAL\"") && sample.contains("\"emitter\": true") && sample.contains("\"attributes\": 5"))
    }

    @Test
    fun registriesListTheirCompilationUnit() {
        assertEquals(listOf("base64"), ScriptModules.bindings.map { it.spec.name })
        assertEquals(listOf("eagerProbe", "probe", "sample", "child"), TestScriptModules.bindings.map { it.spec.name })
        assertEquals("org.autojs.autojs.runtime.api.modules.sample.ProbeModule", TestScriptModules.find("\$probeAlias")?.spec?.className)
        assertEquals(listOf("child"), TestScriptModules.childrenOf("org.autojs.autojs.runtime.api.modules.sample.ProbeModule").map { it.spec.name })
    }

    /** Writes [cases] in the format of `tools/augment/device_eval.py` (expected values in "kt"). */
    private fun exportForDevice(name: String, cases: List<Pair<String, String>>) {
        val dir = File("build/script-modules-device").apply { mkdirs() }
        val entries = cases.joinToString(",\n") { (expression, expected) ->
            "  {\"expression\": ${json(expression)}, \"js\": null, \"kt\": ${json(expected)}, \"reason\": \"new script module layer\"}"
        }
        File(dir, "$name.json").writeText("[\n$entries\n]\n", Charsets.UTF_8)
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

    companion object {

        private const val BACKSLASH = '\\'

        private val BASE64_CASES = listOf(
            "base64.encode('hello')" to "\"aGVsbG8=\"",
            "base64.encode(String.fromCharCode(0x4f60, 0x597d), 'utf_16le')" to "\"YE99WQ==\"",
            "base64.encode(123)" to "\"MTIz\"",
            "base64.encode({ toString() { return 'obj'; } })" to "\"b2Jq\"",
            "base64.decode('aGVsbG8=')" to "\"hello\"",
            "base64.decode({ toString() { return 'aGk='; } })" to "\"hi\"",
            "base64.encode('a', 'definitely-not-a-charset')" to "throws RangeError",
            "base64.encode(null)" to "throws TypeError",
            "base64.encode()" to "throws TypeError",
            "base64.encode('a', 'UTF-8', 'extra')" to "throws TypeError",
            "base64.decode(undefined)" to "throws TypeError",
            "(function () { try { base64.encode(null); } catch (e) { return e instanceof TypeError; } })()" to "true",
            "(function () { try { base64.decode('aGk=', 'no-such-charset'); } catch (e) { return e instanceof RangeError; } })()" to "true",
            "base64.decode(base64.encode([104, 105]))" to "\"hi\"",
            "\$base64 === base64" to "true",
        )

    }

}
