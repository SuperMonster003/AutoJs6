package org.autojs.autojs.rhino

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.VarScope

class ProxyObjectCompatibilityTest {

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
    fun proxyAcceptsCallableObjectsAsHooks() {
        val getter = evaluate("new Proxy(function(name) { return 'proxy:' + name; }, {})")
        val setter = evaluate("new Proxy(function(name, value) { captured = name + ':' + value; }, {})")
        val descriptor = NativeObject().apply {
            put(ProxyObject.PROXY_GETTER_KEY, this, getter)
            put(ProxyObject.PROXY_SETTER_KEY, this, setter)
        }
        val proxy = ProxyObject(scope, descriptor)

        assertEquals("proxy:missing", proxy.get("missing", proxy))
        proxy.put("written", proxy, 7)
        assertEquals("written:7", Context.toString(scope.get("captured", scope)))
    }

    private fun evaluate(source: String): Any = context.evaluateString(scope, source, "proxy-hook", 1, null)

}
