package org.autojs.autojs.util

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Public instance properties of a Kotlin class reached through their JVM accessors, so that kotlin-reflect is not
 * needed at runtime.
 *
 * A property `foo` compiles to `getFoo()` and, when it is a `var`, `setFoo(value)`; a Boolean property `isFoo`
 * keeps its name as the getter and gets `setFoo(value)`. Only public, non-static, non-synthetic accessors are
 * considered, which matches what a caller could reach through kotlin-reflect (`KCallable.call` refuses non-public
 * members). Exceptions thrown by the accessors are rethrown unwrapped.
 *
 * zh-CN: Kotlin 类的公开实例属性, 经其 JVM 访问器 (`getFoo()` / `isFoo()` 与 `setFoo(value)`) 解析,
 * 运行时不再需要 kotlin-reflect. 只考虑公开, 非静态, 非合成的访问器, 与 kotlin-reflect 下可访问的范围一致
 * (`KCallable.call` 拒绝非公开成员); 访问器抛出的异常原样重抛.
 */
object BeanProperties {

    class Property internal constructor(val name: String, private val getter: Method, private val setter: Method?) {

        val isMutable: Boolean
            get() = setter != null

        val type: Class<*>
            get() = getter.returnType

        fun get(target: Any): Any? = invoke(getter, target)

        fun set(target: Any, value: Any?) {
            invoke(checkNotNull(setter) { "Property $name is read-only" }, target, value)
        }

        private fun invoke(method: Method, target: Any, vararg args: Any?): Any? = try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }

    }

    /**
     * The public property called [name] on [type], or null when the class has no public getter for it.
     * zh-CN: [type] 上名为 [name] 的公开属性, 无公开 getter 时返回 null.
     */
    @JvmStatic
    fun find(type: Class<*>, name: String): Property? {
        if (name.isEmpty() || '$' in name) return null
        val isPrefixed = name.length > 2 && name.startsWith("is") && name[2].isUpperCase()
        val capitalized = name.replaceFirstChar { it.uppercaseChar() }
        val getter = accessor(type, "get$capitalized") { it.parameterCount == 0 && it.returnType != Void.TYPE }
            ?: isPrefixed.takeIf { it }?.let { accessor(type, name) { m -> m.parameterCount == 0 && m.returnType == Boolean::class.javaPrimitiveType } }
            ?: return null
        val setterName = if (isPrefixed && getter.name == name) "set${name.substring(2)}" else "set$capitalized"
        val setter = accessor(type, setterName) { it.parameterCount == 1 && it.parameterTypes[0] == getter.returnType }
        return Property(name, getter, setter)
    }

    private fun accessor(type: Class<*>, methodName: String, predicate: (Method) -> Boolean): Method? =
        type.methods.firstOrNull {
            it.name == methodName && Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.isBridge && predicate(it)
        }

}
