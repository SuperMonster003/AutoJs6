package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.lang.reflect.Array.newInstance

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
        obj ?: return@ensureArgumentsLength false
        clazz ?: return@ensureArgumentsLength false
        Class.forName(Context.toString(clazz)).isAssignableFrom(obj.javaClass)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun array(args: Array<out Any?>): Any = ensureArgumentsAtLeast(args, 1) {
        val (componentType) = it
        val clazz = typeToClass(componentType)

        val dims = it.drop(1).map { arg -> Context.toNumber(arg).toInt() }

        // @Hint by SuperMonster003 on Jun 3, 2024.
        //  ! Reflection may cause some performance overhead,
        //  ! and potential exceptions will become harder to trace.
        //  ! zh-CN:
        //  ! 反射会带来一定的额外性能开销, 潜在的异常也难以追踪.
        //  !
        //  # val method = this::class.members.find { mem ->
        //  #     mem is KFunction<*> && mem.name == "newInstance" && mem.parameters.size == dims.size + 1
        //  # } as? KFunction<*>
        //  # method?.call(clazz, *dims.toIntArray().toTypedArray()) ?: throw IllegalArgumentException("Too many arguments for util.java")
        when (dims.size) {
            +0 -> newInstance(clazz, 0)
            +1 -> newInstance(clazz, dims[0])
            +2 -> newInstance(clazz, dims[0], dims[1])
            +3 -> newInstance(clazz, dims[0], dims[1], dims[2])
            +4 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3])
            +5 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4])
            +6 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5])
            +7 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6])
            +8 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7])
            +9 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7], dims[8])
            10 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7], dims[8], dims[9])
            11 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7], dims[8], dims[9], dims[10])
            12 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7], dims[8], dims[9], dims[10], dims[11])
            13 -> newInstance(clazz, dims[0], dims[1], dims[2], dims[3], dims[4], dims[5], dims[6], dims[7], dims[8], dims[9], dims[10], dims[11], dims[12])
            else -> throw WrappedIllegalArgumentException("Too many arguments for util.java")
        }
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
        if (o !is NativeObject) throw WrappedIllegalArgumentException("Argument for util.objectToMap must be a JavaScript Object")
        hashMapOf<String, Any?>().also { map ->
            for (key in ScriptableObject.getPropertyIds(o)) {
                map[key.toString()] = o.prop(key.toString())
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun mapToObject(args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { map ->
        if (map.isJsNullish()) return@ensureArgumentsOnlyOne null
        if (map !is Map<*, *>) throw WrappedIllegalArgumentException("Argument for util.mapToObject must be a Java Map")
        newNativeObject().also { o ->
            val iter = map.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                o.put(Context.toString(entry.key), o, entry.value)
            }
        }
    }

    private fun typeToClass(type: Any?) = when (type) {
        "string" -> String::class.java
        "int" -> Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "double" -> java.lang.Double.TYPE
        "char" -> Character.TYPE
        "byte" -> java.lang.Byte.TYPE
        "float" -> java.lang.Float.TYPE
        else -> Class.forName(Context.toString(type))
    }

}
