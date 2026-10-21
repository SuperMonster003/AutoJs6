package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/**
 * The module object of a proxy module (`@ScriptModule(proxy = true)`, the current `AugmentableProxy.proxying` of ui /
 * console): no own properties, `[object Object]` with `Object.prototype` as prototype, reads and writes of names it
 * does not hold go to the module's handlers. Without a `@ScriptProxyGet` function a read looks the name up among the
 * members ([member]); without a `@ScriptProxySet` function a write defines the name on the proxy, as `ProxyObject`
 * without a setter does. It is a [ProxyObject], so code that recognizes the current proxies (the `inspect` of util and
 * console, which inspect [members] through `__augmented__`) treats it the same.
 *
 * zh-CN: 代理模块的模块对象 (@ScriptModule(proxy = true), 即现有 ui / console 的 AugmentableProxy.proxying): 没有自有属性, `[object Object]`, 原型为
 * Object.prototype, 读写自身没有的名称时交给模块的处理函数. 没有 @ScriptProxyGet 函数时按名在成员中查找 ([member]); 没有 @ScriptProxySet 函数时写入的名称定义在
 * 代理上 (与没有 setter 的 ProxyObject 相同). 它是 ProxyObject, 识别现有代理的代码 (util 与 console 的 inspect 经 `__augmented__` 检查 [members]) 照旧适用.
 */
class ScriptProxyObject internal constructor(
    scope: VarScope,
    /** The object holding the module's members (functions, properties, getters, Java classes, sub-modules, prototypes). */
    val members: ScriptableObject,
    private val getter: ((members: Scriptable, name: String) -> Any?)?,
    private val setter: ((members: Scriptable, name: String, value: Any?) -> Unit)?,
) : ProxyObject() {

    init {
        RhinoUtils.initNativeObjectPrototype(scope, this)
    }

    override fun get(key: String, start: Scriptable): Any? {
        val own = getWithoutProxy(key, start)
        if (own !== NOT_FOUND) return own
        if (key == AUGMENTED_OBJECT_KEY) return members
        return if (getter != null) getter.invoke(members, key) else member(members, key)
    }

    override fun put(key: String, start: Scriptable, value: Any?) {
        when {
            setter != null -> setter.invoke(members, key, value)
            // ProxyObject.put takes "__proxy__" as new handlers; this proxy keeps its own and stores the value.
            key == PROXY_OBJECT_KEY -> defineProperty(key, value, EMPTY)
            else -> super.put(key, start, value)
        }
    }

    companion object {

        /**
         * The value of [name] among [members], as the current `AugmentableProxy.getCore`: a function found on the
         * members' prototype chain (not an own member) is bound to [members], so `let { log } = console` keeps working;
         * `Scriptable.NOT_FOUND` when there is no such name. Without a current Context the function is returned unbound.
         */
        @JvmStatic
        fun member(members: Scriptable, name: String): Any? {
            val value = ScriptableObject.getProperty(members, name)
            if (value !is Callable || members.has(name, members) || !ScriptableObject.hasProperty(members, name)) return value
            val cx = Context.getCurrentContext() ?: return value
            return BoundFunction(cx, members, value, members, emptyArray())
        }

    }

}
