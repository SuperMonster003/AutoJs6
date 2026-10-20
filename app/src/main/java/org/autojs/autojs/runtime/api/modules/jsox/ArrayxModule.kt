package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptBuiltIn
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.Token
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `Arrayx` module on the new script module layer (roadmap P2.1), also the extension of the built-in `Array`
 * (`Array.ensureArray` and the `Array.prototype` methods taking the array as `this`) when the "extend JavaScript built-in
 * objects" option or `jsox` applies it.
 *
 * Behavior is the one of the current Kotlin module (documentation and d.ts agree with it; ROADMAP appendix E): the
 * `sorted*` functions leave their input untouched, `sortBy*` / `sortDescending` / `shuffle` sort in place and return the
 * same array, sorting compares with `>` / `<` and falls back to the string forms, and equality of elements is JS
 * SameValueZero (the set operations `subtract` / `differ` too, which used Java equality and missed equal values of
 * different Java types such as `ConsString` and `String`). Errors are TypeErrors (D10).
 *
 * zh-CN: 新脚本模块层上的 Arrayx 模块 (P2.1), 启用 "JavaScript 内置对象扩展" 或调用 jsox 时也作为内置 Array 的扩展 (Array.ensureArray 与以数组为 this
 * 的 Array.prototype 方法). 行为与现有 Kotlin 模块一致 (文档与 d.ts 也如此, 见附录 E): sorted* 不改动输入, sortBy* / sortDescending / shuffle 原地排序并返回同一数组,
 * 排序用 `>` / `<` 比较并以字符串形式兜底, 元素相等按 JS SameValueZero (subtract / differ 原先用 Java 相等, 会漏掉 ConsString 与 String 等不同 Java 类型的相等值).
 * 错误为 TypeError (D10).
 */
@ScriptModule(name = "Arrayx", dollarPrefix = false, builtIn = "Array")
object ArrayxModule {

