package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues.isNullish
import org.autojs.autojs.runtime.api.modules.util.UtilValues.typeError
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `util.java` on the new script module layer (roadmap P2.3): Java interoperability. Java values are returned as Java
 * objects (`array` gives a Java array, `objectToMap` a `java.util.HashMap`; the current module converted object arrays and
 * maps back into JS values), wrapped Java arguments are unwrapped, and errors are TypeError (D10).
 *
 * zh-CN: 新脚本模块层上的 util.java (P2.3): Java 互操作. Java 值以 Java 对象返回 (array 为 Java 数组, objectToMap 为 java.util.HashMap; 现有模块会把
 * 对象数组与映射转回 JS 值), 包装的 Java 参数会解包, 错误为 TypeError (D10).
 */
@ScriptModule(name = "java", parent = UtilModule::class, dollarPrefix = false)
object UtilJavaModule {

    /** Whether [obj] (unwrapped) is assignable to [clazz] (a Java class, a wrapped class or a class name); false for a nullish class. */
    @ScriptFunction(aliases = ["instanceOf"])
    fun instanceof(obj: Any?, clazz: Any?): Boolean {
        if (isNullish(obj)) throw typeError("util.java.instanceof", ScriptMessages.argumentCannotBe("obj", if (obj == null) "null" else "undefined"))
        if (isNullish(clazz)) return false
        val type = classOf(clazz)
        val value = if (obj is Wrapper) obj.unwrap() else obj
        return value != null && type.isAssignableFrom(value.javaClass)
    }

    /** A new Java array of [componentType] (a class, a class name or `string` / `int` / `long` / `double` / `char` / `byte` / `float` / `short` / `boolean`). */
    @ScriptFunction
    fun array(cx: Context, scope: VarScope, componentType: Any?, dimension: Any?, vararg dimensions: Any?): Any {
        val dims = (listOf(dimension) + dimensions).map { RhinoScriptRuntime.toNumber(it).toInt() }.toIntArray()
        val array = java.lang.reflect.Array.newInstance(typeOf(componentType), *dims)
        return Context.javaToJS(array, scope)
    }

    /** The elements of a Java Iterable as a JS array; null (or `[]` with [nullListToEmptyArray]) for a nullish list. */
    @ScriptFunction
    fun toJsArray(cx: Context, scope: VarScope, list: Any?, nullListToEmptyArray: Any? = null): Any? {
        if (isNullish(list)) {
            return if (!isNullish(nullListToEmptyArray) && RhinoScriptRuntime.toBoolean(nullListToEmptyArray)) cx.newArray(scope, 0) else null
        }
        val iterable = (if (list is Wrapper) list.unwrap() else list) as? Iterable<*> ?: throw typeError("util.java.toJsArray", "list", "java.lang.Iterable", list)
        return cx.newArray(scope, iterable.map { Context.javaToJS(it, scope) }.toTypedArray())
    }

    /** The own properties of a JS object as a `java.util.HashMap` (keys as strings); null for nullish. */
    @ScriptFunction
    fun objectToMap(scope: VarScope, o: Any?): Any? {
        if (isNullish(o)) return null
        if (o !is NativeObject) throw typeError("util.java.objectToMap", "o", "Object", o)
        val map = HashMap<String, Any?>()
        o.ids.forEach { id ->
            val key = RhinoScriptRuntime.toString(id)
            map[key] = ScriptableObject.getProperty(o, key)
        }
        return Context.javaToJS(map, scope)
    }

    /** The entries of a Java Map as a JS object (keys as strings); null for nullish. */
    @ScriptFunction
    fun mapToObject(cx: Context, scope: VarScope, map: Any?): Any? {
        if (isNullish(map)) return null
        val entries = (if (map is Wrapper) map.unwrap() else map) as? Map<*, *> ?: throw typeError("util.java.mapToObject", "map", "java.util.Map", map)
        return cx.newObject(scope).also { o -> entries.forEach { (k, v) -> o.put(RhinoScriptRuntime.toString(k), o, v) } }
    }

    private fun classOf(clazz: Any?): Class<*> = when (clazz) {
        is Class<*> -> clazz
        is NativeJavaClass -> clazz.classObject
        is Wrapper -> classOf(clazz.unwrap())
        is Scriptable -> Class.forName(RhinoScriptRuntime.toString(clazz))
        else -> Class.forName(RhinoScriptRuntime.toString(clazz))
    }

    private fun typeOf(type: Any?): Class<*> = when (type) {
        is CharSequence -> when (val name = type.toString()) {
            "string" -> String::class.java
            "int" -> Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "double" -> java.lang.Double.TYPE
            "char" -> Character.TYPE
            "byte" -> java.lang.Byte.TYPE
            "float" -> java.lang.Float.TYPE
            "short" -> java.lang.Short.TYPE
            "boolean" -> java.lang.Boolean.TYPE
            else -> Class.forName(name)
        }
        else -> classOf(type)
    }

}
