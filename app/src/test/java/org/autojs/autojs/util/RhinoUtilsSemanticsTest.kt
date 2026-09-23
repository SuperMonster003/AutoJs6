package org.autojs.autojs.util

import org.autojs.autojs.core.automator.UiObjectCollection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.autojs.autojs.rhino.TopLevelScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

class RhinoUtilsSemanticsTest {

    private lateinit var cx: Context
    private lateinit var scope: TopLevel

    @Before
    fun enterContext() {
        cx = Context.enter()
        scope = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun scopedFunctionUsesItsRealmAndIsNotAConstructor() {
        val fn = RhinoUtils.newBaseFunction(scope, "echo", { args -> args.firstOrNull() }, RhinoUtils.NOT_CONSTRUCTABLE)

        assertSame(scope, fn.parentScope)
        assertSame(ScriptableObject.getFunctionPrototype(scope), fn.prototype)
        assertFalse(fn.isConstructor)
        assertSame(Scriptable.NOT_FOUND, ScriptableObject.getProperty(fn, "prototype"))
        assertEquals("value", fn.call(cx, scope, scope.globalThis, arrayOf("value")))
        assertTrue(Context.toNumber(fn).isNaN())
    }

    @Test
    fun hostFunctionsPreserveTheDifferenceBetweenNullAndUndefined() {
        val returnsNull = RhinoUtils.newBaseFunction(
            scope,
            "returnsNull",
            { null },
            RhinoUtils.NOT_CONSTRUCTABLE,
        )
        val hasNoBody = RhinoUtils.newBaseFunction(
            scope,
            "hasNoBody",
            null as ((Array<out Any?>) -> Any?)?,
            RhinoUtils.NOT_CONSTRUCTABLE,
        )

        assertNull(returnsNull.call(cx, scope, scope.globalThis, emptyArray()))
        assertSame(Undefined.instance, hasNoBody.call(cx, scope, scope.globalThis, emptyArray()))
    }

    @Test
    fun explicitNullIsNotTreatedAsTheDefaultConstructorSentinel() {
        val constructor = RhinoUtils.newBaseFunction(
            scope,
            "NullConstructor",
            { Undefined.instance },
            { null },
        )

        assertRuntimeFails {
            constructor.construct(cx, scope, emptyArray())
        }
    }

    @Test
    fun unscopedFunctionUsesCurrentTopCallRealm() {
        val factory = Callable { _, _, _, _ ->
            RhinoUtils.newBaseFunction("callback", { Undefined.instance }, RhinoUtils.NOT_CONSTRUCTABLE)
        }

        val fn = RhinoScriptRuntime.doTopCall(factory, cx, scope, scope.globalThis, emptyArray(), false) as BaseFunction

        assertSame(scope, fn.parentScope)
        assertSame(ScriptableObject.getFunctionPrototype(scope), fn.prototype)
    }

    @Test
    fun unscopedFactoriesUseTheRegisteredEngineRealmOutsideTopCalls() {
        val engineScope = TopLevelScope().apply {
            initStandardObjects(cx, false)
        }
        with(RhinoUtils) {
            cx.registerStandardObjects(engineScope)
        }

        val fn = RhinoUtils.newBaseFunction("callback", { Undefined.instance }, RhinoUtils.NOT_CONSTRUCTABLE)
        val obj = RhinoUtils.newNativeObject()
        val arr = RhinoUtils.newNativeArray()

        assertSame(engineScope, fn.parentScope)
        assertSame(ScriptableObject.getFunctionPrototype(engineScope), fn.prototype)
        assertSame(engineScope, obj.parentScope)
        assertSame(ScriptableObject.getObjectPrototype(engineScope), obj.prototype)
        assertSame(engineScope, arr.parentScope)
        assertSame(ScriptableObject.getArrayPrototype(engineScope), arr.prototype)
        assertNotSame(engineScope, RhinoUtils.standardObjects)
    }

    @Test
    fun unscopedNativeFactoriesUseCurrentTopCallRealm() {
        val factory = Callable { _, _, _, _ ->
            arrayOf(RhinoUtils.newNativeObject(), RhinoUtils.newNativeArray())
        }

        val (obj, arr) = RhinoScriptRuntime.doTopCall(
            factory,
            cx,
            scope,
            scope.globalThis,
            emptyArray(),
            false,
        ) as Array<*>

        assertSame(scope, (obj as NativeObject).parentScope)
        assertSame(ScriptableObject.getObjectPrototype(scope), obj.prototype)
        assertSame(scope, (arr as NativeArray).parentScope)
        assertSame(ScriptableObject.getArrayPrototype(scope), arr.prototype)
    }

    @Test
    fun moduleReturnValuesAreNormalizedIntoTheCallingRealm() {
        val cyclic = mutableListOf<Any?>("head")
        cyclic.add(cyclic)

        val result = RhinoUtils.toJsValue(
            scope,
            mapOf(
                "unit" to Unit,
                "items" to cyclic,
            ),
        ) as NativeObject
        val items = ScriptableObject.getProperty(result, "items") as NativeArray

        assertSame(scope, result.parentScope)
        assertSame(ScriptableObject.getObjectPrototype(scope), result.prototype)
        assertSame(Undefined.instance, ScriptableObject.getProperty(result, "unit"))
        assertSame(scope, items.parentScope)
        assertSame(ScriptableObject.getArrayPrototype(scope), items.prototype)
        assertSame(items, items.get(1, items))
    }

    @Test
    fun moduleReturnNormalizationPreservesNonArrayCollectionIdentity() {
        val set = linkedSetOf("value")
        val nonStringKeyMap = linkedMapOf<Any, Any>(1 to "value")

        assertSame(set, (RhinoUtils.toJsValue(scope, set) as Wrapper).unwrap())
        assertSame(nonStringKeyMap, (RhinoUtils.toJsValue(scope, nonStringKeyMap) as Wrapper).unwrap())
    }

    @Test
    fun callablePropertiesDoNotHaveToExtendBaseFunction() {
        val target = RhinoUtils.newNativeObject(scope)
        val callable = Callable { _, _, thisObj, args ->
            assertSame(target, thisObj)
            args.single()
        }
        ScriptableObject.putProperty(target, "invoke", callable)

        assertEquals("value", RhinoUtils.callFunction(target, "invoke", arrayOf("value")))
        assertSame(callable, RhinoUtils.coerceCallable(callable))
    }

    @Test
    fun constructorUsesItsPublicPrototypeAndDeclarationScope() {
        val returned = NativeObject()
        val ctor = RhinoUtils.newBaseFunction(
            scope,
            "HostConstructor",
            { "called" },
            { returned },
        )

        assertTrue(ctor.isConstructor)
        assertEquals("called", ctor.call(cx, scope, scope.globalThis, emptyArray()))

        val result = ctor.construct(cx, scope, emptyArray())
        val publicPrototype = ScriptableObject.getProperty(ctor, "prototype") as Scriptable

        assertSame(returned, result)
        assertSame(publicPrototype, result.prototype)
        assertSame(scope, result.parentScope)
        assertTrue(ctor.hasInstance(result))
    }

    @Test
    fun defaultConstructorSentinelCreatesAnObjectInTheRightRealm() {
        val ctor = RhinoUtils.newBaseFunction(
            scope,
            "HostConstructor",
            RhinoUtils.DEFAULT_CALLER,
            { RhinoUtils.DEFAULT_CONSTRUCTOR },
        )

        assertSame(Undefined.instance, ctor.call(cx, scope, scope.globalThis, emptyArray()))

        val result = ctor.construct(cx, scope, emptyArray())
        val publicPrototype = ScriptableObject.getProperty(ctor, "prototype") as Scriptable

        assertSame(publicPrototype, result.prototype)
        assertSame(scope, result.parentScope)
    }

    @Test
    fun defaultConstructorSentinelInvokesTheCallableFallback() {
        val returned = RhinoUtils.newNativeObject(scope)
        val constructor = RhinoUtils.newBaseFunction(
            scope,
            "ColorLikeConstructor",
            { returned },
            { RhinoUtils.DEFAULT_CONSTRUCTOR },
        )

        assertSame(returned, constructor.construct(cx, scope, emptyArray()))
    }

    @Test
    fun flattenPreservesNestedNullValues() {
        val flattened = RhinoUtils.flatten(listOf(1, listOf(null, arrayOf(2, null))))

        assertEquals(listOf(1, null, 2, null), flattened)
    }

    @Test
    fun missingOwnPropertyDescriptorReturnsUndefined() {
        val value = RhinoUtils.newObject(scope)

        assertSame(Undefined.instance, RhinoUtils.js_object_getOwnPropertyDescriptor(value, "missing"))
    }

    @Test
    fun settingTheSamePrototypeOnANonExtensibleObjectIsANoOp() {
        val value = RhinoUtils.newObject(scope)
        val prototype = RhinoUtils.newObject(scope)
        value.prototype = prototype
        value.preventExtensions()

        assertSame(value, RhinoUtils.js_object_setPrototypeOf(value, prototype))
        assertRuntimeFails {
            RhinoUtils.js_object_setPrototypeOf(value, RhinoUtils.newObject(scope))
        }
    }

    @Test
    fun ownPropertyNamesPropagatesOwnKeysErrors() {
        val proxy = cx.evaluateString(
            scope,
            "new Proxy({}, { ownKeys: function() { throw new Error('ownKeys boom'); } })",
            "RhinoUtilsSemanticsTest",
            1,
            null,
        ) as Scriptable

        val thrown = runCatching { RhinoUtils.js_object_getOwnPropertyNames(proxy) }.exceptionOrNull()

        assertNotNull(thrown)
        assertTrue(thrown?.message.orEmpty().contains("ownKeys boom"))
    }

    @Test
    fun bindAcceptsPrimitiveBoundArguments() {
        val target = RhinoUtils.newBaseFunction(scope, "collect", { args -> args.toList() }, RhinoUtils.NOT_CONSTRUCTABLE)
        val bound = RhinoUtils.js_function_bind(scope.globalThis, target, Undefined.instance, "left", 7)

        val result = bound.call(cx, scope, scope.globalThis, arrayOf("right"))

        assertEquals(listOf("left", 7, "right"), result)
    }

    @Test
    fun scriptableHashHelpersUseTheObjectAsReceiver() {
        val receiver = RhinoUtils.newObject(scope)
        val getClassFn = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: org.mozilla.javascript.VarScope?, thisObj: Any?, args: Array<out Any?>): Any {
                return if (thisObj === receiver) UiObjectCollection::class.java else Any::class.java
            }
        }.also { RhinoUtils.initNewBaseFunction(scope, it) }
        val hashCodeFn = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: org.mozilla.javascript.VarScope?, thisObj: Any?, args: Array<out Any?>): Any {
                return if (thisObj === receiver) 73 else -1
            }
        }.also { RhinoUtils.initNewBaseFunction(scope, it) }
        receiver.defineProperty("getClass", getClassFn, ScriptableObject.EMPTY)
        receiver.defineProperty("hashCode", hashCodeFn, ScriptableObject.EMPTY)

        assertEquals(73, RhinoUtils.hashCodeOfScriptable(receiver))
    }

    @Test
    fun scriptableHashHelpersAcceptProxyWrappedMethods() {
        val receiver = RhinoUtils.newObject(scope)
        receiver.defineProperty(
            "expectedClass",
            Context.javaToJS(UiObjectCollection::class.java, scope),
            ScriptableObject.EMPTY,
        )
        receiver.defineProperty(
            "getClass",
            cx.evaluateString(
                scope,
                "new Proxy(function() { return this.expectedClass; }, {})",
                "proxy-get-class",
                1,
                null,
            ),
            ScriptableObject.EMPTY,
        )
        receiver.defineProperty(
            "hashCode",
            cx.evaluateString(
                scope,
                "new Proxy(function() { return 91; }, {})",
                "proxy-hash-code",
                1,
                null,
            ),
            ScriptableObject.EMPTY,
        )

        assertEquals(91, RhinoUtils.hashCodeOfScriptable(receiver))
    }

    @Test
    fun constructAndPrototypeHelpersAcceptProxyWrappedFunctions() {
        val constructorScope = TopLevelScope().apply {
            initStandardObjects(cx, false)
        }
        val constructor = cx.evaluateString(
            constructorScope,
            "new Proxy(function(value) { this.value = value; }, {})",
            "proxy-constructor",
            1,
            null,
        ) as org.mozilla.javascript.Function
        val instance = RhinoUtils.constructFunction(
            constructor,
            constructorScope,
            arrayOf<Any?>("constructed"),
        )
        assertEquals("constructed", instance.get("value", instance))

        val prototype = ScriptableObject.getObjectPrototype(scope)
        val hook = cx.evaluateString(
            scope,
            "new Proxy(function() { return this.marker; }, {})",
            "proxy-prototype-hook",
            1,
            null,
        ) as Callable
        prototype.put("__proxyHook__", prototype, hook)
        val receiver = RhinoUtils.newObject(scope).apply {
            put("marker", this, "prototype-called")
        }

        assertSame(
            hook,
            RhinoUtils.getPrototypeCallable(scope, TopLevel.Builtins.Object, "__proxyHook__"),
        )
        assertEquals(
            "prototype-called",
            RhinoUtils.callPrototypeFunction(
                TopLevel.Builtins.Object,
                "__proxyHook__",
                receiver,
            ),
        )
    }

    @Test
    fun integerCoercionTruncatesTowardZeroAndChecksBounds() {
        assertEquals(12, RhinoUtils.coerceIntNumber(12.9))
        assertEquals(-12, RhinoUtils.coerceIntNumber(-12.9))
        assertEquals(3, RhinoUtils.coerceIntNumber("3.9"))
        assertEquals(-3, RhinoUtils.coerceIntNumber("-3.9"))
        assertEquals(Int.MAX_VALUE, RhinoUtils.coerceIntNumber(Int.MAX_VALUE.toDouble() + 0.9))
        assertEquals(Int.MIN_VALUE, RhinoUtils.coerceIntNumber(Int.MIN_VALUE.toDouble() - 0.9))

        assertCoercionFails { RhinoUtils.coerceIntNumber(Double.NaN) }
        assertCoercionFails { RhinoUtils.coerceIntNumber(Double.POSITIVE_INFINITY) }
        assertCoercionFails { RhinoUtils.coerceIntNumber(Int.MAX_VALUE.toDouble() + 1.0) }
        assertCoercionFails { RhinoUtils.coerceIntNumber(Int.MIN_VALUE.toDouble() - 1.0) }
    }

    @Test
    fun longCoercionTruncatesTowardZeroAndChecksRepresentableBounds() {
        val largestRepresentableLongDouble = Double.fromBits(0x43dfffffffffffffL)

        assertEquals(42L, RhinoUtils.coerceLongNumber(42.9))
        assertEquals(-42L, RhinoUtils.coerceLongNumber(-42.9))
        assertEquals(Long.MAX_VALUE, RhinoUtils.coerceLongNumber(Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE, RhinoUtils.coerceLongNumber(Long.MIN_VALUE))
        assertEquals(9_223_372_036_854_774_784L, RhinoUtils.coerceLongNumber(largestRepresentableLongDouble))

        assertCoercionFails { RhinoUtils.coerceLongNumber(Double.NaN) }
        assertCoercionFails { RhinoUtils.coerceLongNumber(Double.NEGATIVE_INFINITY) }
        assertCoercionFails { RhinoUtils.coerceLongNumber(Long.MAX_VALUE.toDouble()) }
        assertCoercionFails { RhinoUtils.coerceLongNumber(Math.nextDown(Long.MIN_VALUE.toDouble())) }
    }

    private fun assertCoercionFails(block: () -> Unit) {
        try {
            block()
            fail("Expected coercion to fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun assertRuntimeFails(block: () -> Unit) {
        try {
            block()
            fail("Expected operation to fail")
        } catch (_: RuntimeException) {
            // Expected.
        }
    }
}
