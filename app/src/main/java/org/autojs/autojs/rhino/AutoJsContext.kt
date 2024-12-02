package org.autojs.autojs.rhino

import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Scriptable

class AutoJsContext(factory: ContextFactory?) : Context(factory) {

    val continuations = HashSet<Any>()

    var rhinoJavaScriptEngine: RhinoJavaScriptEngine? = null

    override fun captureContinuation(): ContinuationPending {
        val continuationPending = super.captureContinuation()
        continuations.add(continuationPending.continuation)
        return continuationPending
    }

    override fun resumeContinuation(continuation: Any, scope: Scriptable?, functionResult: Any?): Any {
        continuations.remove(continuation)
        return super.resumeContinuation(continuation, scope, functionResult)
    }

    fun hasPendingContinuation(): Boolean {
        return continuations.isNotEmpty()
    }

}