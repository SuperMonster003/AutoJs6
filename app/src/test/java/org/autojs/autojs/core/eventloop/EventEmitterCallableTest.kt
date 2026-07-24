package org.autojs.autojs.core.eventloop

import org.autojs.autojs.runtime.ScriptBridges
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

class EventEmitterCallableTest {

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
    fun listenerOperationsAcceptProxyWrappedFunctions() {
        val listener = context.evaluateString(
            scope,
            "var calls = []; new Proxy(function(value) { calls.push(value); }, {})",
            "event-emitter-callable",
            1,
            null,
        )
        assertTrue(listener is Callable)
        assertFalse(listener is BaseFunction)

        val emitter = EventEmitter(ScriptBridges())
        emitter.on("data", listener as Callable)

        assertTrue(emitter.emit("data", "first"))
        assertEquals("first", context.evaluateString(scope, "calls.join(',')", "calls", 1, null))

        emitter.removeListener("data", listener)
        assertFalse(emitter.emit("data", "second"))
        assertEquals("first", context.evaluateString(scope, "calls.join(',')", "calls", 1, null))
    }

    @Test
    fun javaInteropOnResolvesRhinoFunctionsWithoutAmbiguity() {
        val emitter = EventEmitter(ScriptBridges())
        val wrappedEmitter = Context.javaToJS(emitter, scope) as Scriptable
        assertTrue(wrappedEmitter.get("on", wrappedEmitter) is NativeJavaMethod)
        ScriptableObject.putProperty(scope, "emitter", wrappedEmitter)

        context.evaluateString(
            scope,
            "emitter.on('data', function() {})",
            "event-emitter-java-interop",
            1,
            null,
        )

        assertEquals(1, emitter.listenerCount("data"))
    }

}
