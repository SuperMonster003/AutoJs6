package org.autojs.autojs.extension

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs6.R.plurals as R_plurals
import org.autojs.autojs6.R.string as R_string

open class FlexibleArray {

    companion object {

        private val context by lazy { GlobalAppContext.get() }
        private val resources by lazy { context.resources }

        fun <R> ensureArgumentsNotEmpty(args: Array<out Any?>, function: (arrayArgs: Array<Any?>) -> R): R {
            if (args.isNotEmpty()) return unwrapAndInvokeAll(args, function)
            throw WrappedIllegalArgumentException(context.getString(R_string.error_arguments_cannot_be_empty))
        }

        fun <R> ensureArgumentsIsEmpty(args: Array<out Any?>, function: () -> R): R {
            if (args.isEmpty()) return function.invoke()
            throw WrappedIllegalArgumentException(context.getString(R_string.error_arguments_must_be_empty))
        }

        fun <R> ensureArgumentsOnlyOne(args: Array<out Any?>, function: (singleArg: Any?) -> R): R {
            return ensureArgumentsOnlyOne(args, false, function)
        }

        fun <R> ensureArgumentsOnlyOne(args: Array<out Any?>, preventUnwrapped: Boolean, function: (singleArg: Any?) -> R): R {
            return when {
                args.isEmpty() -> throw WrappedIllegalArgumentException(context.getString(R_string.error_arguments_cannot_be_empty))
                args.size > 1 -> throw WrappedIllegalArgumentException(resources.getQuantityString(R_plurals.error_method_only_accepts_n_arguments, 1, 1))
                preventUnwrapped -> invokeFirst(args, function)
                else -> unwrapAndInvokeFirst(args, function)
            }
        }

        fun <R> ensureArgumentsAtLeast(args: Array<out Any?>, n: Int, function: (arrayArgs: Array<Any?>) -> R): R {
            if (args.size >= n) return unwrapAndInvokeAll(args, function)
            throw WrappedIllegalArgumentException(resources.getQuantityString(R_plurals.error_method_only_accepts_no_less_than_n_arguments, n, n))
        }

        fun <R> ensureArgumentsAtMost(args: Array<out Any?>, n: Int, function: (arrayArgs: Array<Any?>) -> R): R {
            if (args.size <= n) return unwrapAndInvokeAll(args, function)
            throw WrappedIllegalArgumentException(resources.getQuantityString(R_plurals.error_method_only_accepts_no_more_than_n_arguments, n, n))
        }

        fun <R> ensureArgumentsLength(args: Array<out Any?>, n: Int, function: (arrayArgs: Array<Any?>) -> R): R {
            if (args.size == n) return unwrapAndInvokeAll(args, function)
            throw WrappedIllegalArgumentException(resources.getQuantityString(R_plurals.error_method_only_accepts_n_arguments, n, n))
        }

        fun <R> ensureArgumentsLengthInRange(args: Array<out Any?>, range: IntRange, function: (arrayArgs: Array<Any?>) -> R): R {
            if (args.size in range) return unwrapAndInvokeAll(args, function)
            val (min, max) = range.run { first to last }
            throw WrappedIllegalArgumentException(context.getString(R_string.error_method_only_accepts_a_number_of_arguments_in_the_range_n_to_m, min, max))
        }

        fun unwrap(o: Any?): Any? = RhinoUtils.unwrap(o)

        fun <R> unwrapArguments(args: Array<out Any?>, function: (arrayArgs: Array<Any?>) -> R): R {
            return unwrapAndInvokeAll(args, function)
        }

        private fun <R> invokeFirst(args: Array<out Any?>, function: (arg: Any?) -> R): R {
            return args[0].let { o -> function.invoke(if (o is Unit) UNDEFINED else o) }
        }

        private fun <R> unwrapAndInvokeFirst(args: Array<out Any?>, function: (arg: Any?) -> R): R {
            return args[0].let { o -> function.invoke(unwrap(o)) }
        }

        private fun <R> unwrapAndInvokeAll(args: Array<out Any?>, function: (args: Array<Any?>) -> R): R {
            return function.invoke(args.map { unwrap(it) }.toTypedArray())
        }

        // @Reference to stackoverflow.com by SuperMonster003 on May 26, 2024.
        //  ! https://stackoverflow.com/questions/51587403/component-destructuring-with-fewer-than-expected-components
        private fun <T> Array<out T>.componentN(n: Int): T? = getOrNull(n - 1)
        operator fun <T> Array<out T>.component1(): T? = componentN(1)
        operator fun <T> Array<out T>.component2(): T? = componentN(2)
        operator fun <T> Array<out T>.component3(): T? = componentN(3)
        operator fun <T> Array<out T>.component4(): T? = componentN(4)
        operator fun <T> Array<out T>.component5(): T? = componentN(5)
        operator fun <T> Array<out T>.component6(): T? = componentN(6)
        operator fun <T> Array<out T>.component7(): T? = componentN(7)
        operator fun <T> Array<out T>.component8(): T? = componentN(8)
        operator fun <T> Array<out T>.component9(): T? = componentN(9)

    }

}