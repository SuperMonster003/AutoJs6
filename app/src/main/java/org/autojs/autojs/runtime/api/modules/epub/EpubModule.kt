package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `epub` (functions with their `Async` forms; EpubError as a data property) on the new script module layer (roadmap
 * P4.1): only the mounting layer changes. Every member hands its arguments unchanged to the current
 * `augment/epub/Epub`, so values, argument errors and the module's own coded errors stay those of the current module (a
 * coded error is a script error and passes the new layer's error mapping unchanged, T5); the data properties the
 * current module computes at run time are defined on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 epub (函数及其 Async 形式; EpubError 为数据属性) (P4.1): 只换挂载层. 每个成员把参数原样交给现有
 * augment/epub/Epub, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误, 原样通过新层的错误映射, T5);
 * 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "epub")
class EpubModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Epub(runtime, ScriptRuntimeFields.epub(runtime)) }

    @ScriptInstall(names = [ScriptInstallNames(["EpubError"])])
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl.scriptProperties, it) }

    @ScriptFunction(arity = [1])
    fun open(vararg args: Any?): Any? = impl.open(runtime, args)

    @ScriptFunction(arity = [1])
    fun openAsync(vararg args: Any?): Any? = impl.openAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun metadata(vararg args: Any?): Any? = impl.metadata(runtime, args)

    @ScriptFunction(arity = [1])
    fun metadataAsync(vararg args: Any?): Any? = impl.metadataAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun toc(vararg args: Any?): Any? = impl.toc(runtime, args)

    @ScriptFunction(arity = [1])
    fun tocAsync(vararg args: Any?): Any? = impl.tocAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun readingOrder(vararg args: Any?): Any? = impl.readingOrder(runtime, args)

    @ScriptFunction(arity = [1])
    fun readingOrderAsync(vararg args: Any?): Any? = impl.readingOrderAsync(runtime, args)

    @ScriptFunction(arity = [1, 3])
    fun text(vararg args: Any?): Any? = impl.text(runtime, args)

    @ScriptFunction(arity = [1, 3])
    fun textAsync(vararg args: Any?): Any? = impl.textAsync(runtime, args)

    @ScriptFunction(arity = [1, 3])
    fun cover(vararg args: Any?): Any? = impl.cover(runtime, args)

    @ScriptFunction(arity = [1, 3])
    fun coverAsync(vararg args: Any?): Any? = impl.coverAsync(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun search(vararg args: Any?): Any? = impl.search(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun searchAsync(vararg args: Any?): Any? = impl.searchAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun annotations(vararg args: Any?): Any? = impl.annotations(runtime, args)

    @ScriptFunction(arity = [1])
    fun annotationsAsync(vararg args: Any?): Any? = impl.annotationsAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun read(vararg args: Any?): Any? = impl.read(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun readAsync(vararg args: Any?): Any? = impl.readAsync(runtime, args)

    @ScriptFunction(arity = [0])
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptFunction(arity = [0])
    fun isAvailableAsync(vararg args: Any?): Any? = impl.isAvailableAsync(runtime, args)

}
