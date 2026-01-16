package org.autojs.autojs.runtime.api.augment.proxy

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import java.util.concurrent.ConcurrentHashMap

/**
 * A generic composition-based Java proxy for Rhino.
 * Key rules for a correct proxy:
 * 1) Implement Wrapper so Rhino can unwrap for instanceof and overload resolution.
 * 2) Bind functions so that "this" is the underlying Java wrapper, not the proxy.
 * 3) Intercept only what you must, forward everything else.
 *
 * zh-CN:
 *
 * 一个通用的组合式 Java 代理对象, 用于 Rhino.
 * 正确代理对象的关键要素:
 * 1. 实现 Wrapper, 让 Rhino 可用于 instanceof 与方法重载匹配.
 * 2. 绑定函数, 确保调用时 this 为底层 Java wrapper, 而非代理对象.
 * 3. 只拦截必要行为, 其它全部转发.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Jan 16, 2026.
 */
open class JavaProxyObject<T : Any>(
    scope: Scriptable,
    protected val base: Scriptable,
    protected val target: T,
) : ScriptableObject(), Wrapper {

    // Cache bound functions to reduce allocations on hot paths.
    // zh-CN: 缓存绑定后的函数, 以减少热路径的对象分配.
    private val functionMap: ConcurrentHashMap<String, Function> = ConcurrentHashMap()

    init {
        parentScope = scope
    }

    override fun getClassName(): String = "JavaProxyObject"

    // Expose underlying Java object to Rhino for instanceof and method overload resolution.
    // zh-CN: 向 Rhino 暴露底层 Java 对象, 用于 instanceof 与方法重载匹配.
    override fun unwrap(): Any = target

    override fun get(name: String, start: Scriptable): Any {
        // Allow subclasses to intercept member access.
        // zh-CN: 允许子类拦截成员访问.
        getIntercepted(name)?.let { return it }

        // Try to fetch from the underlying Java wrapper first.
        // zh-CN: 优先从底层 Java wrapper 获取成员.
        val v = base.get(name, base)
        if (v != Scriptable.NOT_FOUND) {
            // Fast path: cached function.
            // zh-CN: 快速路径: 命中函数缓存.
            if (v is Function) {
                functionMap[name]?.let { return it }
            }

            val bound = bindIfFunction(v)
            if (bound is Function) {
                functionMap.putIfAbsent(name, bound)
            }
            return bound
        }

        // Fallback to ScriptableObject default.
        // zh-CN: 兜底回退到 ScriptableObject 默认行为.
        return super.get(name, start)
    }

    override fun put(name: String, start: Scriptable, value: Any?) {
        // Allow subclasses to intercept member writes.
        // zh-CN: 允许子类拦截成员写入.
        if (putIntercepted(name, value)) return

        // Forward property sets to the underlying Java wrapper.
        // zh-CN: 将属性写入转发到底层 Java wrapper.
        base.put(name, base, value)
    }

    override fun has(name: String, start: Scriptable): Boolean {
        // Ensure intercepted members are visible to JS (e.g. setColor).
        // zh-CN: 确保被拦截的成员对 JS 可见 (例如 setColor).
        if (hasIntercepted(name)) return true
        return base.has(name, base) || super.has(name, start)
    }

    override fun getDefaultValue(typeHint: Class<*>?): Any {
        // Delegate to underlying wrapper for meaningful stringification.
        // zh-CN: 委托到底层 wrapper, 以获得更有意义的字符串化结果.
        return base.getDefaultValue(typeHint)
    }

    override fun toString(): String {
        // Make log(proxy) look like a Java object.
        // zh-CN: 让 log(proxy) 更像 JavaObject 的输出.
        return target.toString()
    }

    /**
     * Subclass hook: intercept read access for a property/method name.
     * zh-CN: 子类钩子: 拦截某个属性/方法名的读取访问.
     */
    protected open fun getIntercepted(name: String): Any? = null

    /**
     * Subclass hook: intercept write access for a property name.
     * Return true if handled.
     * zh-CN:
     * 子类钩子: 拦截某个属性名的写入访问.
     * 若已处理则返回 true.
     */
    protected open fun putIntercepted(name: String, value: Any?): Boolean = false

    /**
     * Subclass hook: declare intercepted members as existing.
     * zh-CN: 子类钩子: 声明被拦截成员存在.
     */
    protected open fun hasIntercepted(name: String): Boolean = false

    private fun bindIfFunction(v: Any): Any {
        // Bind functions so that "this" is always the underlying Java wrapper.
        // zh-CN: 绑定函数, 让 this 始终为底层 Java wrapper.
        if (v !is Function) return v
        return object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                return v.call(cx, scope, base, args)
            }
        }
    }

}
