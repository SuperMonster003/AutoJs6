package org.autojs.autojs.runtime.api.modules

import android.content.Context as AndroidContext
import android.content.ContextWrapper
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.PickRequest
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.console.Console
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.runtime.api.modules.selector.Selector
import org.autojs.autojs.runtime.api.modules.threads.Threads
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
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import sun.misc.Unsafe
import java.lang.reflect.Field

class CallableModuleCompatibilityTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var scriptRuntime: ScriptRuntime
    private lateinit var globalContextField: Field
    private var previousGlobalContext: AndroidContext? = null

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply {
            initStandardObjects(context, false)
        }
        with(RhinoUtils) {
            context.registerStandardObjects(scope)
        }
        scriptRuntime = RhinoTestRuntime.create(scope)
        globalContextField = GlobalAppContext::class.java.getDeclaredField("sApplicationContext").apply {
            isAccessible = true
        }
        previousGlobalContext = globalContextField.get(null) as? AndroidContext
        globalContextField.set(null, allocateWithoutConstructor(TestContext::class.java))
    }

    @After
    fun exitContext() {
        globalContextField.set(null, previousGlobalContext)
        Context.exit()
    }

    @Test
    fun globalWaitAcceptsProxyWrappedConditionAndBranchCallbacks() {
        val condition = evaluate(
            """
            var waitConditionThis;
            new Proxy(function() {
                "use strict";
                waitConditionThis = this;
                return "ready";
            }, {});
            """.trimIndent(),
        ) as Callable
        val callback = evaluate(
            """
            var waitThenThis;
            var waitThenValue;
            ({
                then: new Proxy(function(value) {
                    "use strict";
                    waitThenThis = this;
                    waitThenValue = value;
                    return "wait-complete";
                }, {})
            });
            """.trimIndent(),
        ) as ScriptableObject

        assertFalse(condition is BaseFunction)
        assertEquals(
            "wait-complete",
            Global.wait(scriptRuntime, arrayOf<Any?>(condition, 1, 0, callback)),
        )
        assertTrue(Undefined.isUndefined(scope.get("waitConditionThis", scope)))
        assertTrue(Undefined.isUndefined(scope.get("waitThenThis", scope)))
        assertEquals("ready", scope.get("waitThenValue", scope))
    }

    @Test
    fun selectorPickupAndDetectAcceptProxyWrappedCallbacks() {
        val pickupCallback = evaluate(
            """
            var pickupThis;
            new Proxy(function(value) {
                "use strict";
                pickupThis = this;
                return "pickup-complete";
            }, {});
            """.trimIndent(),
        ) as Callable
        val detectCallback = evaluate(
            """
            var detectThis;
            new Proxy(function(value) {
                "use strict";
                detectThis = this;
                return "detect-complete";
            }, {});
            """.trimIndent(),
        ) as Callable

        assertFalse(pickupCallback is BaseFunction)
        assertFalse(detectCallback is BaseFunction)
        assertEquals(
            "pickup-complete",
            Selector.pickup(
                scriptRuntime,
                arrayOf<Any?>(null, null, null, "selector", pickupCallback),
            ),
        )
        assertEquals(
            "detect-complete",
            Selector.detect(
                scriptRuntime,
                arrayOf<Any?>(null, null, "widget", detectCallback),
            ),
        )
        assertTrue(Undefined.isUndefined(scope.get("pickupThis", scope)))
        assertTrue(Undefined.isUndefined(scope.get("detectThis", scope)))
    }

    @Test
    fun selectorSurfacesExposeOnlyCallableJvmDescriptorsAndAcceptBaseFunctionInstances() {
        val pickupParameterTypes = arrayOf(
            ScriptRuntime::class.java,
            UiObject::class.java,
            Any::class.java,
            CharSequence::class.java,
            Any::class.java,
        )
        UiSelector::class.java.getMethod(
            "pickup",
            *pickupParameterTypes,
            Callable::class.java,
        )
        val detectParameterTypes = arrayOf(
            ScriptRuntime::class.java,
            UiObject::class.java,
            CharSequence::class.java,
            Any::class.java,
        )
        UiObject::class.java.getMethod(
            "detect",
            *detectParameterTypes,
            Callable::class.java,
        )

        assertFalse(
            UiSelector::class.java.methods.any { method ->
                method.name == "pickup" && BaseFunction::class.java in method.parameterTypes
            },
        )
        assertFalse(
            UiObject::class.java.methods.any { method ->
                method.name == "detect" && BaseFunction::class.java in method.parameterTypes
            },
        )
        assertTrue(
            UiSelector.Companion::class.java.declaredMethods.any { method ->
                method.name == "pickup\$default" && Callable::class.java in method.parameterTypes
            },
        )
        assertTrue(
            UiObject.Companion::class.java.declaredMethods.any { method ->
                method.name == "detect\$default" && Callable::class.java in method.parameterTypes
            },
        )
        // The pick machinery is a request of core/automator/pick (roadmap P2.5, docs/selector/ROADMAP.md P1.3); the callback
        // surface stays Callable-only.
        // zh-CN: 拾取机制为 core/automator/pick 的请求 (Roadmap P2.5, selector P1.3); 回调表面仍只接受 Callable.
        val requestClass = PickRequest::class.java
        assertEquals(
            Callable::class.java,
            requestClass.getMethod("getCallback").returnType,
        )
        assertFalse(
            requestClass.declaredConstructors.any { constructor ->
                BaseFunction::class.java in constructor.parameterTypes
            },
        )
        assertTrue(
            requestClass.declaredConstructors.any { constructor ->
                Callable::class.java in constructor.parameterTypes
            },
        )

        val pickupCallback = RhinoUtils.newBaseFunction(
            scope,
            "legacyPickup",
            { "legacy-pickup" },
            RhinoUtils.NOT_CONSTRUCTABLE,
        )
        val detectCallback = RhinoUtils.newBaseFunction(
            scope,
            "legacyDetect",
            { "legacy-detect" },
            RhinoUtils.NOT_CONSTRUCTABLE,
        )
        assertEquals(
            "legacy-pickup",
            UiSelector.pickup(scriptRuntime, null, null, null, "selector", pickupCallback),
        )
        assertEquals(
            "legacy-detect",
            UiObject.detect(scriptRuntime, null, null, "widget", detectCallback),
        )
    }

    @Test
    fun threadsSyncAcceptsProxyWrappedAndPlainCallables() {
        val proxy = evaluate(
            """
            var syncThis;
            var syncValue;
            new Proxy(function(value) {
                "use strict";
                syncThis = this;
                syncValue = value;
                return "sync-complete";
            }, {});
            """.trimIndent(),
        ) as Callable
        val synchronizedProxy = Threads.sync(scriptRuntime, arrayOf(proxy, Any()))

        assertFalse(proxy is BaseFunction)
        assertEquals(
            "sync-complete",
            RhinoUtils.callFunction(
                synchronizedProxy,
                scope,
                scope.globalThis,
                arrayOf<Any?>("proxy-value"),
            ),
        )
        assertSame(scope.globalThis, scope.get("syncThis", scope))
        assertEquals("proxy-value", scope.get("syncValue", scope))

        var callableScope: VarScope? = null
        var callableThis: Any? = null
        val plainCallable = Callable { _, callScope, thisObj, args ->
            callableScope = callScope
            callableThis = thisObj
            args.single()
        }
        val synchronizedCallable = Threads.sync(scriptRuntime, arrayOf(plainCallable))

        assertEquals(
            "plain-value",
            RhinoUtils.callFunction(
                synchronizedCallable,
                scope,
                scope.globalThis,
                arrayOf<Any?>("plain-value"),
            ),
        )
        assertSame(scope, callableScope)
        assertSame(scope.globalThis, callableThis)
    }

    @Test
    fun consoleCallablePathsAcceptProxyWrappedFunctionsAndPreserveThis() {
        val assertion = evaluate(
            """
            var assertionThis;
            new Proxy(function() {
                "use strict";
                assertionThis = this;
                return true;
            }, {});
            """.trimIndent(),
        ) as Callable

        assertFalse(assertion is BaseFunction)
        assertTrue(Console.evaluateAssertionValue(scriptRuntime, assertion))
        assertTrue(Undefined.isUndefined(scope.get("assertionThis", scope)))

        val console = context.newObject(scope)
        val setter = evaluate(
            """
            var setterThis;
            var setterArgs;
            new Proxy(function() {
                "use strict";
                setterThis = this;
                setterArgs = Array.prototype.slice.call(arguments);
            }, {});
            """.trimIndent(),
        ) as Callable
        console.put("setValue", console, setter)
        val values = context.newArray(scope, arrayOf<Any?>("first", "second")) as NativeArray

        Console.invokeConfiguratorFunction(scriptRuntime, console, "setValue", values)

        assertSame(console, scope.get("setterThis", scope))
        val setterArgs = scope.get("setterArgs", scope) as NativeArray
        assertEquals("first", setterArgs.get(0, setterArgs))
        assertEquals("second", setterArgs.get(1, setterArgs))
    }

    private fun evaluate(source: String): Any? {
        return context.evaluateString(scope, source, "callable-module-compatibility", 1, null)
    }

    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        @Suppress("UNCHECKED_CAST")
        return unsafe.allocateInstance(type) as T
    }

    private class TestContext : ContextWrapper(null) {

        override fun getPackageName() = "org.autojs.autojs.test"

    }

}
