package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.rhino.continuation.Continuation
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.call
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.prop
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global `ResultAdapter` (d.ts `Internal.ResultAdapter`), formerly the module `assets/modules/result-adapter.js` that
 * `RhinoJavaScriptEngine.init` defined for every script (roadmap P2.5). Kotlin callers (images, automator, dialogs, UI)
 * reach it as `ScriptRuntime.js_ResultAdapter` and call its `promise` / `wait`.
 *
 * The shape is that of the module: a constructor function (`name` "constructor", `length` 0, as Rhino named the module's
 * anonymous function) whose prototype chain is a plain object holding `promise(promiseAdapter)` and `wait(promise)`, then
 * `Object.prototype` (it does not inherit from `Function.prototype`), and whose `prototype` property (writable, not
 * enumerable, not configurable) is a plain object with `constructor`, `setResult`, `setError`, `callback` and `get`, all
 * enumerable. Like the module, the variant is chosen when the runtime creates the function: on the UI thread an instance
 * suspends a continuation, elsewhere it blocks on a `threads.disposable()`.
 *
 * One behavior differs on purpose: off the UI thread, `get()` returns the result and throws the error (the behavior of
 * Auto.js `result_adapter.js` and of `promise.wait()`), where the module returned the `{ result }` / `{ error }` holder
 * because `ContinuationResult.getOrThrow` only unwraps its own Java type.
 *
 * zh-CN: 全局 `ResultAdapter` (d.ts `Internal.ResultAdapter`), 原为 `RhinoJavaScriptEngine.init` 为每个脚本定义的 `assets/modules/result-adapter.js`
 * (ROADMAP P2.5). Kotlin 侧调用方 (images, automator, dialogs, UI) 经 `ScriptRuntime.js_ResultAdapter` 调用其 promise / wait. 形状同原模块: 构造函数
 * (name 为 "constructor", length 0, 即 Rhino 给原模块匿名函数的名称), 其原型链为含 `promise(promiseAdapter)` 与 `wait(promise)` 的普通对象, 再到
 * Object.prototype (不继承 Function.prototype); prototype 属性 (可写, 不可枚举, 不可配置) 是含 constructor, setResult, setError, callback, get 的普通对象,
 * 成员均可枚举. 与原模块一样在运行时创建该函数时选定变体: UI 线程上实例挂起协程, 其他线程上阻塞于 `threads.disposable()`.
 * 有意的行为差异: 非 UI 线程上 `get()` 返回结果或抛出错误 (Auto.js `result_adapter.js` 与 `promise.wait()` 的行为), 原模块因
 * `ContinuationResult.getOrThrow` 只拆它自己的 Java 类型而返回 `{ result }` / `{ error }` 包装对象.
 */
class ResultAdapterFunction(scope: VarScope, private val global: Scriptable, private val uiThread: Boolean) : BaseFunction(scope, null) {

    init {
        prototype = literal(
            scope,
            "promise" to method(scope, "promise", 1) { cx, s, _, args -> promise(cx, s, args[0]) },
            "wait" to method(scope, "wait", 1) { cx, s, _, args -> wait(cx, s, args[0]) },
        )
        setPrototypeProperty(
            literal(
                scope,
                "constructor" to this,
                "setResult" to method(scope, "setResult", 1) { cx, s, thisObj, args -> callMethod(cx, s, prop(cx, s, thisObj, "impl"), "setResult", args[0]) },
                "setError" to method(scope, "setError", 1) { cx, s, thisObj, args -> callMethod(cx, s, prop(cx, s, thisObj, "impl"), "setError", args[0]) },
                "callback" to method(scope, "callback", 0) { _, s, thisObj, _ -> callback(s, thisObj) },
                "get" to method(scope, "get", 0) { cx, s, thisObj, _ -> get(cx, s, thisObj) },
            )
        )
    }

    override fun getFunctionName() = "constructor"

    override fun getLength() = 0

    override fun getArity() = 0

