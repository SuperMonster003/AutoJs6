package org.autojs.autojs.runtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

class ScriptBridgesCompatibilityTest {

    private lateinit var context: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun callAcceptsProxyWrappedFunctions() {
        val callback = context.evaluateString(
            scope,
            "new Proxy(function(value) { captured = [this, value]; }, {})",
            "script-bridges-callable",
            1,
            null,
        ) as Callable

        val globalThis = scope.get("globalThis", scope)
        val bridges: IScriptBridges = ScriptBridges()
        bridges.call(callback, globalThis, arrayOf("ok"))

        val captured = scope.get("captured", scope) as NativeArray
        assertSame(globalThis, captured.get(0, captured))
        assertEquals("ok", captured.get(1, captured))
    }

    @Test
    fun proxyCallsUseTheTargetFunctionDeclarationRealm() {
        val otherScope = context.initStandardObjects()
        val callback = context.evaluateString(
            scope,
            """
            new Proxy(function() {}, {
                apply: function(target, thisArg, args) {
                    capturedArgsPrototype = Object.getPrototypeOf(args);
                }
            })
            """.trimIndent(),
            "script-bridges-proxy-realm",
            1,
            null,
        ) as Callable
        (callback as Scriptable).parentScope = otherScope

        ScriptBridges().call(callback, scope.get("globalThis", scope), arrayOf("ok"))

        assertSame(
            ScriptableObject.getArrayPrototype(scope),
            scope.get("capturedArgsPrototype", scope),
        )
    }

}
