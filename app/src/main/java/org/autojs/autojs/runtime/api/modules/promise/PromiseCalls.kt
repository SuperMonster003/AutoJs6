package org.autojs.autojs.runtime.api.modules.promise

import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The script operations of `assets/modules/promise.js` that the global Promise of the new layer repeats (roadmap P4.2):
 * a method call `target.name(...args)` with the TypeError the interpreter gives for that expression (`name is not a
 * function, it is ...`), and `typeof name` of a global name. Neither needs a Rhino top call, as the jobs of the Promise
 * may run outside one.
 *
 * zh-CN: 新层全局 Promise 重复 `assets/modules/promise.js` 的脚本操作 (ROADMAP P4.2): 方法调用 `target.name(...args)`, 出错时为解释器对该表达式给出的
 * TypeError (`name is not a function, it is ...`); 以及全局名称的 `typeof name`. 两者都不需要 Rhino 顶层调用, 因为 Promise 的任务可能在顶层调用之外运行.
 */
internal object PromiseCalls {

    /** `target.name(...args)` */
    fun callProperty(cx: Context, scope: VarScope, target: Any?, name: String, vararg args: Any?): Any? {
        val receiver = RhinoScriptRuntime.toObject(cx, scope, target)
        val function = ScriptableObject.getProperty(receiver, name)
        if (function !is Callable) throw RhinoScriptRuntime.notFunctionError(function, name)
        return function.call(cx, scope, receiver, arrayOf(*args))
    }

    /** `typeof name` for a name of [scope]'s chain, undefined ones included. */
    fun typeofName(cx: Context, scope: VarScope, name: String): String {
        val holder = RhinoScriptRuntime.bind(cx, scope, name) ?: return "undefined"
        return RhinoScriptRuntime.`typeof`(RhinoScriptRuntime.getObjectProp(holder, name, cx, scope))
    }

    /** The value of the global name [name] when it exists, else undefined (for `typeof name === 'function' ? name(...)`). */
    fun nameOrUndefined(cx: Context, scope: VarScope, name: String): Any? {
        val holder = RhinoScriptRuntime.bind(cx, scope, name) ?: return Undefined.instance
        return RhinoScriptRuntime.getObjectProp(holder, name, cx, scope)
    }

}
