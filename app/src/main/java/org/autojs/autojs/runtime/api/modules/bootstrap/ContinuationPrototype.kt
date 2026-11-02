package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.call
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * The prototype of the `continuation` module object with `continuation.await(promise)` and `continuation.delay(millis)`,
 * formerly the module `assets/modules/continuation.js` (roadmap P2.5): a plain object whose two methods are enumerable,
 * writable and configurable, have `length` 1 and no `prototype` property, as the module's method definitions.
 *
 * Both call `continuation.create()` and suspend in `await()` of the created continuation, which captures the script
 * frames up to this native call; when the continuation resumes, the resumed value becomes the result of the call, as it
 * became the result of `cont.await()` in the module (`delay` resumes with undefined), and a rejection is thrown at the call.
 *
 * zh-CN: `continuation` 模块对象的原型, 含 `continuation.await(promise)` 与 `continuation.delay(millis)`, 原为 `assets/modules/continuation.js`
 * (ROADMAP P2.5): 普通对象, 两个方法可枚举 / 可写 / 可配置, length 为 1, 与原模块的方法定义一样没有 prototype 属性. 两者都调用 `continuation.create()`,
 * 并在所建协程的 `await()` 处挂起, 捕获直到本原生调用为止的脚本帧; 协程恢复时, 恢复值即本调用的结果, 与原模块中 `cont.await()` 的结果相同 (`delay` 以 undefined 恢复), 拒绝则在该调用处抛出.
 */
object ContinuationPrototype {

    fun create(scope: VarScope): NativeObject = literal(
        scope,
        "await" to method(scope, "await", 1) { cx, s, _, args -> await(cx, s, args[0]) },
        "delay" to method(scope, "delay", 1) { cx, s, _, args -> delay(cx, s, args[0]) },
    )

    /**
     * ```js
     * await(promise) {
     *     const cont = continuation.create();
     *     promise
     *         .then(result => cont.resume(result))
     *         .catch(error => cont.resumeError(error));
     *     return cont.await();
     * }
     * ```
     */
    private fun await(cx: Context, scope: VarScope, promise: Any?): Any? {
        val cont = callMethod(cx, scope, name(cx, scope, "continuation"), "create")
        var suspended = true
        val resume = method(scope, "", 1) { c, s, _, args -> inReaction(c, s, suspended) { callMethod(c, s, cont, "resume", args[0]) } }
        val resumeError = method(scope, "", 1) { c, s, _, args -> inReaction(c, s, suspended) { callMethod(c, s, cont, "resumeError", rejection(args[0])) } }
        callMethod(cx, scope, callMethod(cx, scope, promise, "then", resume), "catch", resumeError)
        return try {
            callMethod(cx, scope, cont, "await")
        } catch (e: RuntimeException) {
            if (e !is ContinuationPending) suspended = false
            throw e
        }
    }

    /** `resumeError` takes no nullish error, but `Promise.reject()` has one: as an exception it is thrown as it is. */
    private fun rejection(reason: Any?): Any? = if (reason.isJsNullish()) JavaScriptException(reason) else reason

    /**
     * Runs a resume in a reaction of the awaited Promise. The rest of the script runs inside it, and an error it does not catch
     * belongs to the script, not to the Promise that `then` / `catch` returned: it came to `catch` and resumed the same
     * continuation a second time, or ended as a warning about an unhandled rejection (docs/api-coverage/ROADMAP.md Q205). It is
     * thrown again from the timer, where it is what any uncaught error of a callback is. Without a suspension (`await()` where
     * no coroutine can be captured, which the caller already got as an exception) nothing is left to resume, and a second error
     * would only repeat the first.
     * zh-CN: 在所等待 Promise 的反应里运行恢复. 脚本的剩余部分在其中运行, 它未捕获的错误属于脚本, 而不是 then / catch 返回的 Promise: 原先它会到达 catch 并再次恢复同一协程,
     * 或只成为一条未处理拒绝的警告 (docs/api-coverage/ROADMAP.md Q205). 这里改由 timer 重新抛出, 与回调中任何未捕获的错误相同. 没有挂起时 (await() 在无法捕获协程的位置调用,
     * 调用方已经收到异常) 没有可恢复的内容, 第二个错误只是重复第一个.
     */
    private inline fun inReaction(cx: Context, scope: VarScope, suspended: Boolean, resume: () -> Any?): Any? = when {
        !suspended -> Undefined.instance
        else -> try {
            resume()
        } catch (e: RhinoException) {
            call(cx, scope, name(cx, scope, "setImmediate"), method(scope, "", 0) { _, _, _, _ -> throw e })
        }
    }

    /**
     * ```js
     * delay(millis) {
     *     const cont = continuation.create();
     *     setTimeout(() => cont.resume(), millis);
     *     cont.await();
     * }
     * ```
     */
    private fun delay(cx: Context, scope: VarScope, millis: Any?): Any? {
        val cont = callMethod(cx, scope, name(cx, scope, "continuation"), "create")
        val resume = method(scope, "", 0) { c, s, _, _ -> callMethod(c, s, cont, "resume") }
        call(cx, scope, name(cx, scope, "setTimeout"), resume, millis)
        callMethod(cx, scope, cont, "await")
        return Undefined.instance
    }

}
