package org.autojs.autojs.runtime.api.modules.continuation

import org.autojs.autojs.project.ScriptConfig.Companion.FEATURE_CONTINUATION
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.continuation.Creator
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.mozilla.javascript.Scriptable

/**
 * The `continuation` module on the new script module layer (roadmap P3.4): read-only names as with the current module
 * (`augment(target, ..., READONLY)`), `create(scope?)` returning the continuation control object and the `enabled`
 * getter; the prototype is the runtime's `js_mod_continuation` ([org.autojs.autojs.runtime.api.modules.bootstrap.ContinuationPrototype],
 * P2.5) with `await(promise)` and `delay(millis)`. A scope that is not a script object is a TypeError (D10; the current
 * module ignored it and used the top-level scope).
 *
 * zh-CN: 新脚本模块层上的 continuation 模块 (P3.4): 与现有模块一样名称只读, create(scope?) 返回续体控制对象, 另有 getter enabled; 原型是运行时的
 * js_mod_continuation (ContinuationPrototype, P2.5), 提供 await(promise) 与 delay(millis). scope 不是脚本对象时为 TypeError (D10; 现有模块忽略它并使用顶层作用域).
 */
@ScriptModule(name = "continuation", attributes = ScriptAttributes.PERMANENT or ScriptAttributes.READONLY)
class ContinuationModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.js_mod_continuation

    @ScriptFunction
    fun create(scope: Scriptable? = null): Creator = Creator(runtime, scope)

    /** Whether the current engine runs with the `continuation` feature. */
    @ScriptGetter
    val enabled: Boolean
        get() = runtime.engines.myEngine().hasFeature(FEATURE_CONTINUATION)

}
