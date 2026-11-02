package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.prop
import org.mozilla.javascript.Context
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The two non-standard methods of the AutoJs6 Promise, `await()` and `wait()`, for any thenable receiver. They are the host's
 * extension of the built-in Promise of Rhino (roadmap P9.5 of Rhino-For-AutoJs6, ROADMAP.md T12), written in the words of
 * `assets/modules/promise.js` (docs/augment/ROADMAP.md P4.2). [await] is `continuation.await(this)`: the continuation module's
 * `await` (ContinuationPrototype) suspends the coroutine of the script frame that called it, so Kotlin code may call it
 * as well, which the module's `await` did not allow (its own script frame was a new top frame; Flow `await()`, appendix
 * C Q97). [wait] waits on `runtime.loopers.newSettlementLatch()`, running the microtasks and the due callbacks of its own
 * timer meanwhile (Q26).
 *
 * zh-CN: AutoJs6 Promise 的两个非标准方法 `await()` 与 `wait()`, 接收者可以是任意 thenable. 它们是宿主对 Rhino 内建 Promise 的扩展 (Rhino-For-AutoJs6 ROADMAP P9.5, T12),
 * 写法同 `assets/modules/promise.js` (docs/augment/ROADMAP.md P4.2). [await] 即 `continuation.await(this)`: continuation 模块的 await (ContinuationPrototype)
 * 挂起调用它的脚本帧所在的协程, 因此 Kotlin 代码也能调用它, 而原模块的 await 不行 (它自己的脚本帧成为新的顶层帧; Flow 的 `await()`, 附录 C Q97).
 * [wait] 在 `runtime.loopers.newSettlementLatch()` 上等待, 期间运行本线程的微任务与 timer 上已到期的回调 (Q26).
 */
internal object PromiseSuspension {

    /** Adds both methods to `Promise.prototype` of [scope], not enumerable as those of a class. */
    fun install(scope: TopLevelScope) {
        val prototype = TopLevel.getBuiltinPrototype(scope, TopLevel.Builtins.Promise) as ScriptableObject
        prototype.defineProperty("await", method(scope, "await", 0) { cx, s, thisObj, _ -> await(cx, s, thisObj) }, ScriptableObject.DONTENUM)
        prototype.defineProperty("wait", method(scope, "wait", 0) { cx, s, thisObj, _ -> wait(cx, s, thisObj) }, ScriptableObject.DONTENUM)
    }

    /** `await() { return continuation.await(this); }` */
    fun await(cx: Context, scope: VarScope, promise: Any?): Any? = callMethod(cx, scope, name(cx, scope, "continuation"), "await", promise)

    /**
     * ```js
     * wait() {
     *     let latch = runtime.loopers.newSettlementLatch();
     *     this.then(result => latch.settle({ result: result })).catch(error => latch.settle({ error: error }));
     *     let resultObj = latch.await();
     *     if (resultObj.error) throw resultObj.error;
     *     return resultObj.result;
     * }
     * ```
     * The error is thrown at the script line that called `wait()` (the module's `throw` was at its own line).
     */
    fun wait(cx: Context, scope: VarScope, promise: Any?): Any? {
        val latch = callMethod(cx, scope, prop(cx, scope, name(cx, scope, "runtime"), "loopers"), "newSettlementLatch")
        val onResult = method(scope, "", 1) { c, s, _, args -> callMethod(c, s, latch, "settle", literal(s, "result" to args[0])) }
        val onError = method(scope, "", 1) { c, s, _, args -> callMethod(c, s, latch, "settle", literal(s, "error" to args[0])) }
        callMethod(cx, scope, callMethod(cx, scope, promise, "then", onResult), "catch", onError)
        val resultObj = callMethod(cx, scope, latch, "await")
        val error = prop(cx, scope, resultObj, "error")
        if (RhinoScriptRuntime.toBoolean(error)) {
            val line = IntArray(1)
            val source = Context.getSourcePositionFromStack(line)
            throw JavaScriptException(error, source, line[0])
        }
        return prop(cx, scope, resultObj, "result")
    }

}
