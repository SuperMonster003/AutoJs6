package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.autojs.autojs.runtime.api.modules.sample.EagerProbeModule
import org.autojs.autojs.runtime.api.modules.sample.FakeEmitter
import org.autojs.autojs.runtime.api.modules.sample.ProbeModule
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.LazyDataSlot
import java.io.File

/**
 * Roadmap P1.2: on-demand mounting (T3) of the generated registries, sub-modules, toString roles, prototype chains
 * with an emitter, eager modules, the error model (T5) and the surface of the migrated base64 module against the
 * device baseline `tools/augment/baseline/surface-XQ-AT72-5586.txt`.
 */
class ScriptModuleMounterTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var mounter: ScriptModuleMounter
    private val consoleEntries = mutableListOf<Pair<Int, String>>()

    @Before
    fun setUp() {
        ProbeModule.created = 0
        EagerProbeModule.created = 0
        rhino = DifferentialRhino()
        val emitters = ScriptModuleMounter.EmitterFactory { _, scope -> ScriptModuleMounter.EmitterFactory.emitterObject(FakeEmitter(), scope) }
        val console = ScriptErrorMapper.DebugConsole { level, text -> consoleEntries += level to text.toString() }
        mounter = ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, rhino.ktScope.globalThis, emitters, console)
        assertTrue(mounter.install("probe"))
        assertTrue(mounter.install("eagerProbe"))
        assertFalse(mounter.install("noSuchModule"))
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

    private val global get() = rhino.ktScope.globalThis

    @Test
    fun namesAreVisibleBeforeTheModuleIsBuilt() {
        check(
            listOf(
                "['probe', '\$probe', 'probeAlias', '\$probeAlias', 'probeGlobal', 'probeConstant', 'probeCreated', 'ProbeUnit', 'probeChildGlobal'].map(k => Object.keys(globalThis).indexOf(k) >= 0)" to
                    "[true,true,true,true,true,true,true,true,true]",
                "Object.prototype.hasOwnProperty.call(globalThis, 'probe') && 'probeAlias' in globalThis" to "true",
                "Object.keys(globalThis).filter(k => /^(probe|\\\$probe|probeAlias|\\\$probeAlias)\$/.test(k)).join()" to "\"probe,\$probe,probeAlias,\$probeAlias\"",
            )
        )
        assertEquals("Listing names must not build the module", 0, ProbeModule.created)
        assertTrue(LazyDataSlot.isPending(global, "probe"))
        assertFalse(mounter.isBuilt("probe"))
    }

    /** P1.4: reflection that lists or tests names (keys, own names, for-in, in, hasOwnProperty) never builds a module. */
    @Test
    fun reflectionDoesNotBuildPendingModules() {
        check(
            listOf(
                "['probe', '\$probe', 'probeAlias', 'probeGlobal', 'probeConstant'].every(k => Object.getOwnPropertyNames(globalThis).indexOf(k) >= 0)" to "true",
                "(function () { var seen = []; for (var k in globalThis) if (/^\\\$?probe(Alias)?\$/.test(k)) seen.push(k); return seen.join(); })()" to "\"probe,\$probe,probeAlias,\$probeAlias\"",
                "Object.prototype.propertyIsEnumerable.call(globalThis, '\$probe')" to "true",
                "['probe', '\$probe', 'probeChildGlobal', 'ProbeUnit'].every(k => k in globalThis && globalThis.hasOwnProperty(k))" to "true",
                // Sub-modules and the `$` form of a module without dollarPrefix are not global names.
                "['child', 'sibling', '\$child', '\$eagerProbe'].some(k => k in globalThis)" to "false",
            )
        )
        assertEquals("Reflection must not build the module", 0, ProbeModule.created)
        assertTrue(LazyDataSlot.isPending(global, "\$probe"))
    }

    /**
     * P1.4: `delete` and `Object.defineProperty` on pending names keep the final attributes: non-configurable names
     * refuse both (strict mode throws TypeError), a refused delete does not build the module, and a writable name can
     * still be redefined with a new value, which replaces the pending one without building the module. A descriptor
     * that omits `enumerable` keeps the current enumerability (Rhino-For-AutoJs6 88748ed39; the jar used to compare the
     * missing field as false and refuse `{ value: 1 }`).
     */
    @Test
    fun deleteAndDefinePropertyOnPendingNames() {
        check(
            listOf(
                "delete globalThis.probe" to "false",
                "delete globalThis.probeGlobal" to "false",
                "(function () { 'use strict'; try { delete globalThis.\$probeAlias; return 'deleted'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
                "['probe', 'probeGlobal', '\$probeAlias'].every(k => globalThis.hasOwnProperty(k))" to "true",
            )
        )
        assertEquals("A refused delete must not build the module", 0, ProbeModule.created)
        check(
            listOf(
                "(function () { try { Object.defineProperty(globalThis, 'probe', { get: function () { return 1; } }); return 'redefined'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
                "(function () { try { Object.defineProperty(globalThis, 'probeAlias', { enumerable: false }); return 'redefined'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
                "(function () { try { Object.defineProperty(globalThis, 'eagerProbe', { value: 1 }); return eagerProbe; } catch (e) { return e.name; } })()" to "1",
                "(function () { try { Object.defineProperty(globalThis, 'probeGlobal', { value: 1 }); return probeGlobal; } catch (e) { return e.name; } })()" to "1",
                "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'probeGlobal'))" to "\"{\\\"value\\\":1,\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
            )
        )
        assertEquals("Redefining a pending writable name must not build the module", 0, ProbeModule.created)
        check(
            listOf(
                "(Object.defineProperty(globalThis, 'probeGlobal', { value: 5, enumerable: true }), probeGlobal)" to "5",
                "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'probeGlobal'))" to "\"{\\\"value\\\":5,\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
                "typeof probe.probeGlobal" to "\"function\"",
                "Object.keys(probe).indexOf('probeGlobal') >= 0 && probe === probeAlias" to "true",
            )
        )
        assertEquals(1, ProbeModule.created)
    }

    /** T3: assigning a writable name before its first read replaces the pending value; read-only names ignore it. */
    @Test
    fun assignmentBeforeTheFirstRead() {
        check(
            listOf(
                "(probeGlobal = 'mine', probeGlobal)" to "\"mine\"",
                "(probe = 1, typeof probe)" to "\"object\"",
                "(function () { 'use strict'; try { \$probeAlias = 1; return 'assigned'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
            )
        )
        assertEquals("Reading probe builds it once; the assigned global member keeps the script's value", 1, ProbeModule.created)
        check(listOf("probeGlobal" to "\"mine\"", "probe.probeGlobal()" to "\"global\""))
    }

    /** `$` forms exist only with dollarPrefix, share the object and the attributes of the plain names. */
    @Test
    fun dollarAliases() = check(
        listOf(
            "\$probeAlias === probe && \$probe === probe" to "true",
            "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, '\$probe'), (k, v) => k === 'value' ? typeof v : v) === JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'probe'), (k, v) => k === 'value' ? typeof v : v)" to "true",
            "typeof eagerProbe + ':' + typeof globalThis['\$eagerProbe']" to "\"object:undefined\"",
            "['child', 'sibling'].map(k => typeof probe[k] + ':' + typeof probe['\$' + k]).join()" to "\"object:undefined,object:undefined\"",
            "probe.child === probe.child && probe.sibling.hi()" to "\"hi\"",
        )
    )

    @Test
    fun firstReadBuildsOnceAndEveryNameHoldsTheSameObject() {
        check(
            listOf(
                "typeof probe" to "\"object\"",
                "probe.ping() + probe.ping('x')" to "\"pongx\"",
                "probe === \$probe && probe === probeAlias && probe === \$probeAlias" to "true",
                "probe.probeGlobal === probeGlobal" to "true",
                "probeGlobal()" to "\"global\"",
                "probeConstant" to "\"constant\"",
                "typeof probe.probeConstant" to "\"undefined\"",
                "ProbeUnit.SECONDS.name()" to "\"SECONDS\"",
            )
        )
        assertEquals(1, ProbeModule.created)
        assertTrue(mounter.isBuilt("probe"))
        assertSame(mounter.moduleObject("probe"), global.get("probeAlias", global))
    }

    @Test
    fun descriptorsKeepTheirFinalAttributes() = check(
        listOf(
            // READONLY binding declared by @ScriptModule(attributes); reading the descriptor builds the module.
            "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, '\$probeAlias'), (k, v) => k === 'value' ? typeof v : v)" to
                "\"{\\\"value\\\":\\\"object\\\",\\\"writable\\\":false,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
            "Object.getOwnPropertyDescriptor(globalThis, 'probe').value === probe" to "true",
            "(function () { 'use strict'; try { probe = 1; return 'assigned'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
            "delete globalThis.probe" to "false",
            "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'probeGlobal'), (k, v) => k === 'value' ? typeof v : v)" to
                "\"{\\\"value\\\":\\\"function\\\",\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
        )
    )

    @Test
    fun globalGetterOnlyCreatesTheInstance() {
        check(listOf("probeCreated" to "1"))
        assertFalse("A global getter must not build the module object", mounter.isBuilt("probe"))
        check(listOf("probe.ping()" to "\"pong\"", "probeCreated" to "1"))
    }

    @Test
    fun subModulesAreBuiltWithTheirParent() {
        check(listOf("probeChildGlobal()" to "\"child-global\""))
        assertTrue("A sub-module's global member builds the top-level module", mounter.isBuilt("probe"))
        check(
            listOf(
                "probe.child.hello()" to "\"hello\"",
                "typeof probe.\$child" to "\"undefined\"",
                "typeof child" to "\"undefined\"",
                "Object.getOwnPropertyDescriptor(probe, 'child').enumerable" to "true",
            )
        )
    }

    @Test
    fun toStringRolesAndPrototypeChain() = check(
        listOf(
            "probe.toString(5)" to "\"converted:5.0\"",
            "Object.prototype.hasOwnProperty.call(probe, '__augmented_custom_to_string__')" to "true",
            "JSON.stringify(Object.getOwnPropertyDescriptor(probe, '__augmented_custom_to_string__'), (k, v) => k === 'value' ? typeof v : v)" to
                "\"{\\\"value\\\":\\\"function\\\",\\\"writable\\\":true,\\\"enumerable\\\":false,\\\"configurable\\\":false}\"",
            "Object.keys(probe).indexOf('__augmented_custom_to_string__')" to "-1",
            "String(eagerProbe)" to "\"[object EagerProbe]\"",
            "Object.prototype.hasOwnProperty.call(eagerProbe, '__augmented_custom_to_string__')" to "false",
            "[probe.fromA(), probe.fromB(), probe.emit('x')]" to "[\"a\",\"b\",\"emitted:x\"]",
            "Object.getPrototypeOf(Object.getPrototypeOf(Object.getPrototypeOf(Object.getPrototypeOf(probe)))) === Object.prototype" to "true",
            "Object.prototype.toString.call(Object.getPrototypeOf(Object.getPrototypeOf(Object.getPrototypeOf(probe))))" to "\"[object Object]\"",
        )
    )

    @Test
    fun eagerModulesAreBuiltAtInstall() {
        assertEquals(1, EagerProbeModule.created)
        assertTrue(mounter.isBuilt("eagerProbe"))
        assertFalse(LazyDataSlot.isPending(global, "eagerProbe"))
    }

    @Test
    fun errorModel() {
        check(
            listOf(
                "(function () { try { probe.fail(); } catch (e) { return e.name + ':' + e.javaException.getClass().getSimpleName(); } })()" to "\"JavaException:IllegalStateException\"",
                "(function () { try { probe.callBack(function () { throw new RangeError('x'); }); } catch (e) { return (e instanceof RangeError) + ':' + e.message; } })()" to "\"true:x\"",
                "(function () { try { probe.callBack(function () { throw 'raw'; }); } catch (e) { return e; } })()" to "\"raw\"",
                "(function () { try { probe.ping(1, 2); } catch (e) { return e instanceof TypeError; } })()" to "true",
            )
        )
        val previousErr = System.err
        val printed = java.io.ByteArrayOutputStream()
        System.setErr(java.io.PrintStream(printed, true, "UTF-8"))
        val failure = try {
            assertThrows(ScriptCallException::class.java) { rhino.evalKt("probe.fail()") }
        } finally {
            System.setErr(previousErr)
        }
        assertTrue(failure.wrappedException is IllegalStateException)
        assertTrue(failure.details(), failure.details().contains("probe.fail") && failure.details().contains("java.lang.IllegalStateException: boom"))
        assertEquals("Nothing is printed by default", "", printed.toString("UTF-8"))
        assertThrows(ScriptInterruptedException::class.java) { rhino.evalKt("probe.interrupt()") }
    }

    /**
     * Appendix C Q4: with the switch on (and the module not excluded), mapping a Java exception writes one D-level console
     * entry, also when the script catches it; argument errors and script errors never do, and nothing is written with
     * the switch off.
     */
    @Test
    fun debugPrintingWritesConsoleEntriesPerModule() {
        val previous = ScriptErrorMapper.debugPrinting
        val quiet = listOf(
            "(function () { try { probe.ping(1, 2); } catch (e) { return e.name; } })()" to "\"TypeError\"",
            "(function () { try { probe.callBack(function () { throw new Error('x'); }); } catch (e) { return e.message; } })()" to "\"x\"",
        )
        try {
            ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { false }
            check(quiet + ("(function () { try { probe.fail(); } catch (e) { return 'caught'; } })()" to "\"caught\""))
            assertTrue("No entries with the switch off: $consoleEntries", consoleEntries.isEmpty())

            ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { it == "probe" }
            check(quiet + ("(function () { try { probe.fail(); } catch (e) { return 'caught'; } })()" to "\"caught\""))
            assertEquals("Exactly the Java exception is printed: $consoleEntries", 1, consoleEntries.size)
            val (level, text) = consoleEntries.single()
            assertEquals(android.util.Log.DEBUG, level)
            val lines = text.lines()
            assertEquals("[modules] probe.fail: java.lang.IllegalStateException: boom", lines.first())
            assertTrue(text, lines.drop(1).dropLast(1).all { it.startsWith("    at org.autojs.autojs.runtime.api.modules.") })
            assertTrue(text, lines.any { it.startsWith("    at org.autojs.autojs.runtime.api.modules.sample.ProbeModule.fail(") })
            assertTrue(text, lines.last().startsWith("    ... ") && lines.last().endsWith(" more"))

            consoleEntries.clear()
            ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { it == "base64" }
            check(listOf("(function () { try { probe.fail(); } catch (e) { return 'caught'; } })()" to "\"caught\""))
            assertTrue("An excluded module prints nothing: $consoleEntries", consoleEntries.isEmpty())
        } finally {
            ScriptErrorMapper.debugPrinting = previous
        }
    }

    @Test
    fun errorMapperKeepsScriptErrorsAndInterruptions() {
        val script = JavaScriptException("x", "test", 1)
        assertSame(script, ScriptErrorMapper.map(script, "m"))
        val ecma: EcmaError = org.mozilla.javascript.ScriptRuntime.typeError("t")
        assertSame(ecma, ScriptErrorMapper.map(ecma, "m"))
        val pending = object : ContinuationPending(null) {}
        assertSame(pending, ScriptErrorMapper.map(pending, "m"))
        val interrupted = ScriptInterruptedException()
        assertSame(interrupted, ScriptErrorMapper.map(interrupted, "m"))
        assertTrue(ScriptErrorMapper.map(RuntimeException(InterruptedException()), "m") is ScriptInterruptedException)
        val overflow = StackOverflowError()
        assertSame(overflow, ScriptErrorMapper.map(overflow, "m"))
        val mapped = ScriptErrorMapper.map(UnsupportedOperationException("u"), "mod.fn")
        assertTrue(mapped is ScriptCallException && mapped.method == "mod.fn" && mapped.wrappedException is UnsupportedOperationException)
        assertEquals(listOf("assignProbe", "eagerProbe", "numberProbe", "plainProxy", "probe", "proxyProbe", "sample"), TestScriptModules.moduleNames)
    }

    /**
     * The migrated base64 module keeps the attributes, kinds, own members and prototype recorded on the device
     * (XQ-AT72, debug host 5586); only the function `length` follows the Kotlin signature now (required parameters)
     * instead of 0.
     */
    @Test
    fun base64MatchesTheSurfaceBaseline() {
        ScriptModuleMounter(ScriptModules, rhino.ktRuntime, rhino.ktScope, global).install("base64")
        assertTrue(LazyDataSlot.isPending(global, "base64"))
        val baseline = File(BASELINE).readLines().filter { it.startsWith("base64") || it.startsWith("\$base64") }.associate { line ->
            val (path, attributes, value) = line.split('\t', limit = 3)
            path to "$attributes ${value.substringBefore(' ')}"
        }
        assertTrue("baseline lines for base64 are missing", baseline.size >= 18)
        val describe = """
            (function () {
                var out = [];
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-'); }
                function walk(o, prefix, depth) {
                    Object.getOwnPropertyNames(o).sort().forEach(function (k) {
                        var d = Object.getOwnPropertyDescriptor(o, k);
                        out.push(prefix + k + '\t' + attrs(d) + ' ' + typeof d.value);
                        if (depth < 1 && (typeof d.value === 'object' || typeof d.value === 'function') && d.value !== null) walk(d.value, prefix + k + '.', depth + 1);
                    });
                }
                ['base64', '${'$'}base64'].forEach(function (k) {
                    var d = Object.getOwnPropertyDescriptor(globalThis, k);
                    out.push(k + '\t' + attrs(d) + ' ' + typeof d.value);
                    walk(d.value, k + '.', 0);
                });
                return out.join('\n');
            })()
        """.trimIndent()
        val actual = (rhino.evalKt(describe) as CharSequence).toString().lines().associate { line ->
            val (path, rest) = line.split('\t', limit = 2)
            path to rest
        }
        assertEquals(baseline.keys.sorted(), actual.keys.sorted())
        val differences = baseline.filter { (path, expected) -> actual[path] != expected }.map { (path, expected) -> "$path: baseline $expected, new ${actual[path]}" }
        assertTrue("Surface differences:\n" + differences.joinToString("\n"), differences.isEmpty())
        check(
            listOf(
                "Object.getPrototypeOf(base64) === Object.prototype" to "true",
                "base64 === \$base64" to "true",
                "[base64.encode.length, base64.decode.length]" to "[1,1]",
            )
        )
    }

    companion object {
        /** Unit tests run in the app module directory. */
        private const val BASELINE = "../tools/augment/baseline/surface-XQ-AT72-5586.txt"
    }

}
