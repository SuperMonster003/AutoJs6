package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope

/**
 * What the KSP processor generates for every @ScriptModule class (`org.autojs.autojs.runtime.api.modules.generated`):
 * the mount table ([spec]) and the dispatch of calls, getters and setters by member id. No reflection is involved; the
 * generated code calls the module's Kotlin functions directly (roadmap D5).
 *
 * zh-CN: KSP 处理器为每个 @ScriptModule 类生成的绑定: 挂载表 ([spec]) 与按成员编号分发的调用, getter 与 setter. 不使用反射, 生成代码直接调用
 * 模块的 Kotlin 函数 (D5).
 */
interface ScriptModuleBinding<M : Any> {

    val spec: ScriptModuleSpec

    /** The module instance: the object itself, or a new instance for the given runtime. */
    fun create(runtime: ScriptRuntime?): M

    /** Calls function [id] of [spec], or the module itself ([INVOKE]) or its constructor ([CONSTRUCT]). */
    fun call(module: M, id: Int, cx: Context, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any?

    /** Reads accessor [id] of [spec] (getters on every read, properties once at mount). */
    fun get(module: M, id: Int, scope: VarScope): Any?

    /** Writes accessor [id] of [spec] if it is writable. */
    fun set(module: M, id: Int, value: Any?)

    /** The prototype of the module object, or null to keep the default one. */
    fun prototype(module: M): Any?

    companion object {
        const val INVOKE = -1
        const val CONSTRUCT = -2
    }

}

/**
 * The mount table of a module. It only holds plain values (no annotation types), so the annotations module stays a
 * compile-time dependency.
 */
class ScriptModuleSpec(
    val name: String,
    val aliases: List<String>,
    val dollarPrefix: Boolean,
    val eager: Boolean,
    /** Qualified class name of the parent module, or null for a module on the global object. */
    val parent: String?,
    /** JS `length` of the callable module object, or -1 when the module object is not callable. */
    val invokeLength: Int,
    /** JS `length` of the constructor, or -1 when the module object is not a constructor. */
    val constructLength: Int,
    val functions: List<ScriptFunctionSpec>,
    val accessors: List<ScriptAccessorSpec>,
    val javaClasses: List<ScriptJavaClassSpec>,
) {
    /** Every name the module object is bound to: the name and the aliases, each with its `$` form if [dollarPrefix]. */
    val names: List<String> = (listOf(name) + aliases).flatMap { if (dollarPrefix) listOf(it, "\$$it") else listOf(it) }
}

class ScriptFunctionSpec(
    val id: Int,
    val name: String,
    val aliases: List<String>,
    val onModule: Boolean,
    val onGlobal: Boolean,
    val attributes: Int,
    val length: Int,
)

class ScriptAccessorSpec(
    val id: Int,
    val name: String,
    /** True for an accessor property read on every access, false for a data property read once at mount. */
    val getter: Boolean,
    val writable: Boolean,
    val onModule: Boolean,
    val onGlobal: Boolean,
    val attributes: Int,
)

class ScriptJavaClassSpec(
    val name: String,
    val type: Class<*>,
    val onModule: Boolean,
    val onGlobal: Boolean,
    val attributes: Int,
)
