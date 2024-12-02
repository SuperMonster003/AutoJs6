package org.autojs.autojs.runtime.api.augment.continuation

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.continuation.Continuation
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.mozilla.javascript.Scriptable

class Creator(scriptRuntime: ScriptRuntime, scope: Scriptable? = null) {

    private val mContinuation = scriptRuntime.createContinuation(scope ?: scriptRuntime.topLevelScope)

    fun await(): Any? {
        /**
         * @Caution by SuperMonster003 on Apr 19, 2022.
         * Continuation without "continuation feature" will cause an exception
         * which makes all invocations failed and interrupted here.
         * zh-CN: 在没有 "协程特性" 的情况使用协程会造成异常, 使所有调用失败并于此处被中断.
         *
         * @example Exception snippet
         * Wrapped java.lang.IllegalStateException:
         * Cannot capture continuation from JavaScript code not called directly
         * by executeScriptWithContinuations or callFunctionWithContinuations
         *
         * @example Code for reappearance
         * engines.myEngine().hasFeature('continuation'); // false
         * Object.create(runtime.createContinuation()).suspend(); // throw error
         */
        val result = mContinuation.suspend()

        return Continuation.Result.handle(result)
    }

    fun resumeError(error: Any?) {
        require(!error.isJsNullish()) { "Argument error for continuation.resumeError must be non-nullish" }
        mContinuation.resumeWith(Continuation.Result.failure(error))
    }

    @JvmOverloads
    fun resume(result: Any? = UNDEFINED) {
        mContinuation.resumeWith(Continuation.Result.success(result))
    }

}