    /** `new ResultAdapter()` initializes the new object; a plain call initializes `this`, the global object when absent. */
    override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
        val self = thisObj.takeUnless { it == null || Undefined.isUndefined(it) } ?: global
        if (uiThread) {
            // this.cont = continuation.create(); this.impl = { setResult: r => this.cont.resume(r), setError: e => this.cont.resumeError(e), get: () => this.cont.await() };
            val cont = callMethod(cx, scope, name(cx, scope, "continuation"), "create")
            RhinoScriptRuntime.setObjectProp(self, "cont", cont, cx, scope)
            RhinoScriptRuntime.setObjectProp(self, "impl", impl(scope, self, "cont", "resume", "resumeError", "await"), cx, scope)
        } else {
            // this.disposable = runtime.threads.disposable(); this.impl = { setResult: r => this.disposable.setAndNotify({ result: r }), ... };
            val disposable = callMethod(cx, scope, prop(cx, scope, name(cx, scope, "runtime"), "threads"), "disposable")
            RhinoScriptRuntime.setObjectProp(self, "disposable", disposable, cx, scope)
            RhinoScriptRuntime.setObjectProp(self, "impl", impl(scope, self, "disposable", null, null, null), cx, scope)
        }
        return Undefined.instance
    }

    /** The `impl` object of an instance: arrow functions reading `this.cont` / `this.disposable` when called. */
    private fun impl(scope: VarScope, self: Any, holder: String, resume: String?, resumeError: String?, await: String?) = when (resume) {
        null -> literal(
            scope,
            "setResult" to method(scope, "setResult", 1) { cx, s, _, args -> callMethod(cx, s, prop(cx, s, self, holder), "setAndNotify", literal(s, "result" to args[0])) },
            "setError" to method(scope, "setError", 1) { cx, s, _, args -> callMethod(cx, s, prop(cx, s, self, holder), "setAndNotify", literal(s, "error" to args[0])) },
            "get" to method(scope, "get", 0) { cx, s, _, _ -> getOrThrow(cx, s, callMethod(cx, s, prop(cx, s, self, holder), "blockedGet")) },
        )
        else -> literal(
            scope,
            "setResult" to method(scope, "setResult", 1) { cx, s, _, args -> callMethod(cx, s, prop(cx, s, self, holder), resume, args[0]) },
            "setError" to method(scope, "setError", 1) { cx, s, _, args -> callMethod(cx, s, prop(cx, s, self, holder), checkNotNull(resumeError), args[0]) },
            "get" to method(scope, "get", 0) { cx, s, _, _ -> callMethod(cx, s, prop(cx, s, self, holder), checkNotNull(await)) },
        )
    }

    /**
     * ```js
     * callback() {
     *     return function (result, error) {
     *         this.result !== undefined
     *             ? this.result = { result, error }
     *             : error ? this.setError(error) : this.setResult(result);
     *     }.bind(this);
     * }
     * ```
     */
    private fun callback(scope: VarScope, self: Any?) = method(scope, "", 2) { cx, s, _, args ->
        val (result, error) = args
        when {
            !Undefined.isUndefined(prop(cx, s, self, "result")) -> RhinoScriptRuntime.setObjectProp(self, "result", literal(s, "result" to result, "error" to error), cx, s)
            RhinoScriptRuntime.toBoolean(error) -> callMethod(cx, s, self, "setError", error)
            else -> callMethod(cx, s, self, "setResult", result)
        }
        Undefined.instance
    }

    /**
     * ```js
     * get() {
     *     if (this.result) {
     *         return getOrThrow(this.result);
     *     }
     *     this.result = null;
     *     return this.impl.get();
     * }
     * ```
     */
    private fun get(cx: Context, scope: VarScope, self: Any?): Any? {
        val stored = prop(cx, scope, self, "result")
        if (RhinoScriptRuntime.toBoolean(stored)) return getOrThrow(cx, scope, stored)
        RhinoScriptRuntime.setObjectProp(self, "result", null, cx, scope)
        return callMethod(cx, scope, prop(cx, scope, self, "impl"), "get")
    }

    /** The result of a `{ result, error }` holder, throwing its error when truthy; a continuation result as `ContinuationResult.getOrThrow`. */
    private fun getOrThrow(cx: Context, scope: VarScope, holder: Any?): Any? {
        val unwrapped = (holder as? Wrapper)?.unwrap() ?: holder
        if (unwrapped is Continuation.Result) {
            return try {
                Continuation.Result.getOrThrow(unwrapped)
            } catch (e: Throwable) {
                throw Context.throwAsScriptRuntimeEx(e)
            }
        }
        if (holder !is Scriptable || holder is Wrapper) return holder
        val error = prop(cx, scope, holder, "error")
        if (RhinoScriptRuntime.toBoolean(error)) throw JavaScriptException(error)
        return prop(cx, scope, holder, "result")
    }

    /**
     * ```js
     * promise(promiseAdapter) {
     *     return new Promise((resolve, reject) => {
     *         promiseAdapter
     *             .onResolve(result => resolve(result))
     *             .onReject(error => reject(error));
     *     });
     * }
     * ```
     *
     * A [ScriptPromiseAdapter] gets its callbacks directly, as the interface adapters Rhino creates for the arrow
     * functions (a callback runs on the thread that settles the adapter, in a Context of the same factory); any other
     * object gets them through its `onResolve` / `onReject` methods.
     */
    fun promise(cx: Context, scope: VarScope, promiseAdapter: Any?): Any? {
        val executor = method(scope, "", 2) { c, s, _, args ->
            val (resolve, reject) = args
            val onResolve = method(s, "", 1) { c1, s1, _, a -> call(c1, s1, resolve, a[0]) }
            val onReject = method(s, "", 1) { c1, s1, _, a -> call(c1, s1, reject, a[0]) }
            when (val adapter = (promiseAdapter as? Wrapper)?.unwrap() ?: promiseAdapter) {
                is ScriptPromiseAdapter -> adapter.onResolve(callbackOf(onResolve)).onReject(callbackOf(onReject))
                else -> callMethod(c, s, callMethod(c, s, promiseAdapter, "onResolve", onResolve), "onReject", onReject)
            }
            Undefined.instance
        }
        return RhinoScriptRuntime.newObject(name(cx, scope, "Promise"), cx, scope, arrayOf(executor))
    }

    /**
     * ```js
     * wait(promise) {
     *     if (!(promise instanceof Promise)) {
     *         promise = ResultAdapter.promise(promise);
     *     }
     *     return continuation.enabled ? promise.await() : promise.wait();
     * }
     * ```
     */
    fun wait(cx: Context, scope: VarScope, promise: Any?): Any? {
        val actual = when (RhinoScriptRuntime.instanceOf(promise, name(cx, scope, "Promise"), cx)) {
            true -> promise
            else -> callMethod(cx, scope, this, "promise", promise)
        }
        val enabled = RhinoScriptRuntime.toBoolean(prop(cx, scope, name(cx, scope, "continuation"), "enabled"))
        return callMethod(cx, scope, actual, if (enabled) "await" else "wait")
    }

    private fun callbackOf(function: ScriptableObject) = NativeJavaObject.createInterfaceAdapter(ScriptPromiseAdapter.Callback::class.java, function) as ScriptPromiseAdapter.Callback

}
