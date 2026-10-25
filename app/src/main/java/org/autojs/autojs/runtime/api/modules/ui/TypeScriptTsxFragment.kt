package org.autojs.autojs.runtime.api.modules.ui

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Wrapper
import java.util.Collections
import java.util.IdentityHashMap

/** A neutral snapshot: consumers choose XML or Compose scalar semantics, never the fragment factory. */
internal class TypeScriptTsxFragment private constructor(
    val owner: Any?,
    val values: List<Any?>,
    val depth: Int,
    val arrayDepth: Int,
    val largestArray: Int,
    val cost: Int,
) {
    companion object {
        // Count containers as well as ignored values, so nested arrays of false cannot evade limits.
        const val MAX_INPUT_ENTRIES = ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE * 2 + 1

        fun create(owner: Any?, children: List<Any?>): TypeScriptTsxFragment {
            val output = ArrayList<Any?>()
            val activeArrays = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            var cost = 1
            var maximumDepth = 1
            var maximumArrayDepth = 0
            var largestArray = 0
            fun bound(condition: Boolean) {
                if (!condition) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
            }
            fun visit(input: Any?, depth: Int, arrayDepth: Int) {
                bound(++cost <= MAX_INPUT_ENTRIES && depth <= ComposeUiLimits.MAX_DEPTH)
                maximumDepth = maxOf(maximumDepth, depth)
                maximumArrayDepth = maxOf(maximumArrayDepth, arrayDepth)
                val value = unwrapTsxValue(input)
                when (value) {
                    is TypeScriptTsxFragment -> {
                        if (value.owner !== owner) throw ComposeUiContractException(ComposeUiErrorCodes.NODE_DETACHED)
                        cost += value.cost - 1
                        maximumDepth = maxOf(maximumDepth, depth + value.depth)
                        maximumArrayDepth = maxOf(maximumArrayDepth, arrayDepth + value.arrayDepth)
                        largestArray = maxOf(largestArray, value.largestArray)
                        bound(cost <= MAX_INPUT_ENTRIES && maximumDepth <= ComposeUiLimits.MAX_DEPTH)
                        output.addAll(value.values)
                    }
                    is NativeArray, is List<*>, is Array<*> -> {
                        if (!activeArrays.add(value)) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
                        val size = when (value) {
                            is NativeArray -> value.length.also { bound(it <= ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE) }.toInt()
                            is List<*> -> value.size
                            else -> (value as Array<*>).size
                        }
                        bound(size <= ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE)
                        largestArray = maxOf(largestArray, size)
                        maximumArrayDepth = maxOf(maximumArrayDepth, arrayDepth + 1)
                        for (index in 0 until size) {
                            val child = when (value) {
                                is NativeArray -> value.get(index, value).takeUnless { it === Scriptable.NOT_FOUND }
                                is List<*> -> value[index]
                                else -> (value as Array<*>)[index]
                            }
                            visit(child, depth + 1, arrayDepth + 1)
                        }
                        activeArrays.remove(value)
                    }
                    else -> output += value
                }
                bound(output.size <= ComposeUiLimits.MAX_LAZY_CHILDREN_PER_NODE)
            }
            children.forEach { visit(it, 1, 0) }
            return TypeScriptTsxFragment(owner, Collections.unmodifiableList(output), maximumDepth,
                maximumArrayDepth, largestArray, cost)
        }
    }
}

/** NativeObject implements Map: generic collection normalization would erase Compose node identity. */
internal fun unwrapTsxValue(input: Any?): Any? {
    var value = input
    var layers = 0
    while (value is Wrapper) {
        if (++layers > ComposeUiLimits.MAX_DEPTH) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        val next = value.unwrap()
        if (next === value) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        value = next
    }
    return if (value is ConsString) Context.toString(value) else value
}