    /** Fails with a TypeError unless every argument is a JavaScript array. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ensureArray(vararg o: Any?) {
        o.forEach { if (!JsoxValues.isJsArray(it)) throw JsoxValues.typeError("Arrayx.ensureArray", "o", "Array", it) }
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun distinct(cx: Context, scope: VarScope, arr: List<Any?>): NativeArray = newArray(cx, scope, distinctValues(arr))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun distinctBy(cx: Context, scope: VarScope, arr: List<Any?>, selector: Callable): NativeArray {
        val selected = ArrayList<Any?>()
        val result = arr.filter { element ->
            val key = selector.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(element))
            (selected.none { RhinoScriptRuntime.sameZero(it, key) }).also { isNew -> if (isNew) selected += key }
        }
        return newArray(cx, scope, result)
    }

    /** The distinct elements of [arr] followed by the other arguments (arrays contribute their elements). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun union(cx: Context, scope: VarScope, arr: List<Any?>, vararg others: Any?): NativeArray {
        val values = ArrayList<Any?>(arr)
        others.forEach { other -> if (other is NativeArray) values.addAll(elementsOf(other)) else values.add(other) }
        return newArray(cx, scope, distinctValues(values))
    }

    /** The distinct elements of [arr] found in every other argument (in an array, or equal to a single value). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun intersect(cx: Context, scope: VarScope, arr: List<Any?>, vararg others: Any?): NativeArray {
        if (arr.isEmpty() || others.isEmpty()) return newArray(cx, scope, emptyList())
        val result = distinctValues(arr).filter { candidate ->
            others.all { other ->
                when (other) {
                    is NativeArray -> elementsOf(other).any { RhinoScriptRuntime.sameZero(candidate, it) }
                    else -> RhinoScriptRuntime.shallowEq(candidate, other)
                }
            }
        }
        return newArray(cx, scope, result)
    }

    /** The distinct elements of [arr] that are not in [other]. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun subtract(cx: Context, scope: VarScope, arr: List<Any?>, other: List<Any?>): NativeArray =
        newArray(cx, scope, distinctValues(arr).filter { a -> other.none { RhinoScriptRuntime.sameZero(a, it) } })

    /** Symmetric difference: the distinct elements only in [arr], then those only in [other]. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun differ(cx: Context, scope: VarScope, arr: List<Any?>, other: List<Any?>): NativeArray {
        val onlyA = distinctValues(arr).filter { a -> other.none { RhinoScriptRuntime.sameZero(a, it) } }
        val onlyB = distinctValues(other).filter { b -> arr.none { RhinoScriptRuntime.sameZero(b, it) } }
        return newArray(cx, scope, distinctValues(onlyA + onlyB))
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortBy(cx: Context, scope: VarScope, arr: NativeArray, selector: Callable): NativeArray =
        if (arr.length < 2) arr else sort(cx, scope, arr, comparator(scope, selector, descending = false))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortByDescending(cx: Context, scope: VarScope, arr: NativeArray, selector: Callable): NativeArray =
        if (arr.length < 2) arr else sort(cx, scope, arr, comparator(scope, selector, descending = true))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortDescending(cx: Context, scope: VarScope, arr: NativeArray): NativeArray = sort(cx, scope, arr, comparator(scope, null, descending = true))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sorted(cx: Context, scope: VarScope, arr: List<Any?>): NativeArray = sort(cx, scope, newArray(cx, scope, arr), comparator(scope, null, descending = false))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortedDescending(cx: Context, scope: VarScope, arr: List<Any?>): NativeArray = sort(cx, scope, newArray(cx, scope, arr), comparator(scope, null, descending = true))

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortedBy(cx: Context, scope: VarScope, arr: List<Any?>, selector: Callable): NativeArray {
        val copy = newArray(cx, scope, arr)
        return if (arr.size < 2) copy else sort(cx, scope, copy, comparator(scope, selector, descending = false))
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun sortedByDescending(cx: Context, scope: VarScope, arr: List<Any?>, selector: Callable): NativeArray {
        val copy = newArray(cx, scope, arr)
        return if (arr.size < 2) copy else sort(cx, scope, copy, comparator(scope, selector, descending = true))
    }

    /** Shuffles [arr] in place (a sort with a random comparator, as before) and returns it. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun shuffle(cx: Context, scope: VarScope, arr: NativeArray): NativeArray =
        sort(cx, scope, arr, LambdaFunction(scope, 2, SerializableCallable { _, _, _, _ -> if (Math.random() < 0.5) 1 else -1 }))

    private fun distinctValues(values: Iterable<Any?>): List<Any?> = ArrayList<Any?>().also { result ->
        values.forEach { value -> if (result.none { RhinoScriptRuntime.sameZero(it, value) }) result += value }
    }

    /** The elements of a script array as [ScriptArgs.list] reads them (holes as `undefined`). */
    private fun elementsOf(array: NativeArray): List<Any?> = ScriptArgs.list(array, "Arrayx", "arr")

    private fun newArray(cx: Context, scope: VarScope, values: List<Any?>): NativeArray =
        cx.newArray(scope, Array(values.size) { i -> Context.javaToJS(values[i], scope) }) as NativeArray

    /** Sorts [array] in place with the built-in `Array.prototype.sort` (not the one the script may have replaced). */
    private fun sort(cx: Context, scope: VarScope, array: NativeArray, comparator: Callable): NativeArray {
        val prototype = TopLevel.getBuiltinPrototype(ScriptableObject.getTopLevelScope(scope), TopLevel.Builtins.Array)
        (ScriptableObject.getProperty(prototype, "sort") as Callable).call(cx, scope, array, arrayOf(comparator))
        return array
    }

    private fun comparator(scope: VarScope, selector: Callable?, descending: Boolean): Callable =
        LambdaFunction(scope, 2, SerializableCallable { cx, s, thisObj, args ->
            var a = args.getOrNull(0)
            var b = args.getOrNull(1)
            if (selector != null) {
                a = selector.call(cx, s, thisObj, arrayOf(a))
                b = selector.call(cx, s, thisObj, arrayOf(b))
            }
            if (descending) compare(b, a) else compare(a, b)
        })

    /** `>` / `<`, then the string forms (the comparison of the current Kotlin module). */
    private fun compare(a: Any?, b: Any?): Int = when {
        RhinoScriptRuntime.compare(a, b, Token.GT) -> 1
        RhinoScriptRuntime.compare(a, b, Token.LT) -> -1
        a == b -> 0
        RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.GT) -> 1
        RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.LT) -> -1
        else -> 0
    }

}
