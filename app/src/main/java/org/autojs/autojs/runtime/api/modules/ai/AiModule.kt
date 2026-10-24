package org.autojs.autojs.runtime.api.modules.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined

/**
 * `ai` on the new script module layer (roadmap P4.1): `ai(prompt, options?)` is `ai.ask(prompt, options?)`, with chat /
 * stream / session / catalog and the getter `agent` of the current `augment/ai/Ai` (requests through `AiService`, results
 * as Promises / AiStream / the current agent object, built on first read).
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. The
 * argument shapes the current module rejects are checked here first, on the JSON of the values as the current module
 * reads them (D10, IllegalArgumentException in the current module): a value without JSON (a function, undefined) is a
 * TypeError; a prompt whose JSON is not a string, a message array, a message object (`role` and `content`) or a request
 * object (`messages` an array) is a TypeError and an empty message array a RangeError; options whose JSON is not an
 * object are a TypeError; the `system` option of `session` must be a string (TypeError) that is not empty (RangeError).
 * The request itself stays the current module's.
 * Once the arguments are accepted, a failure of the current module (the plugin not installed or disabled among them)
 * is rethrown as the current augment layer does (`AugmentMembers.asCurrent`): the same InternalError in both states.
 *
 * zh-CN: 新脚本模块层上的 ai (P4.1): ai(prompt, options?) 即 ai.ask(prompt, options?), 另有现有 augment/ai/Ai 的 chat / stream / session / catalog
 * 与 getter agent (请求经 AiService, 结果为 Promise / AiStream / 现有 agent 对象, 首次读取时构建). 参数按真实签名 (T4): 缺少或多余参数为生成层的
 * TypeError. 现有模块拒绝的参数形状在此先行检查, 与现有模块一样读取值的 JSON (D10, 现有模块均为 IllegalArgumentException): 没有 JSON 的值 (函数,
 * undefined) 为 TypeError; JSON 既不是字符串, 消息数组, 消息对象 (role 与 content) 也不是请求对象 (messages 为数组) 的 prompt 为 TypeError, 空的消息
 * 数组为 RangeError; JSON 不是对象的选项为 TypeError; session 的 system 选项须为字符串 (TypeError) 且非空 (RangeError). 请求本身仍由现有模块完成.
 * 参数通过检查后, 现有模块的失败 (含插件未安装或未启用) 按现有 augment 层的方式重新抛出 (AugmentMembers.asCurrent), 两态为同一个 InternalError.
 */
@ScriptModule(name = "ai")
class AiModule(private val runtime: ScriptRuntime) {

    private val ai by lazy { Ai(runtime, ScriptRuntimeFields.ai(runtime)) }

    @ScriptInvoke
    fun invoke(prompt: Any?, options: Any? = null): Any {
        checkRequest(MODULE, prompt, options)
        return AugmentMembers.asCurrent(ai, null) { ai.ask(runtime, arrayOf(prompt, options)) }
    }

    @ScriptFunction
    fun ask(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_ASK, prompt, options)
        return AugmentMembers.asCurrent(ai, "ask") { ai.ask(runtime, arrayOf(prompt, options)) }
    }

    @ScriptFunction
    fun chat(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_CHAT, prompt, options)
        return AugmentMembers.asCurrent(ai, "chat") { ai.chat(runtime, arrayOf(prompt, options)) }
    }

    @ScriptFunction
    fun stream(prompt: Any?, options: Any? = null): Any {
        checkRequest(FUNCTION_STREAM, prompt, options)
        return AugmentMembers.asCurrent(ai, "stream") { ai.stream(runtime, arrayOf(prompt, options)) }
    }

    @ScriptFunction
    fun session(options: Any? = null): Any {
        checkOptions(FUNCTION_SESSION, options)?.get(SYSTEM)?.let { system ->
            if (!system.isJsonPrimitive || !system.asJsonPrimitive.isString) {
                throw UtilValues.typeError(FUNCTION_SESSION, ScriptMessages.argumentType("options.$SYSTEM", "string", system.toString()))
            }
            if (system.asString.isEmpty()) throw UtilValues.rangeError(FUNCTION_SESSION, ScriptMessages.argumentCannotBe("options.$SYSTEM", "\"\""))
        }
        return AugmentMembers.asCurrent(ai, "session") { ai.session(runtime, arrayOf(options)) }
    }

    @ScriptFunction
    fun catalog(options: Any? = null): Any {
        checkOptions(FUNCTION_CATALOG, options)
        return AugmentMembers.asCurrent(ai, "catalog") { ai.catalog(runtime, arrayOf(options)) }
    }

    @ScriptGetter
    fun agent(): Any? = AugmentMembers.getter(ai, "agent")

    // The checks read the JSON of the values, as the current module does (`JSON.stringify`: own enumerable properties,
    // toJSON, primitive wrappers), in its order: the prompt is serialized, then the options are checked, then the shape.
    // zh-CN: 检查读取值的 JSON, 同现有模块 (JSON.stringify: 自有可枚举属性, toJSON, 原始值包装对象), 顺序也相同: 先序列化 prompt, 再检查选项, 再判断形状.
    private fun checkRequest(function: String, prompt: Any?, options: Any?) {
        val request = json(function, "prompt", prompt)
        checkOptions(function, options)
        when {
            request.isJsonPrimitive && request.asJsonPrimitive.isString -> Unit
            request.isJsonArray -> if (request.asJsonArray.isEmpty) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("prompt", "[]"))
            request.isJsonObject && request.asJsonObject.has(MESSAGES) -> {
                val messages = request.asJsonObject.get(MESSAGES)
                if (!messages.isJsonArray) throw UtilValues.typeError(function, ScriptMessages.argumentType("prompt.$MESSAGES", "Array", messages.toString()))
                if (messages.asJsonArray.isEmpty) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("prompt.$MESSAGES", "[]"))
            }
            request.isJsonObject && request.asJsonObject.has("role") && request.asJsonObject.has("content") -> Unit
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("prompt", PROMPT_TYPES, ScriptArgs.describe(prompt)))
        }
    }

    /** The JSON of [options], null when they are not given; options that are given must be an object. */
    private fun checkOptions(function: String, options: Any?): JsonObject? {
        if (ScriptArgs.isNullish(options)) return null
        val value = json(function, "options", options)
        if (!value.isJsonObject) throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
        return value.asJsonObject
    }

    /** The JSON of [value] unwrapped as by the current module's argument guard (a TypeError when it has none, as for a function or undefined). */
    private fun json(function: String, param: String, value: Any?): JsonElement {
        val serialized = RhinoUtils.js_json_stringify(RhinoUtils.unwrap(value))
        val parsed = if (serialized is Undefined) null else runCatching { JsonParser.parseString(Context.toString(serialized)) }.getOrNull()
        return parsed ?: throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "JSON value", ScriptArgs.describe(value)))
    }

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
