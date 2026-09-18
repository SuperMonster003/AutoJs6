package org.autojs.autojs.util

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Public properties declared by a Kotlin companion object, resolved with Java reflection so that
 * kotlin-reflect is not needed at runtime.
 *
 * `const val` and `@JvmField val` members compile to public static fields of the outer class; every other
 * property gets a public getter (`getX()` / `isX()`) on the companion class. Kotlin's `Companion` holder,
 * `$`-mangled synthetic members and non-public properties are not exported, which matches what a caller
 * could reach through kotlin-reflect (`KCallable.call` refuses non-public members).
 *
 * zh-CN: Kotlin 伴生对象声明的公开属性, 用 Java 反射解析, 运行时不再需要 kotlin-reflect.
 * `const val` 与 `@JvmField val` 编译为外部类的公开静态字段; 其余属性在伴生类上生成公开 getter (`getX()` / `isX()`).
 * Kotlin 的 `Companion` 持有字段, 带 `$` 的合成成员与非公开属性不导出, 与 kotlin-reflect 下可访问的范围一致
 * (`KCallable.call` 拒绝非公开成员).
 */
object CompanionProperties {

    /**
     * Property name to getter, in declaration order of the static fields followed by the companion getters.
     * zh-CN: 属性名到取值函数的映射, 顺序为静态字段声明顺序, 其后为伴生类 getter.
     */
    @JvmStatic
    fun publicGetters(outer: Class<*>, companion: Any): Map<String, () -> Any?> {
        val getters = LinkedHashMap<String, () -> Any?>()
        outer.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && !it.isSynthetic && isPropertyName(it.name) }
            .forEach { field -> getters[field.name] = { field.get(null) } }
        companion.javaClass.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.isBridge }
            .filter { it.parameterCount == 0 && it.returnType != Void.TYPE }
            .forEach { method -> propertyNameOf(method)?.let { name -> getters.putIfAbsent(name) { method.invoke(companion) } } }
        return getters
    }

    private fun isPropertyName(name: String) = name != "Companion" && name != "INSTANCE" && '$' !in name

    private fun propertyNameOf(method: Method): String? {
        val name = method.name
        return when {
            name.length > 3 && name.startsWith("get") && name[3].isUpperCase() -> name[3].lowercaseChar() + name.substring(4)
            name.length > 2 && name.startsWith("is") && name[2].isUpperCase() && method.returnType == Boolean::class.javaPrimitiveType -> name
            else -> null
        }
    }

}
