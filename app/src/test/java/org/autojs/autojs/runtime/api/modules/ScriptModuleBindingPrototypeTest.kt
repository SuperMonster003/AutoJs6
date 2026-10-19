package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding
import org.autojs.autojs.runtime.api.modules.generated.SampleModuleBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P1.1: the bindings that `ksp-script-modules-processor` generates for the test fixture `SampleModule` (every
 * annotation and parameter kind) and for the example `Base64Module` (main source set) compile, mount on a global object
 * and dispatch calls, getters, setters, overloads and errors as declared.
 */
class ScriptModuleBindingPrototypeTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        val global = rhino.ktScope.globalThis
        ScriptModuleMounter.mount(SampleModuleBinding, rhino.ktRuntime, global, global, rhino.ktScope, rhino.context)
        ScriptModuleMounter.mount(Base64ModuleBinding, rhino.ktRuntime, global, global, rhino.ktScope, rhino.context)
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
    fun base64Example() = check(
        listOf(
            "base64.encode('hello')" to "\"aGVsbG8=\"",
            "base64.encode('\\u4f60\\u597d', 'utf_16le')" to "\"YE99WQ==\"",
            "base64.encode(123)" to "\"MTIz\"",
            "base64.encode({ toString() { return 'obj'; } })" to "\"b2Jq\"",
            "base64.decode('aGVsbG8=')" to "\"hello\"",
            "base64.decode({ toString() { return 'aGk='; } })" to "\"hi\"",
            "base64.encode('a', 'definitely-not-a-charset')" to "throws RangeError",
            "base64.encode(null)" to "throws TypeError",
            "base64.encode()" to "throws TypeError",
            "\$base64 === base64" to "true",
        )
    )

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
    }

}
