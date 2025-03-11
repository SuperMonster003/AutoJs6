package org.autojs.autojs.runtime.api.augment.jsox

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.annotation.RhinoRuntimeFunctionWithThisObjInterface
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.unshiftWith
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.util.Util.ensureArrayTypeRhino
import org.autojs.autojs.util.RhinoUtils.coerceArray
import org.autojs.autojs.util.RhinoUtils.coerceFunction
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Token
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * JavaScript build-in object extension for the native Array object.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Arrayx(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), JsBuildInObjectExtensible {

    override val key: String = javaClass.simpleName

    override val selfAssignmentFunctions = listOf(
        ::ensureArray.name to AS_JSOX_STATIC,
        ::distinct.name to AS_JSOX_PROTO,
        ::distinctBy.name to AS_JSOX_PROTO,
        ::union.name to AS_JSOX_PROTO,
        ::intersect.name to AS_JSOX_PROTO,
        ::subtract.name to AS_JSOX_PROTO,
        ::differ.name to AS_JSOX_PROTO,
        ::sortBy.name to AS_JSOX_PROTO,
        ::sortByDescending.name to AS_JSOX_PROTO,
        ::sortDescending.name to AS_JSOX_PROTO,
        ::sorted.name to AS_JSOX_PROTO,
        ::sortedDescending.name to AS_JSOX_PROTO,
        ::sortedBy.name to AS_JSOX_PROTO,
        ::sortedByDescending.name to AS_JSOX_PROTO,
        ::shuffle.name to AS_JSOX_PROTO,
    )

    override fun extendBuildInObject() {
        extendBuildInObjectInternal(scriptRuntime, this, "Array", Proto::class.java)
    }

    internal object Proto {

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun distinct(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 0) {
            distinct(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun distinctBy(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            distinctBy(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun union(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsAtLeast(args, 0) {
            union(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun intersect(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsAtLeast(args, 0) {
            intersect(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun subtract(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            subtract(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun differ(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            differ(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortBy(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            sortBy(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortByDescending(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            sortByDescending(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortDescending(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 0) {
            sortDescending(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sorted(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 0) {
            sorted(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortedDescending(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 0) {
            sortedDescending(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortedBy(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            sortedBy(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun sortedByDescending(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 1) {
            sortedByDescending(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun shuffle(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 0) {
            shuffle(scriptRuntime, it.unshiftWith(thisObj))
        }

    }

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureArray(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = unwrapArguments(args) {
            undefined { ensureArrayTypeRhino(*it) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun distinct(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            coerceArray(it).distinct().toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun distinctBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            distinctByRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun distinctByRhino(it: Array<Any?>): NativeArray = withRhinoContext { cx ->
            val (arr, selector) = it
            coerceArray(arr).distinctBy { ele ->
                require(arr is NativeArray)
                coerceFunction(selector).call(cx, arr, arr, arrayOf(ele))
            }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun union(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtLeast(args, 1) {
            it.fold(coerceArray(it[0])) { acc, arg -> acc.union(coerceArray(arg)).toNativeArray() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun intersect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtLeast(args, 1) {
            it.fold(coerceArray(it[0])) { acc, arg -> acc.intersect(coerceArray(arg)).toNativeArray() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun subtract(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            coerceArray(it[0]).subtract(coerceArray(it[1])).toNativeArray()
        }

        // Symmetric difference (zh-CN: 对称差集)
        // Result de-duplication (zh-CN: 结果去重)
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun differ(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            val a = coerceArray(it[0])
            val b = coerceArray(it[1])
            ((a subtract b) union (b subtract a)).toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            val (arr, selector) = it
            sortByRhino(arr, selector)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortByRhino(arr: Any?, selector: Any?): NativeArray = withRhinoContext { cx ->
            require(arr is NativeArray) { "Argument arr for Arrayx.sortBy must be a JavaScript Array" }
            require(selector is BaseFunction) { "Argument selector for Arrayx.sortBy must be a JavaScript Function" }
            when {
                arr.length < 2 -> arr
                else -> {
                    // In-place sorting (zh-CN: 原地排序)
                    NativeArray.js_sort(cx, arr, arr, arrayOf(toCompareFunctionAsc(selector)))
                    arr
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortByDescending(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            val (arr, selector) = it
            sortByDescendingRhino(arr, selector)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortByDescendingRhino(arr: Any?, selector: Any?): NativeArray = withRhinoContext { cx ->
            require(arr is NativeArray) { "Argument arr for Arrayx.sortByDescending must be a JavaScript Array" }
            require(selector is BaseFunction) { "Argument selector for Arrayx.sortByDescending must be a JavaScript Function" }
            when {
                arr.length < 2 -> arr
                else -> {
                    // In-place sorting (zh-CN: 原地排序)
                    NativeArray.js_sort(cx, arr, arr, arrayOf(toCompareFunctionDesc(selector)))
                    arr
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortDescending(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            sortDescendingRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortDescendingRhino(arr: Any?): NativeArray = withRhinoContext { cx ->
            require(arr is NativeArray) { "Argument arr for Arrayx.sortDescending must be a JavaScript Array" }
            NativeArray.js_sort(cx, arr, arr, arrayOf(toCompareFunctionDesc()))
            arr
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sorted(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            sortedRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortedRhino(it: Any?): NativeArray = withRhinoContext { cx ->
            require(it is NativeArray) { "Argument arr for Arrayx.sorted must be a JavaScript Array" }
            val copied = it.slice(it.indices).toNativeArray()
            NativeArray.js_sort(cx, it, copied, arrayOf(toCompareFunctionAsc()))
            copied
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortedDescending(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            sortedDescendingRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortedDescendingRhino(it: Any?): NativeArray = withRhinoContext { cx ->
            require(it is NativeArray) { "Argument arr for Arrayx.sortedDescending must be a JavaScript Array" }
            val copied = it.slice(it.indices).toNativeArray()
            NativeArray.js_sort(cx, it, copied, arrayOf(toCompareFunctionDesc()))
            copied
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortedBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            val (arr, selector) = it
            sortedByRhino(arr, selector)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortedByRhino(arr: Any?, selector: Any?): NativeArray = withRhinoContext { cx ->
            require(arr is NativeArray) { "Argument arr for Arrayx.sortedBy must be a JavaScript Array" }
            require(selector is BaseFunction) { "Argument selector for Arrayx.sortedBy must be a JavaScript Function" }
            val copied = arr.slice(arr.indices).toNativeArray()
            when {
                arr.length < 2 -> copied
                else -> {
                    NativeArray.js_sort(cx, arr, copied, arrayOf(toCompareFunctionAsc(selector)))
                    copied
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sortedByDescending(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLength(args, 2) {
            val (arr, selector) = it
            sortedByDescendingRhino(arr, selector)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sortedByDescendingRhino(arr: Any?, selector: Any?): NativeArray = withRhinoContext { cx ->
            require(arr is NativeArray) { "Argument arr for Arrayx.sortedByDescending must be a JavaScript Array" }
            require(selector is BaseFunction) { "Argument selector for Arrayx.sortedByDescending must be a JavaScript Function" }
            val copied = arr.slice(arr.indices).toNativeArray()
            when {
                arr.length < 2 -> copied
                else -> {
                    NativeArray.js_sort(cx, arr, copied, arrayOf(toCompareFunctionDesc(selector)))
                    copied
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun shuffle(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            shuffleRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun shuffleRhino(it: Any?): NativeArray = withRhinoContext { cx ->
            require(it is NativeArray) { "Argument arr for Arrayx.shuffle must be a JavaScript Array" }
            NativeArray.js_sort(cx, it, it, arrayOf(toCompareFunctionRandom()))
            it
        }

        private fun toCompareFunctionAsc(selector: BaseFunction) = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Int {
                val (a, b) = args
                val sA = selector.call(cx, scope, thisObj, arrayOf(a))
                val sB = selector.call(cx, scope, thisObj, arrayOf(b))
                return compareAsc(sA, sB)
            }
        }

        private fun toCompareFunctionAsc() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Int {
                val (a, b) = args
                return compareAsc(a, b)
            }
        }

        private fun toCompareFunctionDesc(selector: BaseFunction) = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Int {
                val (a, b) = args
                val sA = selector.call(cx, scope, thisObj, arrayOf(a))
                val sB = selector.call(cx, scope, thisObj, arrayOf(b))
                return compareDesc(sA, sB)
            }
        }

        private fun toCompareFunctionDesc() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Int {
                val (a, b) = args
                return compareDesc(a, b)
            }
        }

        private fun toCompareFunctionRandom() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any>): Int {
                val (a, b) = args
                return compareRandom(a, b)
            }
        }

        private fun compareAsc(a: Any?, b: Any?) = when {
            RhinoScriptRuntime.compare(a, b, Token.GT) -> 1
            RhinoScriptRuntime.compare(a, b, Token.LT) -> -1
            a == b -> 0
            RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.GT) -> 1
            RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.LT) -> -1
            else -> 0
        }

        private fun compareDesc(a: Any?, b: Any?) = when {
            RhinoScriptRuntime.compare(a, b, Token.GT) -> -1
            RhinoScriptRuntime.compare(a, b, Token.LT) -> 1
            a == b -> 0
            RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.GT) -> -1
            RhinoScriptRuntime.compare(Context.toString(a), Context.toString(b), Token.LT) -> 1
            else -> 0
        }

        private fun compareRandom(a: Any?, b: Any?) = if (Math.random() < 0.5) 1 else -1

    }

}