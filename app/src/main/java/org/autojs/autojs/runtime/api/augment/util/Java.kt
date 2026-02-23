package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper

@Suppress("unused")
object Java : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        ::instanceof.name,
        ::array.name,
        ::toJsArray.name,
        ::objectToMap.name,
        ::mapToObject.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun instanceof(args: Array<out Any?>): Any = ensureArgumentsLength(args, 2) {
        val (obj, clazz) = it
        obj ?: throw WrappedIllegalArgumentException("Argument \"obj\" for util.java.instanceof must not be null")

        val resolvedClass = when (clazz) {
            null -> return@ensureArgumentsLength false
            is Class<*> -> clazz
            is NativeJavaClass -> clazz.classObject
            is Wrapper -> when (val unwrapped = clazz.unwrap()) {
                is Class<*> -> unwrapped
                is NativeJavaClass -> unwrapped.classObject
                else -> unwrapped.resolveClass()
            }
            else -> clazz.resolveClass()
        }
        resolvedClass.isAssignableFrom(obj.javaClass)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun array(args: Array<out Any?>): Any = ensureArgumentsAtLeast(args, 1) {
        val (componentType) = it
        val clazz = typeToClass(componentType)

        val dims = it.drop(1).map { arg -> Context.toNumber(arg).toInt() }

        if (dims.isEmpty()) {
            // Keep behavior consistent with Rhino JavaScript implementation: calling array(type) throws.
            // zh-CN: 保持与 Rhino JavaScript 实现一致: 调用 array(type) 时抛出异常.
            throw WrappedIllegalArgumentException("Missing array dimensions for util.java.array")
        }

        // Use java.lang.reflect.Array.newInstance(Class, int[]) to support arbitrary dimensions.
        // zh-CN: 使用 java.lang.reflect.Array.newInstance(Class, int[]) 以支持任意维度数量.
        java.lang.reflect.Array.newInstance(clazz, *dims.toIntArray())
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toJsArray(args: Array<out Any?>): Any? = ensureArgumentsAtLeast(args, 1) {
        val (list, nullListToEmptyArray) = it
        toJsArrayRhino(list, nullListToEmptyArray)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toJsArrayRhino(list: Any?, nullListToEmptyArray: Any?): NativeArray? {
        val isNullListToEmptyArray = nullListToEmptyArray ?: false
        list ?: return if (Context.toBoolean(isNullListToEmptyArray)) newNativeArray() else null
        if (list !is Iterable<*>) throw WrappedIllegalArgumentException("Argument \"list\" for toJsArray must be an iterable type")
        return list.toNativeArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun objectToMap(args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { o ->
        if (o.isJsNullish()) return@ensureArgumentsOnlyOne null
        if (o !is NativeObject) throw WrappedIllegalArgumentException("Argument \"o\" ${o.jsBrief()} for util.objectToMap must be a JavaScript Object")
        hashMapOf<String, Any?>().also { map ->
            for (key in ScriptableObject.getPropertyIds(o)) {
                if (RhinoUtils.js_object_hasOwnProperty(o, key.toString())) {
                    map[key.toString()] = o.prop(key.toString())
                }
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun mapToObject(args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { map ->
        if (map.isJsNullish()) return@ensureArgumentsOnlyOne null
        if (map !is Map<*, *>) throw WrappedIllegalArgumentException("Argument \"map\" ${map.jsBrief()} for util.mapToObject must be a Java Map")
        newNativeObject().also { o ->
            val iter = map.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                o.put(Context.toString(entry.key), o, entry.value)
            }
        }
    }

    private fun typeToClass(type: Any?) = when (type) {
        is Class<*> -> type
        is NativeJavaClass -> type.classObject
        is Wrapper -> when (val unwrapped = type.unwrap()) {
            is Class<*> -> unwrapped
            is NativeJavaClass -> unwrapped.classObject
            else -> unwrapped.resolveClass()
        }
        is String -> when (type) {
            "string" -> String::class.java
            "int" -> Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "double" -> java.lang.Double.TYPE
            "char" -> Character.TYPE
            "byte" -> java.lang.Byte.TYPE
            "float" -> java.lang.Float.TYPE
            "short" -> java.lang.Short.TYPE
            "boolean" -> java.lang.Boolean.TYPE
            else -> type.resolveClass()
        }
        else -> type.resolveClass()
    }

    private fun Any?.resolveClass(): Class<*> = Class.forName(Context.toString(this))
}
