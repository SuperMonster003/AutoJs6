package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.speech.SpeechModels
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `speech.models` (functions, no `$` name) on the new script module layer (roadmap P4.1): only the mounting layer
 * changes. Every member hands its arguments unchanged to the current `augment/speech/SpeechModels`, so values, argument
 * errors and the module's own coded errors stay those of the current module (a coded error is a script error and passes
 * the new layer's error mapping unchanged, T5); the data properties the current module computes at run time are defined
 * on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 speech.models (函数, 无 $ 名称) (P4.1): 只换挂载层. 每个成员把参数原样交给现有
 * augment/speech/SpeechModels, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误, 原样通过新层的错误映射,
 * T5); 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "models", parent = SpeechModule::class, dollarPrefix = false)
class SpeechModelsModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { SpeechModels(runtime, ScriptRuntimeFields.speech(runtime)) }

    @ScriptFunction(arity = [0, 1])
    fun list(vararg args: Any?): Any? = impl.list(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun install(vararg args: Any?): Any? = impl.install(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun installAsync(vararg args: Any?): Any? = impl.installAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun importFile(vararg args: Any?): Any? = impl.importFile(runtime, args)

    @ScriptFunction(arity = [1])
    fun remove(vararg args: Any?): Any? = impl.remove(runtime, args)

    @ScriptFunction(arity = [2])
    fun setDefault(vararg args: Any?): Any? = impl.setDefault(runtime, args)

    @ScriptFunction(arity = [1])
    fun getDefault(vararg args: Any?): Any? = impl.getDefault(runtime, args)

    @ScriptFunction(arity = [0])
    fun refreshCatalog(vararg args: Any?): Any? = impl.refreshCatalog(runtime, args)

}
