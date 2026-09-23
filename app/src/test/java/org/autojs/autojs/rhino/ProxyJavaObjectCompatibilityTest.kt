package org.autojs.autojs.rhino

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

class ProxyJavaObjectCompatibilityTest {

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
    fun putWithoutProxyWritesTheWrappedJavaObject() {
        val bean = WritableBean()
        val proxy = ProxyJavaObject(scope, bean, WritableBean::class.java)

        proxy.putWithoutProxy("value", proxy, "updated")

        assertEquals("updated", bean.value)
    }

    @Test
    fun proxyPreservesNullJavaMembers() {
        val bean = WritableBean(value = null)
        val proxy = ProxyJavaObject(scope, bean, WritableBean::class.java)

        assertNull(proxy.get("value", proxy))
    }

    @Test
    fun proxyAcceptsNullAssignments() {
        val bean = WritableBean()
        val proxy = ProxyJavaObject(scope, bean, WritableBean::class.java)

        proxy.put("value", proxy, null)

        assertNull(bean.value)
    }

    @Test
    fun proxyGetterMayReturnNull() {
        val getter = object : BaseFunction() {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = null
        }
        val proxyDescriptor = NativeObject().apply {
            put(ProxyObject.PROXY_GETTER_KEY, this, getter)
        }
        val proxy = ProxyJavaObject(scope, WritableBean(), WritableBean::class.java, proxyDescriptor)

        assertNull(proxy.get("missing", proxy))
    }

    @Test
    fun proxyAcceptsCallableObjectsAsHooks() {
        val getter = context.evaluateString(
            scope,
            "new Proxy(function(name) { return 'proxy:' + name; }, {})",
            "proxy-hook",
            1,
            null,
        )
        val proxyDescriptor = NativeObject().apply {
            put(ProxyObject.PROXY_GETTER_KEY, this, getter)
        }
        val proxy = ProxyJavaObject(scope, WritableBean(), WritableBean::class.java, proxyDescriptor)

        assertEquals("proxy:missing", Context.toString(proxy.get("missing", proxy)))
    }

    class WritableBean(
        var value: String? = "initial",
    )

}
