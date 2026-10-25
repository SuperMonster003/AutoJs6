package org.autojs.autojs.runtime.api.modules.timers

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

class SetIntervalExtTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun listenerIsInvokedWithOrdinaryCallThisValue() {
        var actualThis: Any? = null
        val listener = object : BaseFunction() {
            override fun call(
                cx: Context?,
                nt: Any?,
                scope: VarScope?,
                thisObj: Any?,
                args: Array<out Any?>,
            ): Any {
                actualThis = thisObj
                return Undefined.instance
            }
        }

        SetIntervalExt.callListener(listener, cx, scope)

        assertSame(Undefined.SCRIPTABLE_UNDEFINED, actualThis)
    }

    @Test
    fun listenerMayBeAProxyWrappedFunction() {
        val listener = cx.evaluateString(
            scope,
            "new Proxy(function() { captured = this; }, {})",
            "set-interval-ext-listener",
            1,
            null,
        ) as Callable

        SetIntervalExt.callListener(listener, cx, scope)

        assertSame(scope.get("globalThis", scope), scope.get("captured", scope))
    }

}
