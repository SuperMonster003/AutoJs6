package org.autojs.autojs.util

import org.junit.Assert.assertSame
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

class RhinoInternalBaseFunctionCompatibilityTest {

    @Test
    fun defaultCallHelpersDoNotRedispatchToTheOverride() {
        Context.enter().use { cx ->
            val scope = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
            for (useFlags in listOf(false, true)) {
                val function = defaultFunction(scope, useFlags)
                assertSame(Undefined.instance, function.call(cx, scope, scope.globalThis, emptyArray()))
            }
        }
    }

    @Test
    fun defaultConstructorHelpersDoNotRedispatchToTheOverride() {
        Context.enter().use { cx ->
            val scope = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
            for (useFlags in listOf(false, true)) {
                val function = defaultFunction(scope, useFlags)
                val instance = function.construct(cx, scope, emptyArray())
                assertSame(scope, instance.parentScope)
                assertSame(function.get("prototype", function), instance.prototype)
            }
        }
    }

    private fun defaultFunction(scope: VarScope, useFlags: Boolean) =
        object : RhinoUtils.InternalBaseFunction("LegacyDefault") {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? =
                if (useFlags) callFlag(RhinoUtils.DEFAULT_CALLER, cx, scope, thisObj as Scriptable?, args)
                else callFunc(null, cx, scope, thisObj as Scriptable?, args)

            override fun construct(cx: Context, nt: Any?, scope: VarScope, args: Array<out Any?>): Scriptable =
                if (useFlags) constructFlag(RhinoUtils.DEFAULT_CONSTRUCTOR, cx, scope, args)
                else constructFunc(null, cx, scope, args)
        }.also {
            it.parentScope = scope
            it.setImmunePrototypeProperty(NativeObject().apply { parentScope = scope })
        }
}
