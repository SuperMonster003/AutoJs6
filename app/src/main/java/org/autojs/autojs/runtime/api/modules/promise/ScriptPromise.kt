package org.autojs.autojs.runtime.api.modules.promise

import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined

/**
 * An instance of the global `Promise` of [PromiseConstructor] (roadmap P4.2, appendix C Q95 route A). The state that
 * `assets/modules/promise.js` kept in the own enumerable properties `_state`, `_handled`, `_value` and `_deferreds` of
 * each instance is in Kotlin fields instead (Q96), so an instance has no own property until a script adds one; its class
 * name stays "Object" (`Object.prototype.toString.call(p)` is `[object Object]`). It is a [NativeObject] as the module's
 * instances were: Kotlin callers of the old augment package take a Promise as one (`RhinoUtils.handleAsyncOperation` for
 * `images.loadAsync` and the `http` `Async` functions, `images.requestScreenCaptureAsync`), which threw a
 * ClassCastException with the switch on (P5.1). The fields are guarded by the instance: a Promise may be settled on
 * another thread (a binder thread, a thread of `threads`) while the script adds reactions.
 *
 * zh-CN: [PromiseConstructor] 的全局 `Promise` 的实例 (ROADMAP P4.2, 附录 C Q95 路线 A). `assets/modules/promise.js` 放在每个实例的可枚举自有属性
 * `_state` / `_handled` / `_value` / `_deferreds` 中的状态改为 Kotlin 字段 (Q96), 因此脚本添加属性之前实例没有自有属性; 类名仍为 "Object"
 * (`Object.prototype.toString.call(p)` 为 `[object Object]`). 与原模块的实例一样是 [NativeObject]: 旧 augment 包的 Kotlin 调用方把 Promise 当作
 * NativeObject (images.loadAsync 与 http 的 Async 函数所用的 RhinoUtils.handleAsyncOperation, images.requestScreenCaptureAsync), 开关开启时曾抛
 * ClassCastException (P5.1). 字段由实例本身加锁保护: Promise 可能在其他线程 (binder 线程, threads 的线程) 上结算,
 * 同时脚本在添加反应.
 */
class ScriptPromise internal constructor() : NativeObject() {

    /** [PENDING], [FULFILLED], [REJECTED] or [ADOPTED] (then [value] is the adopted Promise), as `_state` was. */
    internal var state = PENDING

    /** Whether a reaction was added after the settlement, as `_handled`: a rejection without one is reported. */
    internal var handled = false

    internal var value: Any? = Undefined.instance

    /** The reactions added while pending, as `_deferreds` (null while there is none, and once settled). */
    internal var deferreds: ArrayList<Deferred>? = null

    /** A reaction, the module's `Handler`: the callbacks that are functions, and the Promise that `then` returned. */
    internal class Deferred(@JvmField val onFulfilled: Callable?, @JvmField val onRejected: Callable?, @JvmField val promise: Any?)

    internal companion object {
        const val PENDING = 0
        const val FULFILLED = 1
        const val REJECTED = 2
        const val ADOPTED = 3
    }

}
