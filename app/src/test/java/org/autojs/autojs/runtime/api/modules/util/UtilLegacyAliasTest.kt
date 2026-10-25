package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

class UtilLegacyAliasTest {

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
    fun functionUtilitiesAcceptProxyWrappedFunctions() {
        val proxy = context.evaluateString(
            scope,
            "new Proxy(function() { return this.marker || 7; }, {})",
            "util-callable",
            1,
            null,
        )
        assertTrue(proxy is Callable)
        assertFalse(proxy is BaseFunction)

        val source = RhinoUtils.newNativeObject(scope).apply {
            put("marker", this, "bound")
            put("run", this, proxy)
        }
        val target = RhinoUtils.newNativeObject(scope)
        val names = context.newArray(scope, arrayOf<Any?>("run")) as NativeArray

        Util.__assignFunctions__Rhino(source, target, names)
        val bound = target.get("run", target) as Callable
        assertEquals("bound", bound.call(context, scope, target, emptyArray()))

        val regular = Util.toRegularRhino(proxy)
        assertSame(proxy, regular)
        val result = regular.call(context, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray<Any?>())
        assertEquals(7, (result as Number).toInt())
    }

}
