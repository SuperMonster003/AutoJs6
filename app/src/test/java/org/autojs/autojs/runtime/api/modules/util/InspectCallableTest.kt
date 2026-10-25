package org.autojs.autojs.runtime.api.modules.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

class InspectCallableTest {

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
    fun customInspectAcceptsProxyWrappedFunction() {
        val value = context.evaluateString(
            scope,
            "({ inspect: new Proxy(function() { return 'proxy-inspect'; }, {}) })",
            "inspect-callable",
            1,
            null,
        ) as Scriptable
        val inspect = value.get("inspect", value)
        assertTrue(inspect is Callable)
        assertFalse(inspect is BaseFunction)

        val options = context.evaluateString(
            scope,
            "({ customInspect: true })",
            "inspect-options",
            1,
            null,
        )
        assertTrue(Inspect.inspectRhino(value, options).contains("proxy-inspect"))
    }

    @Test
    fun proxyFunctionsAndProxyWrappedToStringHooksAreFormattedAsFunctions() {
        val function = context.evaluateString(
            scope,
            "new Proxy(function proxied() {}, {})",
            "inspect-proxy-function",
            1,
            null,
        )
        assertEquals("[Function proxied()]", Inspect.inspectRhino(function))

        val value = context.evaluateString(
            scope,
            "({ tag: 'proxy-to-string', toString: new Proxy(function() { return this.tag; }, {}) })",
            "inspect-proxy-to-string",
            1,
            null,
        )
        assertEquals("proxy-to-string", Inspect.inspectRhino(value))
    }

}
