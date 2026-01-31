package org.autojs.autojs.rhino.extension

import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.getOrCreateStandardObjects
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray

object IterableExtensions {

    fun Iterable<*>.toNativeArray(): NativeArray {
        return RhinoUtils.withRhinoContext { cx ->
            val standardObjects = cx.getOrCreateStandardObjects()
            cx.newArray(standardObjects, this.map { Context.javaToJS(it, standardObjects) }.toTypedArray()) as NativeArray
        }
    }

    // @Hint by JetBrains AI Assistant on Oct 25, 2024.
    //  ! 31 is a prime number that acts as a mixer for hash code calculation.
    //  ! Multiplying by the prime number 31 helps to evenly distribute the hash values
    //  ! across different properties and reduce collisions.
    //  ! zh-CN (translated by Jetbrains AI Assistant on Oct 25, 2024):
    //  ! 31 是一个质数, 它的作用类似于混合器, 用于哈希码的计算.
    //  ! 乘以质数 31 可以帮助在不同属性值上更均匀地分散哈希值, 减少碰撞.
    fun Iterable<Any?>.toHashCode(initial: Int = 0): Int = fold(initial) { acc, o ->
        31 * acc + when (o) {
            null -> 0
            is Int -> o
            is Long -> o.hashCode()
            is Float -> o.hashCode()
            is Double -> o.hashCode()
            is Byte -> o.hashCode()
            is Short -> o.hashCode()
            is CharSequence -> o.hashCode()
            is Array<*> -> o.contentHashCode()
            is ByteArray -> o.contentHashCode()
            is CharArray -> o.contentHashCode()
            is ShortArray -> o.contentHashCode()
            is IntArray -> o.contentHashCode()
            is LongArray -> o.contentHashCode()
            is FloatArray -> o.contentHashCode()
            is DoubleArray -> o.contentHashCode()
            is BooleanArray -> o.contentHashCode()
            else -> o.hashCode()
        }
    }

}