package org.autojs.autojs.util

import java.lang.reflect.Array as JavaArray

/**
 * Created by Stardust on May 8, 2017.
 * Transformed by SuperMonster003 on Jan 23, 2026.
 */
object ArrayUtils {

    @JvmStatic
    fun box(array: IntArray): Array<Int> {
        return Array(array.size) { i -> array[i] }
    }

    @JvmStatic
    fun unbox(array: Array<Int>): IntArray {
        return IntArray(array.size) { i -> array[i] }
    }

    @JvmStatic
    fun toStringArray(list: List<*>): Array<String?> {
        return Array(list.size) { i -> list[i]?.toString() }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> merge(a1: Array<T>, a2: Array<T>): Array<T> {
        val componentType = requireNotNull(a1.javaClass.componentType)
        val result = JavaArray.newInstance(componentType, a1.size + a2.size) as Array<T>
        System.arraycopy(a1, 0, result, 0, a1.size)
        System.arraycopy(a2, 0, result, a1.size, a2.size)
        return result
    }

    fun <T> Array<T>.unshiftWith(thisObj: Any?): Array<Any?> {
        return Array(this.size + 1) { if (it == 0) thisObj else this[it - 1] }
    }

    fun <T> Array<T>.symmetricDifference(other: Array<T>): Set<T> {
        val a = this.asIterable() subtract other.asIterable().toSet()
        val b = other.asIterable() subtract this.asIterable().toSet()
        return a union b
    }

}
