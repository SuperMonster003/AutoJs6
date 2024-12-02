package org.autojs.autojs.runtime.api.augment.continuation

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.project.ScriptConfig.Companion.FEATURE_CONTINUATION
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.mozilla.javascript.Scriptable
import java.util.function.Supplier

// FIXME by SuperMonster003 on Jul 11, 2024.
//  ! This augmentable class is not fully suitable for continuation
//  ! as it'll always stuck at Continuation#await.
//  ! Maybe a hint could be found at method org.mozilla.javascript.Context#captureContinuation
//  ! zh-CN:
//  ! 这个扩充类并不完全适用于协程对象 (continuation), 因为 Continuation#await 调用总是会卡住.
//  ! 或许在 org.mozilla.javascript.Context#captureContinuation 可以找到一些线索.
class Continuation(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "enabled" to Supplier { isEnabled(scriptRuntime) }
    )

    override val selfAssignmentFunctions = listOf(
        ::create.name,
        // ::await.name,
        // ::delay.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        fun isEnabled(scriptRuntime: ScriptRuntime) = scriptRuntime.engines.myEngine().hasFeature(FEATURE_CONTINUATION)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun create(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Creator = ensureArgumentsAtMost(args, 1) {
            val (scope) = it
            createRhinoWithRuntime(scriptRuntime, scope)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun createRhinoWithRuntime(scriptRuntime: ScriptRuntime, scope: Any? = null): Creator {
            return Creator(scriptRuntime, scope as? Scriptable)
        }

        // @JvmStatic
        // @RhinoRuntimeFunctionInterface
        // fun await(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) {
        //     val (promise) = it
        //     awaitRhinoWithRuntime(scriptRuntime, promise)
        // }
        //
        // @JvmStatic
        // @RhinoFunctionBody
        // fun awaitRhinoWithRuntime(scriptRuntime: ScriptRuntime, promise: Any?): Any? {
        //     require(promise is ScriptableObject) { "Argument promise for continuation.await must be a ScriptableObject" }
        //     val scope = scriptRuntime.topLevelScope
        //     val cont = Context.javaToJS(createRhinoWithRuntime(scriptRuntime, scope), scope) as Scriptable
        //     val thenFunc = promise.prop("then")
        //     require(thenFunc is BaseFunction) { "Property then of promise for continuation.await must be a JavaScript Function" }
        //     val self = callFunction(thenFunc, scope, promise, arrayOf(resumeFunction(cont)))
        //     require(self is ScriptableObject) { "Promise#then should always return itself as a JavaScript Promise" }
        //     val catchFunc = promise.prop("then")
        //     require(catchFunc is BaseFunction) { "Property catch of promise for continuation.await must be a JavaScript Function" }
        //     callFunction(catchFunc, scope, self, arrayOf(resultErrorFunction(cont)))
        //     val awaitFunc = cont["await"] as BaseFunction
        //     return awaitFunc.call(Context.getCurrentContext() as AutoJsContext, scope, cont, arrayOf())
        // }
        //
        // @JvmStatic
        // @RhinoRuntimeFunctionInterface
        // fun delay(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) {
        //     val (millis) = it
        //     val scope = scriptRuntime.topLevelScope
        //     val cont = Context.javaToJS(createRhinoWithRuntime(scriptRuntime, scope), scope) as Scriptable
        //     scriptRuntime.timers.setTimeout(resumeFunction(cont), coerceLongNumber(millis, 0L))
        //     val awaitFunc = cont["await"] as BaseFunction
        //     awaitFunc.call(Context.getCurrentContext() as AutoJsContext, scope, cont, arrayOf())
        // }
        //
        // private fun resumeFunction(cont: Scriptable) = object : BaseFunction() {
        //     override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Undefined {
        //         val f = cont["resume"] as BaseFunction
        //         return undefined { f.call(cx, scope, thisObj, args) }
        //     }
        // }
        //
        // private fun resultErrorFunction(cont: Scriptable) = object : BaseFunction() {
        //     override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Undefined {
        //         val f = cont["resumeError"] as BaseFunction
        //         return undefined { f.call(cx, scope, thisObj, args) }
        //     }
        // }

    }

}
