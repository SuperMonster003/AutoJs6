package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.VarScope

/**
 * What a module's `@ScriptInstall` function receives when the module is installed on a runtime: the means to declare
 * what the annotations cannot (names computed at run time, start-up side effects, links between modules) while keeping
 * the rules of the layer: names stay lazy (T3), members map their errors (T5) and nothing enters a Context (T6).
 *
 * zh-CN: 模块的 @ScriptInstall 函数在模块安装到运行时时收到的对象, 用来表达注解无法声明的内容 (运行时计算的名称, 启动副作用, 模块之间的关联), 同时保持本层的规则:
 * 名称照旧按需构建 (T3), 成员的错误照旧映射 (T5), 不进入 Context (T6).
 */
class ScriptModuleInstallation internal constructor(
    private val mounter: ScriptModuleMounter,
    /** The script path of the module being installed (`selector`, `ocr.mlkit`). */
    val path: String,
    private val topLevel: String,
    val runtime: ScriptRuntime?,
    val scope: VarScope,
    val global: ScriptableObject,
) {

    internal val builtActions = mutableListOf<(ScriptableObject) -> Unit>()

    /**
     * Defines [name] on [target] (the global object by default) as a lazy data property with the final [attributes]:
     * visible to `Object.keys` / `hasOwnProperty` / `in` at once, built by [builder] on the first read of its value or
     * descriptor (T3). What the builder throws is mapped as for a member of this module.
     */
    @JvmOverloads
    fun defineLazy(name: String, attributes: Int, target: ScriptableObject = global, builder: () -> Any?) {
        LazyDataSlot.define(target, name, attributes) { guarded(name, builder) }
    }

    /**
     * A script function [name] of this module written by hand, with the error mapping of generated members (T5): the
     * body receives the Rhino call as is, its result goes to the script unchanged.
     */
    fun function(name: String, length: Int, body: (cx: Context, scope: VarScope, thisObj: Any?, args: Array<out Any?>) -> Any?): LambdaFunction =
        LambdaFunction(scope, name, length, SerializableCallable { cx, s, thisObj, args -> guarded(name) { body(cx, s, thisObj, args) } }, false)

    /**
     * Runs [action] right after the module object is built, before any script reads it, with the object holding the
     * module's members (the module object itself, or for a proxy module the object the proxy reads from). An eager
     * module is built at install, after this installation.
     */
    fun onBuilt(action: (members: ScriptableObject) -> Unit) {
        builtActions += action
    }

    /** The module object of the installed module at [path] (`autojs`, `ocr.mlkit`), built if needed; null if not installed. */
    fun moduleObject(path: String): ScriptableObject? = mounter.moduleObject(path)

    /** Runs [block] for member [name] of this module, mapping what it throws (T5). */
    fun <T> guarded(name: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw ScriptErrorMapper.map(e, "$path.$name", topLevel, mounter.debugConsoleForHooks)
    }

}
