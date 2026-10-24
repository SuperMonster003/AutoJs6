package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.literal
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.prop
import org.autojs.autojs.runtime.api.modules.promise.PromiseCalls.callProperty
import org.autojs.autojs.runtime.api.modules.promise.ScriptPromise.Companion.ADOPTED
import org.autojs.autojs.runtime.api.modules.promise.ScriptPromise.Companion.FULFILLED
import org.autojs.autojs.runtime.api.modules.promise.ScriptPromise.Companion.PENDING
import org.autojs.autojs.runtime.api.modules.promise.ScriptPromise.Companion.REJECTED
import org.autojs.autojs.runtime.api.modules.promise.ScriptPromise.Deferred
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.concurrent.atomic.AtomicInteger
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global `Promise` of a script with the new script module layer (roadmap P4.2, appendix C Q95 route A), in place of
 * `assets/modules/promise.js` (taylorhakes/promise-polyfill as modified for AutoJs6), which the layer switched off keeps
 * until P5.2. Kotlin callers reach it as `ScriptRuntime.js_Promise`. It follows the module step for step, so that both
 * switch states run scripts alike (docs/augment/P4-2-promise-eval.md):
 *
 * - Shape: a function named `Promise` with `length` 1 whose prototype chain is a plain object holding `_immediateFn`,
 *   `_unhandledRejectionFn`, `all`, `allSettled`, `resolve`, `reject` and `race`, then `Object.prototype` (as
 *   `Object.setPrototypeOf(Promise, { ... })` left it, so there is no `call` / `apply` / `bind`); `Promise.prototype`
 *   holds `catch`, `then`, `finally`, `await` and `wait`, enumerable. Every method is a method definition (no `prototype`,
 *   not a constructor) with the module's `length`; methods take a missing `this` as the global object (sloppy mode).
 * - Reactions run as macrotasks through `Promise._immediateFn`, by default the global `setImmediate` (else
 *   `setTimeout(fn, 0)`): on the timer of the settling thread, or the main timer of the script, interleaved with
 *   `setTimeout(fn, 0)` and `setImmediate` in posting order. While neither has been replaced, a [ReactionPoster] posts the
 *   job to that timer directly, in the same order.
 * - An instance of this constructor is adopted without a job (the module's state 3), the `then` of a foreign thenable is
 *   called at once; a rejection without reaction goes to `Promise._unhandledRejectionFn` (`console.warn`) one job later.
 * - `all` / `allSettled` / `race` take anything with a `length`; `Promise.resolve` returns as it is an object whose
 *   `constructor` is this function. Errors have the module's messages.
 * - `await()` and `wait()` are [PromiseSuspension].
 *
 * Instances ([ScriptPromise]) keep their state in Kotlin fields (Q96). What is not an instance (a receiver of
 * `Promise.prototype.then.call(obj)`, `Object.create(Promise.prototype)`, what a replaced `constructor` makes) is taken as
 * the module took an object without `_state` and `_value`: as settled, not fulfilled, with the value undefined; settling it
 * does nothing (the module wrote the fields on it and then failed with a TypeError). Called as a function with an instance
 * as `this` (`Reflect.apply`; `Promise.call` does not exist), the constructor throws the TypeError of a call without `new`
 * instead of initializing that object.
 *
 * zh-CN: 启用新脚本模块层时脚本的全局 `Promise` (ROADMAP P4.2, 附录 C Q95 路线 A), 取代 `assets/modules/promise.js` (taylorhakes/promise-polyfill 的
 * AutoJs6 改版), 新层关闭时仍用该模块直到 P5.2. Kotlin 调用方经 `ScriptRuntime.js_Promise` 取得. 它逐步对应原模块, 使开关两态运行脚本的行为一致
 * (docs/augment/P4-2-promise-eval.md): 形状为名为 Promise, length 为 1 的函数, 原型链是含 _immediateFn / _unhandledRejectionFn / all / allSettled /
 * resolve / reject / race 的普通对象, 再到 Object.prototype (没有 call / apply / bind); Promise.prototype 含可枚举的 catch / then / finally / await / wait;
 * 方法均为方法定义 (没有 prototype, 不是构造器), length 同原模块, this 缺省时取全局对象 (非严格模式). 反应作为宏任务经 `Promise._immediateFn` 运行,
 * 默认为全局 setImmediate (否则 setTimeout(fn, 0)): 在结算线程的 timer 上, 或脚本的主 timer 上, 与 setTimeout(fn, 0) / setImmediate 按投递顺序交错;
 * 两者都未被替换时由 [ReactionPoster] 直接投递到该 timer, 顺序不变. 本构造器的实例不经任务直接采纳 (原模块的状态 3), 外来 thenable 的 then 立即调用;
 * 没有反应的拒绝在一个任务之后交给 `Promise._unhandledRejectionFn` (console.warn). all / allSettled / race 接受任何有 length 的值; Promise.resolve
 * 对 constructor 为本函数的对象原样返回. 错误消息同原模块. await() 与 wait() 见 [PromiseSuspension].
 * 实例 ([ScriptPromise]) 的状态在 Kotlin 字段中 (Q96). 不是实例的对象 (`Promise.prototype.then.call(obj)` 的接收者, `Object.create(Promise.prototype)`,
 * 被替换的 constructor 创建的对象) 按原模块对待没有 _state 与 _value 的对象的方式处理: 视为已结算, 非成功, 值为 undefined; 结算它不做任何事 (原模块
 * 会在其上写字段, 随后因 TypeError 失败). 以实例为 this 把构造器当作函数调用时 (Reflect.apply; Promise.call 不存在) 抛出不带 new 调用的 TypeError,
 * 不再初始化该对象.
 */
class PromiseConstructor(scope: VarScope, private val poster: ReactionPoster? = null) : BaseFunction(scope, null) {

    private val topScope: VarScope = scope

    private val global: Scriptable = ScriptableObject.getTopLevelScope(scope).globalThis

    /** `_.noop`, the executor of the promises that `then` makes with another constructor. */
    private val noop = method(scope, "noop", 0) { _, _, _, _ -> Undefined.instance }

    private val defaultImmediate = method(scope, "_immediateFn", 1) { cx, _, _, args -> immediateByTimers(cx, args[0]); Undefined.instance }

    private val defaultUnhandled = method(scope, "_unhandledRejectionFn", 1) { cx, _, _, args -> warnUnhandled(cx, args[0]); Undefined.instance }

    init {
        prototype = literal(
            scope,
            "_immediateFn" to defaultImmediate,
            "_unhandledRejectionFn" to defaultUnhandled,
            "all" to method(scope, "all", 1) { cx, _, _, args -> all(cx, args[0]) },
            "allSettled" to method(scope, "allSettled", 1) { cx, _, thisObj, args -> allSettled(cx, sloppyThis(thisObj), args[0]) },
            "resolve" to method(scope, "resolve", 1) { cx, _, _, args -> resolveStatic(cx, args[0]) },
            "reject" to method(scope, "reject", 1) { cx, _, _, args -> create(cx) { it.reject(cx, args[0]) } },
            "race" to method(scope, "race", 1) { cx, _, _, args -> race(cx, args[0]) },
        )
        val proto = RhinoUtils.newNativeObject(scope)
        proto.defineProperty("constructor", this, DONTENUM)
        proto.defineProperty("catch", method(scope, "catch", 1) { cx, s, thisObj, args -> callProperty(cx, s, sloppyThis(thisObj), "then", null, args[0]) }, EMPTY)
        proto.defineProperty("then", method(scope, "then", 2) { cx, _, thisObj, args -> then(cx, sloppyThis(thisObj), args[0], args[1]) }, EMPTY)
        proto.defineProperty("finally", method(scope, "finally", 1) { cx, _, thisObj, args -> doFinally(cx, sloppyThis(thisObj), args[0]) }, EMPTY)
        proto.defineProperty("await", method(scope, "await", 0) { cx, s, thisObj, _ -> PromiseSuspension.await(cx, s, sloppyThis(thisObj)) }, EMPTY)
        proto.defineProperty("wait", method(scope, "wait", 0) { cx, s, thisObj, _ -> PromiseSuspension.wait(cx, s, sloppyThis(thisObj)) }, EMPTY)
        setPrototypeProperty(proto)
    }

    override fun getFunctionName() = "Promise"

    override fun getLength() = 1

    override fun getArity() = 1

    override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
        throw RhinoScriptRuntime.typeError("Promises must be constructed via new")
    }

    override fun construct(cx: Context, nt: Any?, scope: VarScope, args: Array<out Any?>): Scriptable {
        val fn = args.getOrNull(0)
        if (!isFunction(fn)) throw RhinoScriptRuntime.typeError("not a function")
        // A subclass (`new.target`) gives the new object its own prototype, as `this` of the module's function had.
        val promise = newInstance(if (nt is Scriptable && nt !== this) ScriptableObject.getProperty(nt, "prototype") else getPrototypeProperty())
        doResolve(cx, promise, fn as Callable, Undefined.SCRIPTABLE_UNDEFINED)
        return promise
    }

    private fun newInstance(proto: Any? = getPrototypeProperty()): ScriptPromise = ScriptPromise().also {
        it.parentScope = topScope
        it.prototype = proto as? Scriptable ?: ScriptableObject.getObjectPrototype(topScope)
    }

    /** `this` of the module's sloppy-mode methods: the global object when null or undefined. */
    private fun sloppyThis(thisObj: Any?): Any? = if (thisObj == null || Undefined.isUndefined(thisObj)) global else thisObj

    // ---- the `_` helpers of the module ----

    /** `_.handle(self, deferred)`: follows adoptions, queues the reaction while pending, else runs it in a job. */
    private fun handle(cx: Context, self: Any?, deferred: Deferred) {
        var target = self
        while (target is ScriptPromise) {
            val promise: ScriptPromise = target
            var state = ADOPTED
            var value: Any?
            synchronized(promise) {
                when (promise.state) {
                    PENDING -> {
                        (promise.deferreds ?: ArrayList<Deferred>(1).also { promise.deferreds = it }).add(deferred)
                        return
                    }
                    ADOPTED -> value = promise.value
                    else -> {
                        promise.handled = true
                        state = promise.state
                        value = promise.value
                    }
                }
            }
            if (state == ADOPTED) {
                target = value
                continue
            }
            immediate(cx, Reaction(deferred, state, value))
            return
        }
        // Not an instance: the module read `_state` and `_value` of it, normally both undefined.
        immediate(cx, Reaction(deferred, NO_STATE, Undefined.instance))
    }

    /** `_.resolve(self, newValue)`, the Promise Resolution Procedure. */
    internal fun resolve(cx: Context, target: Any?, newValue: Any?) {
        val self = target as? ScriptPromise ?: return
        try {
            if (newValue === self) {
                settle(cx, self, REJECTED, newError(cx, "A promise cannot be resolved with itself."))
                return
            }
            if (RhinoScriptRuntime.toBoolean(newValue) && isObjectOrFunction(newValue)) {
                val then = RhinoScriptRuntime.getObjectProp(newValue, "then", cx, topScope)
                if (RhinoScriptRuntime.instanceOf(newValue, this, cx)) {
                    settle(cx, self, ADOPTED, newValue)
                    return
                }
                if (isFunction(then)) {
                    // _.doResolve(_.bind(then, newValue), self)
                    doResolve(cx, self, then as Callable, newValue)
                    return
                }
            }
            settle(cx, self, FULFILLED, newValue)
        } catch (e: Throwable) {
            reject(cx, self, caught(cx, e))
        }
    }

    /** `_.reject(self, newValue)` */
    internal fun reject(cx: Context, target: Any?, newValue: Any?) {
        val self = target as? ScriptPromise ?: return
        settle(cx, self, REJECTED, newValue)
    }

    /** Sets the state, then `_.finale(self)`: the check for an unhandled rejection, or the queued reactions. */
    private fun settle(cx: Context, self: ScriptPromise, state: Int, value: Any?) {
        val waiting: List<Deferred>?
        synchronized(self) {
            self.state = state
            self.value = value
            waiting = self.deferreds
            self.deferreds = null
        }
        if (waiting.isNullOrEmpty()) {
            if (state == REJECTED) immediate(cx, UnhandledCheck(self))
            return
        }
        waiting.forEach { handle(cx, self, it) }
    }

    /**
     * `_.doResolve(fn, self)`: calls [fn] with a resolving pair that settles [self] once; what [fn] throws rejects [self]
     * unless it is settled already, and what a script `catch` would not catch passes through.
     */
    private fun doResolve(cx: Context, self: ScriptPromise, fn: Callable, thisObj: Any?) {
        val resolvers = Resolvers(self)
        try {
            fn.call(cx, topScope, thisObj, arrayOf(resolvers.resolveFunction(), resolvers.rejectFunction()))
        } catch (e: Throwable) {
            resolvers.rejectCaught(cx, e)
        }
    }

    /** `new Promise(function (resolve, reject) { ... })` for an executor of the module itself, as [doResolve] runs it. */
    private inline fun create(cx: Context, executor: (Resolvers) -> Unit): ScriptPromise {
        val promise = newInstance()
        val resolvers = Resolvers(promise)
        try {
            executor(resolvers)
        } catch (e: Throwable) {
            resolvers.rejectCaught(cx, e)
        }
        return promise
    }

    /** `Promise._immediateFn(fn)` */
    private fun immediate(cx: Context, job: PromiseJob) {
        if (ScriptableObject.getProperty(this, "_immediateFn") === defaultImmediate) {
            immediateByTimers(cx, job)
        } else {
            callProperty(cx, topScope, this, "_immediateFn", job.function(topScope))
        }
    }

    /** The default `_immediateFn(fn)`: `typeof setImmediate === 'function' ? setImmediate(fn) : setTimeout(fn, 0)`. */
    private fun immediateByTimers(cx: Context, fn: Any?) {
        val setImmediate = PromiseCalls.nameOrUndefined(cx, topScope, "setImmediate")
        if (isFunction(setImmediate)) {
            if (fn is PromiseJob && poster?.post(setImmediate, fn) == true) return
            (setImmediate as Callable).call(cx, topScope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(functionOf(fn)))
        } else {
            callName(cx, name(cx, topScope, "setTimeout"), "setTimeout", functionOf(fn), RhinoScriptRuntime.zeroObj)
        }
    }

    /** `name(...args)`, a call of a variable [function] named [name]. */
    private fun callName(cx: Context, function: Any?, name: String, vararg args: Any?): Any? {
        if (function !is Callable) throw RhinoScriptRuntime.notFunctionError(function, name)
        return function.call(cx, topScope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(*args))
    }

    private fun functionOf(fn: Any?): Any? = if (fn is PromiseJob) fn.function(topScope) else fn

    /** The default `_unhandledRejectionFn(err)`: `if (typeof console !== 'undefined') console.warn(...)`. */
    private fun warnUnhandled(cx: Context, err: Any?) {
        if (PromiseCalls.typeofName(cx, topScope, "console") == "undefined") return
        callProperty(cx, topScope, name(cx, topScope, "console"), "warn", "Possible Unhandled Promise Rejection:", err)
    }

    /** What a script `catch` block receives for [e] (the `catch` of the module); what it would not catch is rethrown. */
    private fun caught(cx: Context, e: Throwable): Any? {
        if (!isCaughtByScript(cx, e)) throw e
        return if (e is JavaScriptException) e.value else RhinoScriptRuntime.wrapException(e, topScope, cx)
    }

    /** `new TypeError(message)` */
    private fun newError(cx: Context, message: String): Scriptable = cx.newObject(topScope, "TypeError", arrayOf(message))

    // ---- Promise.prototype ----

    /** `then(onFulfilled, onRejected) { let prom = new this.constructor(_.noop); _.handle(this, new _.Handler(...)); return prom; }` */
    private fun then(cx: Context, self: Any?, onFulfilled: Any?, onRejected: Any?): Any? {
        val ctor = prop(cx, topScope, self, "constructor")
        val prom = if (ctor === this) newInstance() else RhinoScriptRuntime.newObject(ctor, cx, topScope, arrayOf(noop))
        handle(cx, self, Deferred(asCallback(onFulfilled), asCallback(onRejected), prom))
        return prom
    }

    /**
     * ```js
     * finally(callback) {
     *     let constructor = this.constructor;
     *     return this.then(
     *         function (value) { return constructor.resolve(callback()).then(function () { return value; }); },
     *         function (reason) { return constructor.resolve(callback()).then(function () { return constructor.reject(reason); }); },
     *     );
     * }
     * ```
     */
    private fun doFinally(cx: Context, self: Any?, callback: Any?): Any? {
        val constructor = prop(cx, topScope, self, "constructor")
        val onFulfilled = method(topScope, "", 1) { c, s, _, args ->
            val value = args[0]
            val result = callProperty(c, s, constructor, "resolve", callCallback(c, callback))
            callProperty(c, s, result, "then", method(s, "", 0) { _, _, _, _ -> value })
        }
        val onRejected = method(topScope, "", 1) { c, s, _, args ->
            val reason = args[0]
            val result = callProperty(c, s, constructor, "resolve", callCallback(c, callback))
            callProperty(c, s, result, "then", method(s, "", 0) { c2, s2, _, _ -> callProperty(c2, s2, constructor, "reject", reason) })
        }
        return callProperty(cx, topScope, self, "then", onFulfilled, onRejected)
    }

    /** `callback()` */
    private fun callCallback(cx: Context, callback: Any?): Any? = callName(cx, callback, "callback")

    // ---- statics ----

    /** `resolve(value)`: an object whose `constructor` is `Promise` as it is, else `new Promise(resolve => resolve(value))`. */
    private fun resolveStatic(cx: Context, value: Any?): Any? {
        if (RhinoScriptRuntime.toBoolean(value) && RhinoScriptRuntime.`typeof`(value) == "object" && prop(cx, topScope, value, "constructor") === this) {
            return value
        }
        return create(cx) { it.resolve(cx, value) }
    }

    /** `Boolean(x && typeof x.length !== 'undefined')` */
    private fun isArrayLike(cx: Context, x: Any?): Boolean {
        return RhinoScriptRuntime.toBoolean(x) && !Undefined.isUndefined(RhinoScriptRuntime.getObjectProp(x, "length", cx, topScope))
    }

    /** `Array.prototype.slice.call(arr)` */
    private fun slice(cx: Context, arr: Any?): Any? {
        val slice = prop(cx, topScope, prop(cx, topScope, name(cx, topScope, "Array"), "prototype"), "slice")
        return callProperty(cx, topScope, slice, "call", arr)
    }

    private fun lengthOf(cx: Context, x: Any?): Int = RhinoScriptRuntime.toInt32(RhinoScriptRuntime.getObjectProp(x, "length", cx, topScope))

    private fun getIndex(cx: Context, x: Any?, i: Int): Any? = RhinoScriptRuntime.getObjectIndex(x, i.toDouble(), cx, topScope)

    private fun setIndex(cx: Context, x: Any?, i: Int, value: Any?) {
        RhinoScriptRuntime.setObjectIndex(x, i.toDouble(), value, cx, topScope)
    }

    private fun all(cx: Context, arr: Any?): Any? = create(cx) { resolvers ->
        if (!isArrayLike(cx, arr)) {
            resolvers.reject(cx, newError(cx, "Promise.all accepts an array"))
            return@create
        }
        val args = slice(cx, arr)
        val length = lengthOf(cx, args)
        val remaining = AtomicInteger(length)
        if (length == 0) {
            resolvers.resolve(cx, cx.newArray(topScope, 0))
            return@create
        }
        fun res(c: Context, i: Int, value: Any?) {
            try {
                if (RhinoScriptRuntime.toBoolean(value) && isObjectOrFunction(value)) {
                    val then = RhinoScriptRuntime.getObjectProp(value, "then", c, topScope)
                    if (isFunction(then)) {
                        val onFulfilled = method(topScope, "", 1) { c2, _, _, a -> res(c2, i, a[0]); Undefined.instance }
                        (then as Callable).call(c, topScope, value, arrayOf(onFulfilled, resolvers.rejectFunction()))
                        return
                    }
                }
                setIndex(c, args, i, value)
                if (remaining.decrementAndGet() == 0) resolvers.resolve(c, args)
            } catch (e: Throwable) {
                resolvers.reject(c, caught(c, e))
            }
        }
        for (i in 0 until length) res(cx, i, getIndex(cx, args, i))
    }

    /** `allSettled(arr)`: `new P(...)` with `P = this`. */
    private fun allSettled(cx: Context, self: Any?, arr: Any?): Any? {
        val body = { c: Context, settlers: Settlers ->
            if (!isArrayLike(c, arr)) {
                settlers.reject(c, newError(c, "${RhinoScriptRuntime.`typeof`(arr)} ${RhinoScriptRuntime.toString(arr)} is not iterable (cannot read property Symbol(Symbol.iterator))"))
            } else {
                val args = slice(c, arr)
                val length = lengthOf(c, args)
                val remaining = AtomicInteger(length)
                if (length == 0) {
                    settlers.resolve(c, c.newArray(topScope, 0))
                } else {
                    fun res(c1: Context, i: Int, value: Any?) {
                        if (RhinoScriptRuntime.toBoolean(value) && isObjectOrFunction(value)) {
                            val then = RhinoScriptRuntime.getObjectProp(value, "then", c1, topScope)
                            if (isFunction(then)) {
                                val onFulfilled = method(topScope, "", 1) { c2, _, _, a -> res(c2, i, a[0]); Undefined.instance }
                                val onRejected = method(topScope, "", 1) { c2, s2, _, a ->
                                    setIndex(c2, args, i, literal(s2, "status" to "rejected", "reason" to a[0]))
                                    if (remaining.decrementAndGet() == 0) settlers.resolve(c2, args)
                                    Undefined.instance
                                }
                                (then as Callable).call(c1, topScope, value, arrayOf(onFulfilled, onRejected))
                                return
                            }
                        }
                        setIndex(c1, args, i, literal(topScope, "status" to "fulfilled", "value" to value))
                        if (remaining.decrementAndGet() == 0) settlers.resolve(c1, args)
                    }
                    for (i in 0 until length) res(c, i, getIndex(c, args, i))
                }
            }
        }
        if (self === this) return create(cx) { body(cx, it) }
        val executor = method(topScope, "", 2) { c, _, _, a ->
            body(c, ForeignSettlers(a[0], a[1]))
            Undefined.instance
        }
        return RhinoScriptRuntime.newObject(self, cx, topScope, arrayOf(executor))
    }

    /** `race(arr)`: `Promise.resolve(arr[i]).then(resolve, reject)` for each index below `arr.length`. */
    private fun race(cx: Context, arr: Any?): Any? = create(cx) { resolvers ->
        if (!isArrayLike(cx, arr)) {
            resolvers.reject(cx, newError(cx, "Promise.race accepts an array"))
            return@create
        }
        val length = RhinoScriptRuntime.toNumber(RhinoScriptRuntime.getObjectProp(arr, "length", cx, topScope))
        var i = 0
        while (i < length) {
            val item = RhinoScriptRuntime.getObjectIndex(arr, i.toDouble(), cx, topScope)
            val promise = callProperty(cx, topScope, this, "resolve", item)
            callProperty(cx, topScope, promise, "then", resolvers.resolveFunction(), resolvers.rejectFunction())
            i++
        }
    }

    // ---- jobs and resolving functions ----

    /** The job of `_.handle`: the callback for the state, or the settlement passed on to the derived promise. */
    private inner class Reaction(private val deferred: Deferred, private val state: Int, private val value: Any?) : PromiseJob() {

        override fun run(cx: Context) {
            val callback = if (state == FULFILLED) deferred.onFulfilled else deferred.onRejected
            if (callback == null) {
                if (state == FULFILLED) resolve(cx, deferred.promise, value) else reject(cx, deferred.promise, value)
                return
            }
            val result = try {
                callback.call(cx, topScope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(value))
            } catch (e: Throwable) {
                reject(cx, deferred.promise, caught(cx, e))
                return
            }
            resolve(cx, deferred.promise, result)
        }

    }

    /** The job of `_.finale` for a rejection without reaction: `if (!self._handled) Promise._unhandledRejectionFn(self._value)`. */
    private inner class UnhandledCheck(private val promise: ScriptPromise) : PromiseJob() {

        override fun run(cx: Context) {
            val value = synchronized(promise) { if (promise.handled) return else promise.value }
            if (ScriptableObject.getProperty(this@PromiseConstructor, "_unhandledRejectionFn") === defaultUnhandled) {
                warnUnhandled(cx, value)
            } else {
                callProperty(cx, topScope, this@PromiseConstructor, "_unhandledRejectionFn", value)
            }
        }

    }

    /** The `resolve` / `reject` an executor receives. */
    private interface Settlers {
        fun resolve(cx: Context, value: Any?)
        fun reject(cx: Context, reason: Any?)
    }

    /** The resolving functions of `_.doResolve`: the first call settles, later ones do nothing. */
    private inner class Resolvers(private val promise: ScriptPromise) : Settlers {

        private var done = false
        private var resolveFunction: LambdaFunction? = null
        private var rejectFunction: LambdaFunction? = null

        private fun take(): Boolean = synchronized(this) {
            if (done) false else true.also { done = true }
        }

        override fun resolve(cx: Context, value: Any?) {
            if (take()) this@PromiseConstructor.resolve(cx, promise, value)
        }

        override fun reject(cx: Context, reason: Any?) {
            if (take()) this@PromiseConstructor.reject(cx, promise, reason)
        }

        /** `catch (ex) { if (done) return; done = true; _.reject(self, ex); }` of the executor call. */
        fun rejectCaught(cx: Context, e: Throwable) {
            if (!isCaughtByScript(cx, e)) throw e
            if (take()) this@PromiseConstructor.reject(cx, promise, caught(cx, e))
        }

        fun resolveFunction(): LambdaFunction = resolveFunction ?: method(topScope, "", 1) { c, _, _, args ->
            resolve(c, args[0])
            Undefined.instance
        }.also { resolveFunction = it }

        fun rejectFunction(): LambdaFunction = rejectFunction ?: method(topScope, "", 1) { c, _, _, args ->
            reject(c, args[0])
            Undefined.instance
        }.also { rejectFunction = it }

    }

    /** The resolving functions that another constructor passed to an executor of the module (`allSettled` on a subclass). */
    private inner class ForeignSettlers(private val resolve: Any?, private val reject: Any?) : Settlers {

        override fun resolve(cx: Context, value: Any?) {
            callName(cx, resolve, "resolve", value)
        }

        override fun reject(cx: Context, reason: Any?) {
            callName(cx, reject, "reject", reason)
        }

    }

    private companion object {

        /** The state of what is not an instance: neither fulfilled nor pending. */
        const val NO_STATE = -1

        fun isFunction(x: Any?): Boolean = x is Callable && RhinoScriptRuntime.`typeof`(x) == "function"

        /** `typeof f === 'function' ? f : null` of the module's `Handler`. */
        fun asCallback(f: Any?): Callable? = if (isFunction(f)) f as Callable else null

        /** `typeof x === 'object' || typeof x === 'function'` */
        fun isObjectOrFunction(x: Any?): Boolean = when (RhinoScriptRuntime.`typeof`(x)) {
            "object", "function" -> true
            else -> false
        }

        /** What a script `catch` block catches (`Interpreter`): the others only run `finally` blocks or pass through. */
        fun isCaughtByScript(cx: Context, t: Throwable): Boolean = when (t) {
            is JavaScriptException, is EcmaError, is EvaluatorException -> true
            is ContinuationPending -> false
            else -> cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)
        }

    }

}
