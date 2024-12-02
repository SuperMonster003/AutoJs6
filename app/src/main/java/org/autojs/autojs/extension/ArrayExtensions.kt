package org.autojs.autojs.extension

import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

object ArrayExtensions {

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
    fun <T> Array<T>.unshiftWith(thisObj: Any?): Array<Any?> {
        return Array(this.size + 1) { if (it == 0) thisObj else this[it - 1] }
    }

    fun <T> Array<T>.symmetricDifference(other: Array<T>): Set<T> {
        val a = this.asIterable() subtract other.asIterable().toSet()
        val b = other.asIterable() subtract this.asIterable().toSet()
        return a union b
    }

    fun Iterable<*>.toNativeArray(): NativeArray {
        return withRhinoContext { context, standardObjects ->
            context.newArray(standardObjects, this.map { Context.javaToJS(it, standardObjects) }.toTypedArray()) as NativeArray
        }!!
    }

    fun Array<*>.toNativeArray(): NativeArray {
        return withRhinoContext { context, standardObjects ->
            context.newArray(standardObjects, this.toList().map { Context.javaToJS(it, standardObjects) }.toTypedArray()) as NativeArray
        }!!
    }

    fun <K, V> Map<K, V>.toNativeObject(): NativeObject = newNativeObject().also { o ->
        forEach { entry: Map.Entry<K, V> ->
            val key = Context.toString(entry.key)
            when (val value = entry.value) {
                is String -> o.put(key, o, value)
                is Number -> o.put(key, o, Context.toNumber(value))
                is Boolean -> o.put(key, o, Context.toBoolean(value))
                is Wrapper -> o.put(key, o, value.unwrap())
                is Unit -> o.put(key, o, UNDEFINED)
                is List<*> -> o.put(key, o, value.toNativeArray())
                is Array<*> -> o.put(key, o, value.toNativeArray())
                is Map<*, *> -> o.put(key, o, value.toNativeObject())
                is Pair<*, *> -> o.put(key, o, newNativeObject().also { newObj ->
                    newObj.put(Context.toString(value.first), o, value.second)
                })
                else -> o.put(key, o, Context.javaToJS(value, o))
            }
        }
    }

}
