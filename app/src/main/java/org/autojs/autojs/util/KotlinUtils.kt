package org.autojs.autojs.util

import java.util.*
import kotlin.reflect.KMutableProperty0

object KotlinUtils {

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