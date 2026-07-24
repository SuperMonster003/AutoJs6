package org.autojs.autojs.runtime.api.augment.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ai.AiRequestInput
import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.toJsPromise
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined

class Ai(
    private val scriptRuntime: ScriptRuntime,
    private val aiService: AiService,
) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::ask.name,
        ::chat.name,
        ::stream.name,
        ::profiles.name,
        ::providers.name,
        ::defaultProfile.name,
        ::isConfigured.name,
    )

    override fun invoke(vararg args: Any?): Any {
        return ask(scriptRuntime, args)
    }

    @RhinoRuntimeFunctionInterface
    fun ask(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        val input = parseRequestArguments(args)
        return aiService.ask(input).toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun chat(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        val input = parseRequestArguments(args)
        return aiService.chat(input).toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun stream(runtime: ScriptRuntime, args: Array<out Any?>): AiStream {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        return aiService.stream(parseRequestArguments(args))
    }

    @RhinoRuntimeFunctionInterface
    fun profiles(runtime: ScriptRuntime, args: Array<out Any?>): List<Map<String, Any?>> {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        return ensureArgumentsIsEmpty(args, aiService::profiles)
    }

    @RhinoRuntimeFunctionInterface
    fun providers(runtime: ScriptRuntime, args: Array<out Any?>): List<Map<String, Any?>> {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        return ensureArgumentsIsEmpty(args, aiService::providers)
    }

    @RhinoRuntimeFunctionInterface
    fun defaultProfile(
        runtime: ScriptRuntime,
        args: Array<out Any?>,
    ): Map<String, Any?>? {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        return ensureArgumentsIsEmpty(args, aiService::defaultProfile)
    }

    @RhinoRuntimeFunctionInterface
    fun isConfigured(runtime: ScriptRuntime, args: Array<out Any?>): Boolean {
        check(runtime === scriptRuntime) { "AI module belongs to a different script runtime" }
        return ensureArgumentsIsEmpty(args, aiService::isConfigured)
    }

    companion object {

        internal fun parseRequestArguments(args: Array<out Any?>): AiRequestInput {
            return ensureArgumentsLengthInRange(args, 1..2) { argumentList ->
                val request = parseJson(argumentList[0], "prompt or messages")
                val trailingOptions = argumentList.getOrNull(1)
                    ?.takeUnless { it.isJsNullish() }
                    ?.let { parseObject(it, "options") }
                    ?: JsonObject()

                val messages: JsonArray
                val inlineOptions = JsonObject()
                when {
                    request.isJsonPrimitive && request.asJsonPrimitive.isString -> {
                        messages = JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("role", "user")
                                addProperty("content", request.asString)
                            })
                        }
                    }
                    request.isJsonArray -> {
                        messages = request.asJsonArray.deepCopy()
                    }
                    request.isJsonObject && request.asJsonObject.has("messages") -> {
                        val requestObject = request.asJsonObject.deepCopy()
                        val messageElement = requestObject.remove("messages")
                        require(messageElement != null && messageElement.isJsonArray) {
                            "AI request messages must be an array"
                        }
                        messages = messageElement.asJsonArray.deepCopy()
                        requestObject.entrySet().forEach { (key, value) ->
                            inlineOptions.add(key, value.deepCopy())
                        }
                    }
                    request.isJsonObject &&
                        request.asJsonObject.has("role") &&
                        request.asJsonObject.has("content") -> {
                        messages = JsonArray().apply { add(request.asJsonObject.deepCopy()) }
                    }
                    else -> throw WrappedIllegalArgumentException(
                        "AI input must be a prompt string, a message array, a message object, or a request object",
                    )
                }
                require(messages.size() > 0) { "AI messages must not be empty" }
                trailingOptions.entrySet().forEach { (key, value) ->
                    inlineOptions.add(key, value.deepCopy())
                }
                AiRequestInput(messages, inlineOptions)
            }
        }

        private fun parseObject(value: Any?, label: String): JsonObject {
            val parsed = parseJson(value, label)
            if (!parsed.isJsonObject) {
                throw WrappedIllegalArgumentException("AI $label must be a JavaScript Object")
            }
            return parsed.asJsonObject
        }

        private fun parseJson(value: Any?, label: String): JsonElement {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) {
                throw WrappedIllegalArgumentException("AI $label cannot be undefined")
            }
            return try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (error: RuntimeException) {
                throw WrappedIllegalArgumentException(
                    "AI $label must be JSON-compatible: ${error.message.orEmpty()}",
                )
            }
        }
    }
}
