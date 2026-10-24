package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ai.Ai
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * `ai` on the new script module layer (roadmap P4.1): `ai(prompt, options?)` is `ai.ask(prompt, options?)`, with chat /
 * stream / session / catalog and the getter `agent` of the current `augment/ai/Ai` (requests through `AiService`, results
 * as Promises / AiStream / the current agent object, built on first read).
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. The
 * argument shapes the current module rejects are checked here first (D10, IllegalArgumentException in the current
 * module): a prompt that is not a string, a message array, a message object (`role` and `content`) or a request object
 * (`messages` an array) is a TypeError and an empty message array a RangeError; options that are not an object are a
 * TypeError; the `system` option of `session` must be a string (TypeError) that is not empty (RangeError). The JSON
 * conversion of the values and the request itself stay the current module's.
 *
 * zh-CN: 新脚本模块层上的 ai (P4.1): ai(prompt, options?) 即 ai.ask(prompt, options?), 另有现有 augment/ai/Ai 的 chat / stream / session / catalog
 * 与 getter agent (请求经 AiService, 结果为 Promise / AiStream / 现有 agent 对象, 首次读取时构建). 参数按真实签名 (T4): 缺少或多余参数为生成层的
 * TypeError. 现有模块拒绝的参数形状在此先行检查 (D10, 现有模块均为 IllegalArgumentException): prompt 既不是字符串, 消息数组, 消息对象 (role 与 content)
 * 也不是请求对象 (messages 为数组) 为 TypeError, 空的消息数组为 RangeError; 不是对象的选项为 TypeError; session 的 system 选项须为字符串 (TypeError)
 * 且非空 (RangeError). 值的 JSON 转换与请求本身仍由现有模块完成.
 */
@ScriptModule(name = "ai")
class AiModule(private val runtime: ScriptRuntime) {

    private val ai by lazy { Ai(runtime, ScriptRuntimeFields.ai(runtime)) }

    @ScriptInvoke
    fun invoke(prompt: Any?, options: Any? = null): Any {
        checkRequest(MODULE, prompt, options)
        return ai.ask(runtime, arrayOf(prompt, options))
    }

    @ScriptFunction
    fun ask(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_ASK, prompt, options)
        return ai.ask(runtime, arrayOf(prompt, options))
    }

    @ScriptFunction
    fun chat(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_CHAT, prompt, options)
        return ai.chat(runtime, arrayOf(prompt, options))
    }

    @ScriptFunction
    fun stream(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_STREAM, prompt, options)
        return ai.stream(runtime, arrayOf(prompt, options))
    }

    @ScriptFunction
    fun session(options: Any? = null): Any {
        if (checkOptions(FUNCTION_SESSION, options)) {
            val system = ScriptableObject.getProperty(options as Scriptable, SYSTEM)
            if (system !== Scriptable.NOT_FOUND && !ScriptArgs.isNullish(system)) {
                if (system !is CharSequence) throw UtilValues.typeError(FUNCTION_SESSION, ScriptMessages.argumentType("options.$SYSTEM", "string", ScriptArgs.describe(system)))
                if (system.isEmpty()) throw UtilValues.rangeError(FUNCTION_SESSION, ScriptMessages.argumentCannotBe("options.$SYSTEM", UtilValues.quote(system)))
            }
        }
        return ai.session(runtime, arrayOf(options))
    }

    @ScriptFunction
    fun catalog(options: Any? = null): Any {
        checkOptions(FUNCTION_CATALOG, options)
        return ai.catalog(runtime, arrayOf(options))
    }

    @ScriptGetter
    fun agent(): Any? = AugmentMembers.getter(ai, "agent")

    private fun checkRequest(function: String, prompt: Any?, options: Any?) {
        when {
            prompt is CharSequence -> Unit
            prompt is NativeArray -> if (prompt.length == 0L) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("prompt", "[]"))
            isObject(prompt) -> {
                val request = prompt as Scriptable
                val messages = ScriptableObject.getProperty(request, MESSAGES)
                when {
                    messages !== Scriptable.NOT_FOUND && messages !is Undefined -> {
                        if (messages !is NativeArray) throw UtilValues.typeError(function, ScriptMessages.argumentType("prompt.$MESSAGES", "Array", ScriptArgs.describe(messages)))
                        if (messages.length == 0L) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("prompt.$MESSAGES", "[]"))
                    }
                    ScriptableObject.hasProperty(request, "role") && ScriptableObject.hasProperty(request, "content") -> Unit
                    else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("prompt", PROMPT_TYPES, ScriptArgs.describe(prompt)))
                }
            }
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("prompt", PROMPT_TYPES, ScriptArgs.describe(prompt)))
        }
        checkOptions(function, options)
    }

    /** Whether [options] is given; options that are given must be an object. */
    private fun checkOptions(function: String, options: Any?): Boolean {
        if (ScriptArgs.isNullish(options)) return false
        if (!isObject(options)) throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
        return true
    }

    private fun isObject(value: Any?) = value is Scriptable && value !is NativeArray && value !is Callable

    private companion object {
        const val MODULE = "ai"
        const val FUNCTION_ASK = "ai.ask"
        const val FUNCTION_CHAT = "ai.chat"
        const val FUNCTION_STREAM = "ai.stream"
        const val FUNCTION_SESSION = "ai.session"
        const val FUNCTION_CATALOG = "ai.catalog"
        const val MESSAGES = "messages"
        const val SYSTEM = "system"
        const val PROMPT_TYPES = "string, message array, message object or request object"
    }

}
