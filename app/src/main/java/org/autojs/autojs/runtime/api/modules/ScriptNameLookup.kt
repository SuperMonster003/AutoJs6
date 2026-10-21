package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

/**
 * Resolves names that are not properties of an object (the current `SimpleGetterProxy` prototypes: the Java classes of
 * global, the constants of WebSocket). A `@ScriptPrototype` value of this type becomes a [ScriptLookupPrototype] in the
 * module object's prototype chain (on the global object for an assign module), so `Object.keys`, `hasOwnProperty` and
 * `in` see none of these names and a read resolves them each time, as the current proxy prototypes do.
 *
 * zh-CN: 解析不是对象自有属性的名称 (现有 SimpleGetterProxy 原型: global 的 Java 类, WebSocket 的常量). 类型为本接口的 @ScriptPrototype 值成为模块对象原型链中的
 * ScriptLookupPrototype (assign 模块则在全局对象的原型链中), 因此 Object.keys, hasOwnProperty 与 in 都看不到这些名称, 每次读取时解析, 与现有的代理原型相同.
 */
fun interface ScriptNameLookup {

    /** The value of [name], or `Scriptable.NOT_FOUND` when this lookup does not know it. */
    fun lookup(scope: VarScope, name: String): Any?

}

/**
 * An object without own properties whose reads fall back to a [ScriptNameLookup] (`[object Object]`, no `has`, as the
 * current `ProxyObject` with a getter only). Writes define own properties as on any object.
 *
 * zh-CN: 没有自有属性的对象, 读取未定义的名称时交给 ScriptNameLookup (`[object Object]`, 不改写 has, 与现有只有 getter 的 ProxyObject 相同); 写入照常定义自有属性.
 */
class ScriptLookupPrototype internal constructor(
    private val scope: VarScope,
    private val lookup: ScriptNameLookup,
    private val guard: (name: String, block: () -> Any?) -> Any?,
) : NativeObject() {

    init {
        RhinoUtils.initNativeObjectPrototype(scope, this)
    }

    override fun get(name: String, start: Scriptable): Any? {
        val own = super.get(name, start)
        if (own !== Scriptable.NOT_FOUND) return own
        return guard(name) { lookup.lookup(scope, name) }
    }

}
