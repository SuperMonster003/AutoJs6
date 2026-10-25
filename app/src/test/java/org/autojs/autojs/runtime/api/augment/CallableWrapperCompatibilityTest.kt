package org.autojs.autojs.runtime.api.augment

import okio.Buffer
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.automator.RootAutomatorNativeObject
import org.autojs.autojs.runtime.api.modules.http.RequestBuilderHelper
import org.autojs.autojs.runtime.api.modules.http.ResponseBodyNativeObject
import org.autojs.autojs.runtime.api.modules.zip.ZipNativeObject
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import java.io.File

class CallableWrapperCompatibilityTest {

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
    fun requestBodyWriterAcceptsProxyFunctionAndPreservesReceiver() {
        val writer = evaluate(
            "new Proxy(function(sink) { capturedWriterThis = this; capturedSink = sink; }, {})",
        )
        val sink = Buffer()

        RequestBuilderHelper.callBodyWriter(null, scope, writer, sink)

        assertSame(writer, scope.get("capturedWriterThis", scope))
        val capturedSink = scope.get("capturedSink", scope)
        assertSame(sink, (capturedSink as? Wrapper)?.unwrap() ?: capturedSink)
    }

    @Test
    fun delegatedMethodsBindProxyFunctionsToTheirJavaOwner() {
        val delegated = evaluate("new Proxy(function() { return this; }, {})")
        val owner = newNativeObject(scope)

        lateinit var rootBound: Callable
        lateinit var responseBound: Callable
        val createBindings = RhinoUtils.newBaseFunction(scope, "createBindings", { _ ->
            rootBound = RootAutomatorNativeObject.bindCallable(context, owner, delegated)
            responseBound = ResponseBodyNativeObject.bindCallable(context, owner, delegated)
            Undefined.instance
        }, RhinoUtils.NOT_CONSTRUCTABLE)
        RhinoUtils.callFunction(createBindings, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray())

        assertSame(owner, RhinoUtils.callFunction(rootBound, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray()))
        assertSame(owner, RhinoUtils.callFunction(responseBound, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray()))
    }

    @Test
    fun automatorAndZipRecognizeProxyFunctionsAsCallbacks() {
        val callback = evaluate(
            "new Proxy(function() { capturedFilterThis = this; return true; }, {})",
        )
        val callbackObject = newNativeObject(scope).apply {
            put("onCompleted", this, callback)
        }
        val filterOwner = newNativeObject(scope)
        val filter = ZipNativeObject.toExcludeFileFilter(callback, filterOwner)

        assertTrue(Automator.isGestureResultCallbackLike(callback))
        assertTrue(Automator.isGestureResultCallbackLike(callbackObject))
        assertTrue(filter.isExcluded(File("skip.txt")))
        assertSame(filterOwner, scope.get("capturedFilterThis", scope))
    }

    private fun evaluate(source: String): Callable {
        val result = context.evaluateString(scope, source, "callable-wrapper", 1, null)
        assertTrue(result is Callable)
        assertFalse(result is BaseFunction)
        return result as Callable
    }

}
