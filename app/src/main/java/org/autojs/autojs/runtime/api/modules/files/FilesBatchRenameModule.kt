package org.autojs.autojs.runtime.api.modules.files

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.files.BatchRename
import org.autojs.autojs.runtime.api.augment.files.BatchRenameJsErrors
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.mozilla.javascript.Context

/**
 * `files.batchRename` on the new script module layer (roadmap P3.4): the callable sub-module of `files` (no `$` name) with
 * the `BatchRenameError` constructor and the functions of the current module in its order (`apply` is the current
 * `applyPlan`), served by the current implementation (`augment/files/BatchRename`, which asks the Batch Rename plugin; to
 * be moved out before P5.2).
 *
 * The documentation makes every failure of `files.batchRename` a `BatchRenameError` (`INVALID_OPTIONS` for arguments),
 * including the argument count, so the functions take their arguments as given (`vararg`) and leave every check to the
 * current implementation instead of the TypeErrors of the generated layer; the `BatchRenameError` it throws reaches the
 * script unchanged (T5). Every function therefore has `length` 0, as before.
 *
 * zh-CN: 新脚本模块层上的 files.batchRename (P3.4): files 的可调用子模块 (无 $ 名称), 带 BatchRenameError 构造器, 函数及顺序同现有模块 (apply 即现有的
 * applyPlan), 由现有实现处理 (augment/files/BatchRename, 向 Batch Rename 插件请求; P5.2 前迁出). 文档规定 files.batchRename 的一切失败都是 BatchRenameError
 * (参数错误为 INVALID_OPTIONS), 参数个数也不例外, 因此各函数原样接收参数 (vararg), 一切检查交给现有实现, 不使用生成层的 TypeError; 其抛出的 BatchRenameError
 * 原样到达脚本 (T5). 因此各函数的 length 与以前一样为 0.
 */
@ScriptModule(name = "batchRename", parent = FilesModule::class, dollarPrefix = false)
class FilesBatchRenameModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { BatchRename(runtime) }

    /** The script's `BatchRenameError` constructor, built in the Context that builds the module object. */
    @ScriptProperty(name = "BatchRenameError")
    val errorConstructor: Any
        get() = Context.getCurrentContext()?.let { BatchRenameJsErrors.constructor(it, runtime.topLevelScope) } ?: BatchRenameJsErrors.constructor(runtime)

    /** `files.batchRename(paths, options)`: plans and applies in one step. */
    @ScriptInvoke
    fun invoke(vararg args: Any?): Any? = impl.invoke(*args)

    @ScriptFunction
    fun plan(vararg args: Any?): Any? = impl.plan(runtime, args)

    @ScriptFunction(name = "apply")
    fun applyPlan(vararg args: Any?): Any? = impl.applyPlan(runtime, args)

    @ScriptFunction
    fun undo(vararg args: Any?): Any? = impl.undo(runtime, args)

    @ScriptFunction
    fun history(vararg args: Any?): Any? = impl.history(runtime, args)

    @ScriptFunction
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptFunction
    fun planAsync(vararg args: Any?): Any = impl.planAsync(runtime, args)

    @ScriptFunction
    fun applyAsync(vararg args: Any?): Any = impl.applyAsync(runtime, args)

    @ScriptFunction
    fun undoAsync(vararg args: Any?): Any = impl.undoAsync(runtime, args)

    @ScriptFunction
    fun presets(vararg args: Any?): Any? = impl.presets(runtime, args)

    @ScriptFunction
    fun presetsAsync(vararg args: Any?): Any = impl.presetsAsync(runtime, args)

    @ScriptFunction
    fun importPresets(vararg args: Any?): Any? = impl.importPresets(runtime, args)

    @ScriptFunction
    fun importPresetsAsync(vararg args: Any?): Any = impl.importPresetsAsync(runtime, args)

    @ScriptFunction
    fun preview(vararg args: Any?): Any? = impl.preview(runtime, args)

    @ScriptFunction
    fun previewAsync(vararg args: Any?): Any = impl.previewAsync(runtime, args)

    @ScriptFunction
    fun deleteHistory(vararg args: Any?): Any? = impl.deleteHistory(runtime, args)

    @ScriptFunction
    fun clearHistory(vararg args: Any?): Any? = impl.clearHistory(runtime, args)

}
