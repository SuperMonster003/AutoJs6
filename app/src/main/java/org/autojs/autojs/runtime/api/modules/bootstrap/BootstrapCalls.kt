package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Script-level operations shared by the bootstrap objects that replace the startup JavaScript modules (roadmap P2.5).
 * They do what the replaced JavaScript did in the same words: a global name is resolved through the scope chain when the
 * function runs (a missing one is a ReferenceError), a method is looked up on its receiver and called with it (a missing
 * one is a TypeError), so a script that replaces `continuation.create` or `setTimeout` sees the same effect as before.
 *
 * zh-CN: 替代启动必载 JS 模块的 Kotlin 对象共用的脚本级操作 (ROADMAP P2.5). 与原 JS 的写法一一对应: 全局名称在函数运行时沿作用域链解析 (不存在则 ReferenceError),
 * 方法在接收者上查找并以它为 this 调用 (不存在则 TypeError), 因此脚本替换 continuation.create 或 setTimeout 后的效果与原来相同.
 */
internal object BootstrapCalls {

    /** The value of the global name [name], as an identifier in the replaced module code. */
    fun name(cx: Context, scope: VarScope, name: String): Any? = RhinoScriptRuntime.name(cx, scope, name)

    /** `target.name` */
    fun prop(cx: Context, scope: VarScope, target: Any?, name: String): Any? = RhinoScriptRuntime.getObjectProp(target, name, cx, scope)

    /** `target.name(...args)` */
    fun callMethod(cx: Context, scope: VarScope, target: Any?, name: String, vararg args: Any?): Any? {
        val receiver = RhinoScriptRuntime.toObject(cx, scope, target)
        val function = ScriptableObject.getProperty(receiver, name)
        if (function !is Callable) throw RhinoScriptRuntime.notFunctionError(receiver, function, name)
        return function.call(cx, scope, receiver, arrayOf(*args))
    }

    /** `f(...args)` for a plain call expression: `this` is undefined. */
    fun call(cx: Context, scope: VarScope, function: Any?, vararg args: Any?): Any? {
        if (function !is Callable) throw RhinoScriptRuntime.notFunctionError(function)
        return function.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(*args))
    }

    /** A plain object with [entries] as enumerable, writable and configurable properties in order, as an object literal. */
    fun literal(scope: VarScope, vararg entries: Pair<String, Any?>): NativeObject = RhinoUtils.newNativeObject(scope).also { obj ->
        entries.forEach { (key, value) -> obj.defineProperty(key, value, ScriptableObject.EMPTY) }
    }

    /**
     * A function without `prototype` property, as a method definition or an arrow function in the replaced module code;
     * [body] receives the context, the declaration scope, `this` and the arguments (missing ones are undefined).
     */
    fun method(scope: VarScope, name: String, length: Int, body: (cx: Context, scope: VarScope, thisObj: Any?, args: Array<out Any?>) -> Any?): LambdaFunction {
        return LambdaFunction(scope, name, length, SerializableCallable { cx, s, thisObj, args ->
            val padded = if (args.size >= length) args else Array(length) { args.getOrElse(it) { Undefined.instance } }
            body(cx, s, thisObj, padded)
        }, false)
    }

}
