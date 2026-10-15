package org.autojs.autojs.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
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
    fun unscopedNativeObjectUsesCurrentContextRealm() {
        val currentRealm = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
        val value = RhinoUtils.newNativeObject()

        assertSame(currentRealm, RhinoUtils.getDeclarationScope(value))
        assertSame(currentRealm, RhinoUtils.getTopLevelScope(value))
        assertSame(ScriptableObject.getObjectPrototype(currentRealm), RhinoUtils.getObjectPrototype(value))
        assertSame(ScriptableObject.getArrayPrototype(currentRealm), RhinoUtils.getArrayPrototype(value))
        assertSame(ScriptableObject.getFunctionPrototype(currentRealm), RhinoUtils.getFunctionPrototype(value))
    }

    @Test
    fun declarationScopeOrDefaultUsesCurrentContextFallback() {
        val cached = with(RhinoUtils) { cx.getOrCreateStandardObjects() }

        assertSame(cached, with(RhinoUtils) { cx.getDeclarationScopeOrDefault(null) })
        assertSame(RhinoUtils.standardObjects, RhinoUtils.getDeclarationScopeOrDefault(null))
    }

    @Test
    fun newObjectCanUseDetachedScope() {
        val fallback = RhinoUtils.standardObjects
        val source = NativeObject()
        val created = RhinoUtils.newObject(source)

        assertSame(fallback, created.parentScope)
        assertSame(ScriptableObject.getObjectPrototype(fallback), created.prototype)
    }

    @Test
    fun defineFunctionPropertiesCanUseDetachedTarget() {
        val detached = NativeObject()

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
        val fallback = RhinoUtils.standardObjects
        val value = NativeObject()
        value.defineProperty("lines", 91, ScriptableObject.EMPTY)

        val descriptor = RhinoUtils.js_object_getOwnPropertyDescriptor(value, "lines") as ScriptableObject

        assertEquals(91, descriptor.get("value", descriptor))
        assertSame(fallback, descriptor.parentScope)
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
        val fallback = RhinoUtils.standardObjects
        val value = NativeObject()
        val fn = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>): Any {
                return scope === fallback && thisObj === value
            }
        }

        assertSame(true, RhinoUtils.callFunction(fn, value, value, emptyArray()))
    }

    @Test
    fun withScopeResolvesNamesOnTheObjectBeforeItsParentScope() {
        val topLevel: VarScope = cx.initStandardObjects()
        ScriptableObject.putProperty(topLevel, "name", "global")
        ScriptableObject.putProperty(topLevel, "suffix", " (global)")
        val item = cx.newObject(topLevel).apply {
            ScriptableObject.putProperty(this, "name", "小明")
            ScriptableObject.putProperty(this, "age", 18)
        }

        val scope = RhinoUtils.newWithScope(item, topLevel)

        assertSame(topLevel, scope.parentScope)
        assertEquals("小明", evaluate(scope, "name"))
        assertEquals("小明 (global)", evaluate(scope, "name + suffix"))
        assertEquals(19.0, Context.toNumber(evaluate(scope, "age + 1")), 0.0)
        assertEquals("global", evaluate(topLevel, "name"))
    }

    @Test
    fun withScopeDefaultsToTheDeclarationScopeWhichAloneHidesTheObject() {
        val topLevel: VarScope = cx.initStandardObjects()
        ScriptableObject.putProperty(topLevel, "suffix", "!")
        val item = cx.newObject(topLevel).apply {
            ScriptableObject.putProperty(this, "name", "小红")
        }

        // The declaration scope alone cannot see the object's own properties,
        // which is what a list item bound through callFunction(func, Scriptable, ...) ran into.
        // zh-CN: 仅凭声明作用域无法看到对象自身的属性, 这正是通过 callFunction(func, Scriptable, ...) 绑定列表项时遇到的问题.
        assertSame(topLevel, RhinoUtils.getDeclarationScope(item))
        val error = runCatching { evaluate(topLevel, "name") }.exceptionOrNull()
        assertTrue("expected a ReferenceError, got $error", error is EcmaError && error.name == "ReferenceError")

        assertEquals("小红!", evaluate(RhinoUtils.newWithScope(item), "name + suffix"))
    }

    private fun evaluate(scope: VarScope, expression: String): Any? = cx.evaluateString(scope, expression, "<eval>", 1, null)

    @Suppress("unused")
    class TestFunctions {
        companion object {
            @JvmStatic
            fun ping(): String = "pong"
        }
    }
}
