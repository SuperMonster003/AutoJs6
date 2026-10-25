package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `speech` (functions; SpeechError as a data property; sub-module `models`, [SpeechModelsModule]) on the new script
 * module layer (roadmap P4.1): only the mounting layer changes. Every member hands its arguments unchanged to the
 * current `augment/speech/Speech`, so values, argument errors and the module's own coded errors stay those of the
 * current module (a coded error is a script error and passes the new layer's error mapping unchanged, T5); the data
 * properties the current module computes at run time are defined on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 speech (函数; SpeechError 为数据属性; 子模块 models, 见 [SpeechModelsModule]) (P4.1):
 * 只换挂载层. 每个成员把参数原样交给现有 augment/speech/Speech, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误,
 * 原样通过新层的错误映射, T5); 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "speech")
class SpeechModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Speech(runtime, ScriptRuntimeFields.speech(runtime), ScriptRuntimeFields.tts(runtime)) }

    @ScriptInstall(names = [ScriptInstallNames(["SpeechError"])])
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl.scriptProperties, it) }

    @ScriptFunction(arity = [0])
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptFunction(arity = [0])
    fun capabilities(vararg args: Any?): Any? = impl.capabilities(runtime, args)

    @ScriptFunction(arity = [0, 1])
    fun listen(vararg args: Any?): Any? = impl.listen(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun recognize(vararg args: Any?): Any? = impl.recognize(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun recognizeAsync(vararg args: Any?): Any? = impl.recognizeAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun detectVoice(vararg args: Any?): Any? = impl.detectVoice(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun wake(vararg args: Any?): Any? = impl.wake(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun synthesize(vararg args: Any?): Any? = impl.synthesize(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun synthesizeAsync(vararg args: Any?): Any? = impl.synthesizeAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun speak(vararg args: Any?): Any? = impl.speak(runtime, args)

}
