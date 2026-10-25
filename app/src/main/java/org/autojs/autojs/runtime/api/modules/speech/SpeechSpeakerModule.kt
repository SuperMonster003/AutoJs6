package org.autojs.autojs.runtime.api.modules.speech

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `speech.speaker` (functions, no `$` name; 3-Macaw Speech roadmap D132, D133) on the new script module layer, mounted
 * like `speech.models` ([SpeechModelsModule]): every member hands its arguments unchanged to [SpeechSpeaker], so values,
 * argument errors and the module's coded errors are the same on both layers.
 *
 * zh-CN: 新脚本模块层上的 speech.speaker (函数, 无 $ 名称; 3-Macaw Speech 路线图 D132, D133), 挂载方式同 speech.models
 * ([SpeechModelsModule]): 每个成员把参数原样交给 [SpeechSpeaker], 因此两层的取值, 参数错误与模块带错误码的错误相同.
 */
@ScriptModule(name = "speaker", parent = SpeechModule::class, dollarPrefix = false)
class SpeechSpeakerModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { SpeechSpeaker(runtime, ScriptRuntimeFields.speech(runtime)) }

    @ScriptFunction(arity = [2, 3])
    fun enroll(vararg args: Any?): Any? = impl.enroll(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun enrollAsync(vararg args: Any?): Any? = impl.enrollAsync(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun verify(vararg args: Any?): Any? = impl.verify(runtime, args)

    @ScriptFunction(arity = [2, 3])
    fun verifyAsync(vararg args: Any?): Any? = impl.verifyAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun identify(vararg args: Any?): Any? = impl.identify(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun identifyAsync(vararg args: Any?): Any? = impl.identifyAsync(runtime, args)

    @ScriptFunction(arity = [0])
    fun list(vararg args: Any?): Any? = impl.list(runtime, args)

    @ScriptFunction(arity = [1])
    fun remove(vararg args: Any?): Any? = impl.remove(runtime, args)

}
