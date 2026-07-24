package org.autojs.autojs.runtime

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

interface IScriptBridges {

    fun call(func: BaseFunction, target: Any?, args: Array<*>): Any?

    fun call(func: Callable, target: Any?, args: Array<*>): Any? {
        if (func is BaseFunction) return call(func, target, args)
        val adapter = object : BaseFunction() {
            override fun call(
                cx: Context,
                scope: VarScope,
                thisObj: Scriptable,
                args: Array<out Any?>,
            ): Any? = func.call(cx, scope, thisObj, args.map { it }.toTypedArray())
        }.also {
            it.parentScope = when (func) {
                is Function -> func.declarationScope
                is Scriptable -> func.parentScope
                else -> null
            }
        }
        return call(adapter, target, args)
    }

    fun toArray(o: Iterable<*>?): NativeArray

    fun asArray(listLike: Any): NativeArray

    fun toString(obj: Any?): String

    fun toPrimitive(obj: Any?): Any?
}
