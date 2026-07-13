package org.autojs.autojs.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

class RhinoUtilsScopeCompatTest {

    private lateinit var cx: Context

    @Before
    fun enterContext() {
        cx = Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun declarationScopeUsesPrototypeParentWhenObjectIsDetached() {
        val topLevel = cx.initStandardObjects()
        val prototypeWithScope = NativeObject().apply {
            parentScope = topLevel
        }
        val detached = NativeObject().apply {
            prototype = prototypeWithScope
        }

        assertSame(topLevel, RhinoUtils.getDeclarationScope(detached))
        assertSame(topLevel, RhinoUtils.getTopLevelScope(detached))
        assertSame(ScriptableObject.getObjectPrototype(topLevel), RhinoUtils.getObjectPrototype(detached))
    }

    @Test
    fun cachedStandardObjectsUsesAutoJsTopLevelScope() {
        val cached = with(RhinoUtils) { cx.getOrCreateStandardObjects() }

        assertSame(cached, cached.globalThis.parentScope)
        assertSame(RhinoUtils.standardObjects, RhinoUtils.standardObjects.globalThis.parentScope)
    }

    @Test
    fun declarationScopeFallsBackForFullyDetachedNativeObject() {
        val detached = RhinoUtils.newNativeObject()

        assertSame(RhinoUtils.standardObjects, RhinoUtils.getDeclarationScope(detached))
        assertSame(RhinoUtils.standardObjects, RhinoUtils.getTopLevelScope(detached))
        assertSame(RhinoUtils.objectPrototype, RhinoUtils.getObjectPrototype(detached))
        assertSame(RhinoUtils.arrayPrototype, RhinoUtils.getArrayPrototype(detached))
        assertSame(RhinoUtils.functionPrototype, RhinoUtils.getFunctionPrototype(detached))
    }

    @Test
    fun declarationScopeOrDefaultUsesCurrentContextFallback() {
        val cached = with(RhinoUtils) { cx.getOrCreateStandardObjects() }

        assertSame(cached, with(RhinoUtils) { cx.getDeclarationScopeOrDefault(null) })
        assertSame(RhinoUtils.standardObjects, RhinoUtils.getDeclarationScopeOrDefault(null))
    }

    @Test
    fun newObjectCanUseDetachedScope() {
        val detached = RhinoUtils.newNativeObject()
        val created = RhinoUtils.newObject(detached)

        assertSame(RhinoUtils.standardObjects, created.parentScope)
        assertSame(RhinoUtils.objectPrototype, created.prototype)
    }

    @Test
    fun defineFunctionPropertiesCanUseDetachedTarget() {
        val detached = RhinoUtils.newNativeObject()

        RhinoUtils.defineFunctionProperties(
            detached,
            arrayOf("ping"),
            TestFunctions::class.java,
            ScriptableObject.DONTENUM,
        )

        assertTrue(detached.get("ping", detached) is BaseFunction)
    }

    @Test
    fun getOwnPropertyDescriptorCanUseDetachedObject() {
        val detached = RhinoUtils.newNativeObject()
        detached.defineProperty("lines", 91, ScriptableObject.EMPTY)

        val descriptor = RhinoUtils.js_object_getOwnPropertyDescriptor(detached, "lines")

        assertEquals(91, descriptor.get("value", descriptor))
        assertSame(RhinoUtils.standardObjects, descriptor.parentScope)
    }

    @Test
    fun objectHelpersUseDetachedObjectDeclarationScope() {
        val topLevel = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
        val prototypeWithScope = NativeObject().apply {
            parentScope = topLevel
        }
        val detached = NativeObject().apply {
            prototype = prototypeWithScope
            defineProperty("lines", 91, ScriptableObject.EMPTY)
        }

        val keys = RhinoUtils.js_object_keys(detached)
        val values = RhinoUtils.js_object_values(detached)
        val names = RhinoUtils.js_object_getOwnPropertyNames(detached)
        val created = RhinoUtils.js_object_create(detached)

        assertSame(topLevel, keys.parentScope)
        assertSame(topLevel, values.parentScope)
        assertSame(topLevel, names.parentScope)
        assertSame(topLevel, created.parentScope)
        assertSame(detached, created.prototype)
        assertSame(detached, RhinoUtils.js_object_getPrototypeOf(created))
    }

    @Test
    fun callFunctionCanUseDetachedScope() {
        val detached = RhinoUtils.newNativeObject()
        val fn = object : BaseFunction() {
            override fun call(cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                return scope === RhinoUtils.standardObjects && thisObj === detached
            }
        }

        assertSame(true, RhinoUtils.callFunction(fn, detached, detached, emptyArray()))
    }

    @Suppress("unused")
    class TestFunctions {
        companion object {
            @JvmStatic
            fun ping(): String = "pong"
        }
    }
}
