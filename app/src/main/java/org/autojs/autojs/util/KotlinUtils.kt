package org.autojs.autojs.util

import org.jetbrains.annotations.Contract
import java.math.BigInteger
import java.util.Arrays
import kotlin.reflect.KMutableProperty0

object KotlinUtils {

    @Contract(value = "_ -> new", pure = true)
    fun boxing(obj: Any?): Any? {
        return if (obj is String || obj is Boolean || obj is Int || obj is Short || obj is Long || obj is Float || obj is Double || obj is BigInteger || obj is Char) {
            KotlinBoxed(obj)
        } else obj
    }

    @Contract(pure = true)
    fun unboxing(obj: Any): Any = if (obj is KotlinBoxed) obj.get() else obj

    class KotlinBoxed(private val mObject: Any) {
        fun get(): Any = mObject
    }

    @JvmStatic
    inline fun <reified T> sortedArrayOf(vararg elements: T): Array<T> {
        val a = arrayOf(*elements)
        Arrays.sort(a)
        return a
    }

    @JvmStatic
    fun <R, T : KMutableProperty0<R?>> T.ifNull(provider: () -> R): R {
        val value = this.get()
        if (value != null) {
            return value
        }
        val newValue = provider()
        set(newValue)
        return newValue
    }

}