package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.call
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.callMethod
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.name
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.prop
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global functions `__exitIfError__` and `__asGlobal__` (declared in d.ts `aj6-int-init.d.ts`), formerly the module
 * `assets/modules/internal.js` that `ScriptRuntime.initPrologue` assigned to the global object for every script (roadmap
 * P2.5). Names, `length`, attributes (enumerable, writable, configurable, in this order after the modules) and behavior
 * are those of the module; like a method definition, neither function has a `prototype` property.
 *
 * Unlike the interpreted function it replaces, `__exitIfError__` is a native call, so a continuation (`continuation.await`,
 * `promise.await()`) cannot be captured inside its action, as for every callback that a Kotlin module calls.
 *
 * zh-CN: 全局函数 `__exitIfError__` 与 `__asGlobal__` (d.ts `aj6-int-init.d.ts` 有声明), 原为 `assets/modules/internal.js`, 由
 * `ScriptRuntime.initPrologue` 为每个脚本合入全局对象 (ROADMAP P2.5). 名称, length, 特性 (可枚举 / 可写 / 可配置, 紧跟模块之后按此顺序) 与行为同原模块;
 * 与方法定义一样, 两个函数都没有 prototype 属性. 与被替代的解释执行函数不同, `__exitIfError__` 是原生调用, 其 action 内无法捕获协程
 * (continuation.await, promise.await()), 与 Kotlin 模块调用的所有回调相同.
 */
object InternalGlobals {

    const val EXIT_IF_ERROR = "__exitIfError__"
    const val AS_GLOBAL = "__asGlobal__"

    /** Defines both functions on [global] as the module's `Object.assign` did. */
    fun install(scope: VarScope, global: ScriptableObject) {
        global.defineProperty(EXIT_IF_ERROR, method(scope, EXIT_IF_ERROR, 1) { cx, s, _, args -> exitIfError(cx, s, args[0]) }, ScriptableObject.EMPTY)
        global.defineProperty(AS_GLOBAL, method(scope, AS_GLOBAL, 3) { cx, s, _, args -> asGlobal(cx, s, args[0], args[1], args[2]) }, ScriptableObject.EMPTY)
    }

    /**
     * ```js
     * __exitIfError__(action) {
     *     try {
     *         return action();
     *     } catch (e) {
     *         if (e instanceof java.lang.Throwable) {
     *             runtime.exit(e);
     *         } else if (e instanceof Error) {
     *             runtime.exit(new EvaluatorException(`${e.name}: ${e.message}`, e.fileName, e.lineNumber));
     *         } else {
     *             runtime.exit();
     *         }
     *     }
     * }
     * ```
     */
    private fun exitIfError(cx: Context, scope: VarScope, action: Any?): Any? {
        try {
            return call(cx, scope, action)
        } catch (t: Throwable) {
            if (!isCaughtByScript(cx, t)) throw t
            // The value a script `catch (e)` receives for the throwable.
            // zh-CN: 脚本 `catch (e)` 对该异常得到的值.
            val e = ScriptableObject.getProperty(RhinoScriptRuntime.newCatchScope(t, null, "e", cx, scope), "e")
            val runtime = name(cx, scope, "runtime")
            when {
                (e as? Wrapper)?.unwrap() is Throwable -> callMethod(cx, scope, runtime, "exit", e)
                RhinoScriptRuntime.instanceOf(e, name(cx, scope, "Error"), cx) -> {
                    val detail = "${RhinoScriptRuntime.toString(prop(cx, scope, e, "name"))}: ${RhinoScriptRuntime.toString(prop(cx, scope, e, "message"))}"
                    val sourceName = Context.jsToJava(prop(cx, scope, e, "fileName"), String::class.java) as String?
                    val lineNumber = Context.jsToJava(prop(cx, scope, e, "lineNumber"), Int::class.javaPrimitiveType) as Int
                    callMethod(cx, scope, runtime, "exit", Context.javaToJS(EvaluatorException(detail, sourceName, lineNumber), scope))
                }
                else -> callMethod(cx, scope, runtime, "exit")
            }
            return Undefined.instance
        }
    }

    /** What a script `catch` block catches (`Interpreter`: the others only run `finally` blocks or pass through). */
    private fun isCaughtByScript(cx: Context, t: Throwable): Boolean = when (t) {
        is JavaScriptException, is EcmaError, is EvaluatorException -> true
        is ContinuationPending -> false
        is RuntimeException, is Error -> cx.hasFeature(Context.FEATURE_ENHANCED_JAVA_ACCESS)
        else -> false
    }

    /**
     * ```js
     * __asGlobal__(obj, functions, scope) {
     *     functions.forEach((name) => {
     *         let { objKey, scopeKey } = (() => {
     *             if (typeof name === 'string') { ... objKey = scopeKey = name }
     *             if (typeof name === 'object') { objKey = Object.values(name)[0]; scopeKey = Object.keys(name)[0] }
     *             throw TypeError(`Unknown type of name (${name}) in functions for __asGlobal__`);
     *         })();
     *         let f = obj[objKey];
     *         if (typeof f !== 'function') throw ReferenceError(`${objKey} doesn't exist on object: ${obj}`);
     *         if (isObject(scope)) scope[scopeKey] = f.bind(obj); else global[scopeKey] = f.bind(obj);
     *     });
     * }
     * ```
     */
    private fun asGlobal(cx: Context, scope: VarScope, obj: Any?, functions: Any?, target: Any?): Any? {
        val each = method(scope, "", 1) { c, s, _, args ->
            bindAsGlobal(c, s, obj, args[0], target)
            Undefined.instance
        }
        callMethod(cx, scope, functions, "forEach", each)
        return Undefined.instance
    }

    private fun bindAsGlobal(cx: Context, scope: VarScope, obj: Any?, entry: Any?, target: Any?) {
        val (objKey, scopeKey) = when (RhinoScriptRuntime.`typeof`(entry)) {
            "string" -> entry to entry
            "object" -> {
                val objectConstructor = name(cx, scope, "Object")
                first(cx, scope, callMethod(cx, scope, objectConstructor, "values", entry)) to first(cx, scope, callMethod(cx, scope, objectConstructor, "keys", entry))
            }
            else -> throw RhinoScriptRuntime.constructError("TypeError", "Unknown type of name (${RhinoScriptRuntime.toString(entry)}) in functions for $AS_GLOBAL")
        }
        val f = RhinoScriptRuntime.getObjectElem(obj, objKey, cx, scope)
        if (RhinoScriptRuntime.`typeof`(f) != "function") {
            throw RhinoScriptRuntime.constructError("ReferenceError", "${RhinoScriptRuntime.toString(objKey)} doesn't exist on object: ${RhinoScriptRuntime.toString(obj)}")
        }
        val bound = callMethod(cx, scope, f, "bind", obj)
        val destination = when (RhinoScriptRuntime.toBoolean(call(cx, scope, name(cx, scope, "isObject"), target))) {
            true -> target
            else -> name(cx, scope, "global")
        }
        RhinoScriptRuntime.setObjectElem(destination, scopeKey, bound, cx, scope)
    }

    /** `let [ first ] = array` */
    private fun first(cx: Context, scope: VarScope, array: Any?): Any? = RhinoScriptRuntime.getObjectElem(array, 0, cx, scope).let {
        if (it === Scriptable.NOT_FOUND) Undefined.instance else it
    }

}
