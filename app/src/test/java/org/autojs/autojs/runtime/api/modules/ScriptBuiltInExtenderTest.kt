package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap P2.1: `@ScriptModule(builtIn)` and [ScriptBuiltInExtender] (the "extend JavaScript built-in objects" option and
 * jsox on the new layer), with the fixture `BuiltInProbeModule` extending `Number`: placement (static, prototype with the
 * receiver as first argument, global, built-in only), attributes, binding order, `length`, errors and re-extension.
 */
class ScriptBuiltInExtenderTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var mounter: ScriptModuleMounter

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        mounter = ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, rhino.ktScope.globalThis)
        mounter.install("numberProbe")
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
    fun nothingIsBoundBeforeTheExtension() = check(
        listOf(
            "typeof Number.twice" to "\"undefined\"",
            "typeof Number.prototype.plus" to "\"undefined\"",
            "typeof numberProbeGlobal" to "\"undefined\"",
            "numberProbe.twice(2)" to "4",
            "typeof numberProbe.negate" to "\"undefined\"",
            // Data properties come before functions, as in Augmentable.assign (P2.1 corrected the mounter's order).
            "Object.keys(numberProbe).join()" to "\"ANSWER,twice,plus,describe,fail,numberProbeGlobal\"",
        )
    )

    @Test
    fun extensionBindsEveryPlacement() {
        assertEquals(listOf("numberProbe"), mounter.builtIns.names)
        assertFalse(mounter.builtIns.extend("sample"))
        assertFalse(mounter.builtIns.extend("nope"))
        assertTrue(mounter.builtIns.extend("numberProbe"))
        check(
            listOf(
                "Number.ANSWER" to "42",
                "Number.twice(3)" to "6",
                "Number.negate(1)" to "-1",
                "Number.twice === numberProbe.twice" to "false",
                "(5).plus()" to "6",
                "(5).plus(2)" to "7",
                "new Number(5).plus(1)" to "6",
                "(5).describe()" to "\"number:5.0\"",
                "Number.describe('x')" to "\"other\"",
                "numberProbeGlobal()" to "\"global\"",
                "Number.prototype.plus.length" to "0",
                "Number.prototype.describe.length" to "0",
                "Number.twice.length" to "1",
                "Number.prototype.plus.name" to "\"plus\"",
                "Object.keys(Number).join()" to "\"ANSWER,twice,negate,describe\"",
                "(function () { var d = Object.getOwnPropertyDescriptor(Number.prototype, 'plus'); return [d.enumerable, d.writable, d.configurable]; })()" to "[true,true,false]",
                "(function () { var d = Object.getOwnPropertyDescriptor(Number, 'ANSWER'); return [d.enumerable, d.writable, d.configurable]; })()" to "[true,true,false]",
                "Object.getOwnPropertyDescriptor(globalThis, 'numberProbeGlobal').enumerable" to "true",
                "Number.twice('x')" to "throws TypeError",
                "Number.twice(1, 2)" to "throws TypeError",
                "(5).plus(1, 2)" to "throws TypeError",
                "(function () { try { (5).fail(); } catch (e) { return e.name + ':' + e.javaException.getClass().getSimpleName(); } })()" to "\"JavaException:IllegalStateException\"",
                "new Number.twice(1)" to "throws TypeError",
            )
        )
    }

    @Test
    fun extendingAgainReplacesTheFunctions() {
        mounter.builtIns.extend("numberProbe")
        rhino.evalKt("var aj6Twice = Number.twice; var aj6Plus = Number.prototype.plus;")
        mounter.builtIns.extend("numberProbe")
        check(listOf("Number.twice === aj6Twice" to "false", "Number.prototype.plus === aj6Plus" to "false", "Number.twice(4)" to "8"))
    }

    @Test
    fun extendBuiltInsOrRunsTheLegacyExtensionWithoutAMounter() {
        var legacy = 0
        ScriptModuleLayer.extendBuiltInsOr(null) { legacy++ }
        assertEquals(1, legacy)
    }

}
