package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.autojs.autojs.runtime.api.modules.sample.AssignProbeModule
import org.autojs.autojs.runtime.api.modules.sample.FakeEmitter
import org.autojs.autojs.runtime.api.modules.sample.ProbeModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.0 (appendix C Q17): the module shapes added for global and the heavy modules of P3. An assign module has
 * no module object and puts its members and prototypes on the global object; a name lookup prototype resolves names
 * without own properties (the Java classes of global); a proxy module object has no own properties and reads and writes
 * through handlers (ui / console); an install hook defines names computed at run time and acts once the members are
 * built; host code reaches sub-modules and instances by path. Fixtures: `modules/sample/AssignProbeModule.kt`.
 */
class ScriptModuleShapesTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var mounter: ScriptModuleMounter
    private lateinit var keysBefore: List<String>
    private lateinit var keysAfterAssign: List<String>

    @Before
    fun setUp() {
        AssignProbeModule.created = 0
        ProbeModule.created = 0
        rhino = DifferentialRhino()
        keysBefore = keys()
        val emitters = ScriptModuleMounter.EmitterFactory { _, scope -> ScriptModuleMounter.EmitterFactory.emitterObject(FakeEmitter(), scope) }
        mounter = ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, rhino.ktScope.globalThis, emitters)
        assertTrue(mounter.install("assignProbe"))
        keysAfterAssign = keys()
        listOf("probe", "proxyProbe", "plainProxy").forEach { assertTrue(it, mounter.install(it)) }
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private val global get() = rhino.ktScope.globalThis

    private fun keys(): List<String> = (rhino.evalKt("Object.keys(globalThis).join(',')") as CharSequence).toString().split(',')

    private fun check(cases: List<Pair<String, String>>) {
        val failures = cases.mapNotNull { (expression, expected) ->
            val actual = rhino.render(DifferentialRhino.Side.KT, expression).key
            if (actual == expected) null else "$expression\n    expected $expected\n    actual   $actual"
        }
        assertTrue("Unexpected results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** The members of an assign module are global names in declaration order, after the names its install hook defines. */
    @Test
    fun assignModuleBindsItsMembersOnTheGlobalObject() {
        assertEquals(
            listOf("assignDynamic", "assignConstant", "toString", "assignPing", "assignPingAlias", "assignCreated", "AssignProbeUnit"),
            keysAfterAssign.filter { it !in keysBefore },
        )
        check(
            listOf(
                "['assignProbe', '\$assignProbe'].some(k => k in globalThis)" to "false",
                "typeof globalThis.global === 'undefined' || globalThis.global === globalThis" to "true",
            )
        )
        assertFalse("Installing an assign module must not build it", mounter.isBuilt("assignProbe"))
        assertTrue(LazyDataSlot.isPending(global, "assignPing"))
        assertTrue(LazyDataSlot.isPending(global, "assignDynamic"))
        assertNull("An assign module has no module object", mounter.moduleObject("assignProbe"))
        check(
            listOf(
                "assignPing() + assignPing('x') + assignPingAlias('y')" to "\"pongxy\"",
                "assignConstant" to "\"constant\"",
                "String(globalThis.toString())" to "\"[object assignProbe]\"",
                "AssignProbeUnit.SECONDS.name()" to "\"SECONDS\"",
                "assignCreated" to "1",
                "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'assignPing'), (k, v) => k === 'value' ? typeof v : v)" to
                    "\"{\\\"value\\\":\\\"function\\\",\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
                "assignPing.length" to "0",
            )
        )
        assertTrue(mounter.isBuilt("assignProbe"))
        assertEquals("The instance is created once, at install", 1, AssignProbeModule.created)
    }

    /**
     * The name lookup prototype: no own properties, `in` and `hasOwnProperty` false, each read resolves the name. Like
     * the current getter `ProxyObject` it does not answer `has`, so `typeof` of a bare name, which Rhino binds through
     * `has`, is "undefined" while a read resolves it.
     */
    @Test
    fun nameLookupPrototypeOnTheGlobalObject() = check(
        listOf(
            "Object.prototype.toString.call(Object.getPrototypeOf(globalThis))" to "\"[object Object]\"",
            "Object.getOwnPropertyNames(Object.getPrototypeOf(globalThis)).length" to "0",
            "Object.getPrototypeOf(Object.getPrototypeOf(globalThis)) === Object.prototype" to "true",
            "typeof LookupUnit" to "\"undefined\"",
            "typeof globalThis.LookupUnit" to "\"function\"",
            "LookupUnit.SECONDS.name()" to "\"SECONDS\"",
            "LookupUnit === LookupUnit" to "true",
            "['LookupUnit' in globalThis, globalThis.hasOwnProperty('LookupUnit'), Object.getOwnPropertyDescriptor(globalThis, 'LookupUnit') === undefined]" to "[false,false,true]",
            "Object.keys(globalThis).indexOf('LookupUnit')" to "-1",
            "typeof NoSuchLookupName" to "\"undefined\"",
            "(function () { try { NoSuchLookupName; return 'resolved'; } catch (e) { return e.name; } })()" to "\"ReferenceError\"",
            "(function () { try { LookupFail; return 'resolved'; } catch (e) { return e.name + ':' + e.javaException.getMessage(); } })()" to "\"JavaException:lookup\"",
            "(LookupUnit = 1, [LookupUnit, globalThis.hasOwnProperty('LookupUnit')])" to "[1,true]",
        )
    )

    /** An install hook's lazy name maps errors like a member of its module (T5). */
    @Test
    fun installHookNames() = check(
        listOf(
            "assignDynamic('a')" to "\"dynamic:a\"",
            "assignDynamic.name + ':' + assignDynamic.length" to "\"assignDynamic:1\"",
            "(function () { try { assignDynamic(); } catch (e) { return e.name + ':' + e.javaException.getMessage(); } })()" to "\"JavaException:no argument\"",
        )
    )

    /** A proxy module object: no own properties, members read through the handlers, writes kept by the module. */
    @Test
    fun proxyModuleObject() {
        // The current ui / console proxy for comparison: a ProxyObject whose getter is AugmentableProxy.getCore, which
        // binds a function found on the members' prototype chain (hasOwnProperty too) to the members.
        val oldMembers = rhino.context.newObject(rhino.ktScope) as ScriptableObject
        oldMembers.put("ping", oldMembers, "pong")
        global.put("oldProxy", global, ProxyObject(rhino.ktScope, getter = { args ->
            val key = args[0] as String
            val value = ScriptableObject.getProperty(oldMembers, key)
            when {
                value is Callable && !oldMembers.has(key, oldMembers) && ScriptableObject.hasProperty(oldMembers, key) -> BoundFunction(rhino.context, oldMembers, value, oldMembers, emptyArray())
                else -> value
            }
        }))
        check(
            listOf(
                "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'proxyProbe'), (k, v) => k === 'value' ? typeof v : v)" to
                    "\"{\\\"value\\\":\\\"object\\\",\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":true}\"",
                "[Object.keys(proxyProbe).length, Object.getOwnPropertyNames(proxyProbe).length]" to "[0,0]",
                "Object.getPrototypeOf(proxyProbe) === Object.prototype && Object.prototype.toString.call(proxyProbe) === '[object Object]'" to "true",
                "proxyProbe === \$proxyProbe" to "true",
                "[proxyProbe.ping(), proxyProbe.constant, proxyProbe.greet()]" to "[\"pong\",1,\"hello\"]",
                "(function () { var g = proxyProbe.greet; return g(); })()" to "\"hello\"",
                "proxyProbe.builtMark" to "\"marked\"",
                "proxyProbe.probeRef === probe" to "true",
                "['ping' in proxyProbe, proxyProbe.hasOwnProperty('ping')]" to "[false,true]",
                "['ping' in oldProxy, oldProxy.hasOwnProperty('ping'), Object.keys(oldProxy).length]" to "[false,true,0]",
                "typeof proxyProbe.nothing" to "\"undefined\"",
                "(proxyProbe.storedX = 5, [proxyProbe.storedX, Object.keys(proxyProbe).length])" to "[5,0]",
                "(proxyProbe.storedNull = null, proxyProbe.storedNull)" to "null",
                "(function () { try { proxyProbe.failing; return 'read'; } catch (e) { return e.name + ':' + e.javaException.getMessage(); } })()" to "\"JavaException:read\"",
                "Object.keys(proxyProbe.__augmented__).join()" to "\"constant,ping,builtMark,probeRef\"",
            )
        )
        assertTrue(mounter.moduleObject("proxyProbe") is ScriptProxyObject)
    }

    /** Without handlers a proxy reads members and keeps written names on itself. */
    @Test
    fun plainProxyModuleObject() = check(
        listOf(
            "plainProxy.ping()" to "\"plain\"",
            "typeof globalThis['\$plainProxy']" to "\"undefined\"",
            "(plainProxy.extra = 1, [plainProxy.extra, Object.keys(plainProxy).join()])" to "[1,\"extra\"]",
            "JSON.stringify(Object.getOwnPropertyDescriptor(globalThis, 'plainProxy'), (k, v) => k === 'value' ? typeof v : v)" to
                "\"{\\\"value\\\":\\\"object\\\",\\\"writable\\\":true,\\\"enumerable\\\":true,\\\"configurable\\\":false}\"",
        )
    )

    /** Host code reaches installed modules by path: sub-module objects and instances without building. */
    @Test
    fun hostReachesModulesByPath() {
        assertNotNull(mounter.instance("probe"))
        assertEquals(1, ProbeModule.created)
        assertFalse("Creating the instance must not build the module object", mounter.isBuilt("probe"))
        val child = mounter.moduleObject("probe.child")
        assertNotNull(child)
        assertTrue(mounter.isBuilt("probe"))
        assertSame(child, (global.get("probe", global) as Scriptable).get("child", global))
        assertNull(mounter.moduleObject("probe.noSuchChild"))
        assertNull(mounter.moduleObject("noSuchModule"))
        assertNull(mounter.instance("noSuchModule"))
    }

    @Test
    fun registryFindsAssignModulesByName() {
        assertEquals(listOf("assignProbe", "eagerProbe", "numberProbe", "plainProxy", "probe", "proxyProbe", "sample"), TestScriptModules.moduleNames)
        val spec = TestScriptModules.find("assignProbe")?.spec
        assertNotNull(spec)
        assertTrue(spec!!.assign && spec.names.isEmpty() && spec.hasInstall)
        val proxy = TestScriptModules.find("\$proxyProbe")?.spec
        assertTrue(proxy!!.proxy && proxy.hasProxyGet && proxy.hasProxySet && proxy.hasInstall)
    }

}
