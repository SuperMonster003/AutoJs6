package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.call
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * The prototype of the `continuation` module object with `continuation.await(promise)` and `continuation.delay(millis)`,
 * formerly the module `assets/modules/continuation.js` (roadmap P2.5): a plain object whose two methods are enumerable,
 * writable and configurable, have `length` 1 and no `prototype` property, as the module's method definitions.
 *
 * Both call `continuation.create()` and suspend in `await()` of the created continuation, which captures the script
 * frames up to this native call; when the continuation resumes, the resumed value becomes the result of the call, as it
 * became the result of `cont.await()` in the module (`delay` resumes with undefined).
 *
 * zh-CN: `continuation` 模块对象的原型, 含 `continuation.await(promise)` 与 `continuation.delay(millis)`, 原为 `assets/modules/continuation.js`
 * (ROADMAP P2.5): 普通对象, 两个方法可枚举 / 可写 / 可配置, length 为 1, 与原模块的方法定义一样没有 prototype 属性. 两者都调用 `continuation.create()`,
 * 并在所建协程的 `await()` 处挂起, 捕获直到本原生调用为止的脚本帧; 协程恢复时, 恢复值即本调用的结果, 与原模块中 `cont.await()` 的结果相同 (`delay` 以 undefined 恢复).
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
        val resume = method(scope, "", 1) { c, s, _, args -> callMethod(c, s, cont, "resume", args[0]) }
        val resumeError = method(scope, "", 1) { c, s, _, args -> callMethod(c, s, cont, "resumeError", args[0]) }
        callMethod(cx, scope, callMethod(cx, scope, promise, "then", resume), "catch", resumeError)
        return callMethod(cx, scope, cont, "await")
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
