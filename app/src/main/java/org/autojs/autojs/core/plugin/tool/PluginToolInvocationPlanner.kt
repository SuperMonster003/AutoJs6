package org.autojs.autojs.core.plugin.tool

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.autojs.plugin.tool.api.PluginToolErrorCodes
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolValues

/** One image or file argument the host resolves into a descriptor; [value] is `"screen"` (images only) or a path. */
data class PluginToolInput(val parameter: String, val type: String, val value: String) {
    val isScreen: Boolean get() = type == PluginToolValues.SPECIAL_TYPE_IMAGE && value == PluginToolValues.IMAGE_SCREEN
}

/**
 * A call ready to run: [arguments] validated with defaults filled and without the image and file arguments, the
 * [inputs] to resolve, the declared [outputs] the caller wants written (output name to path) and the time limit.
 */
data class PluginToolInvocationPlan(
    val tool: PluginToolDefinition,
    val arguments: JsonObject,
    val inputs: List<PluginToolInput>,
    val outputs: Map<String, String>,
    val timeoutMs: Long,
)

sealed class PluginToolPlanResult {

    data class Ready(val plan: PluginToolInvocationPlan) : PluginToolPlanResult()

    data class Invalid(val message: String) : PluginToolPlanResult()
}

/**
 * Turns one `plugin_tools.invoke` request into a [PluginToolInvocationPlan] (MCP Server roadmap P12 D31 / D32,
 * docs/dev/plugin-tool-protocol-v1.md): the arguments must match the tool's schema (PluginToolSchema), absent defaults
 * are filled, image and file arguments move into [PluginToolInput]s, requested outputs must be declared by the tool,
 * and the time limit is the tool's, shortened by the caller and by the bridge deadline. Pure Kotlin on top of Gson.
 *
 * zh-CN: 把一次 `plugin_tools.invoke` 请求转换为调用计划: 参数须符合工具 schema, 填入缺省默认值, 图片与文件参数移入
 * [PluginToolInput], 请求的输出须由工具声明, 时限取工具声明值并受调用方与桥接截止时间约束.
 */
object PluginToolInvocationPlanner {

    private const val MAX_REPORTED_PROBLEMS = 5

    /**
     * @param arguments the `arguments` value of the request (an object, or absent / null for none).
     * @param outputs the `outputs` option (output name to working-directory path), or null.
     * @param requestedTimeoutMs the caller's `timeoutMs` option, or null.
     * @param ceilingMs the longest the bridge may wait for the result.
     */
    fun plan(tool: PluginToolDefinition, arguments: JsonElement?, outputs: JsonObject?, requestedTimeoutMs: Long?, ceilingMs: Long): PluginToolPlanResult {
        val given = when (arguments) {
            null, is JsonNull -> JsonObject()
            is JsonObject -> arguments
            else -> return invalid("arguments must be an object")
        }
        val size = given.toString().toByteArray(Charsets.UTF_8).size
        if (size > PluginToolLimits.MAX_ARGUMENTS_BYTES) return invalid("arguments have $size bytes, more than ${PluginToolLimits.MAX_ARGUMENTS_BYTES}")
        val problems = PluginToolSchema.validate(tool.inputSchema, given)
        if (problems.isNotEmpty()) {
            val shown = problems.take(MAX_REPORTED_PROBLEMS).joinToString("; ")
            return invalid("arguments do not match the schema of ${tool.fullName}: $shown" + if (problems.size > MAX_REPORTED_PROBLEMS) "; ..." else "")
        }
        val filled = PluginToolSchema.withDefaults(tool.inputSchema, given)

        val inputs = mutableListOf<PluginToolInput>()
        for ((parameter, type) in tool.specialParameters) {
            val value = filled.remove(parameter) ?: continue
            if (value.isJsonNull) continue
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim().orEmpty()
            if (text.isEmpty()) return invalid("$parameter must name ${if (type == PluginToolValues.SPECIAL_TYPE_IMAGE) "\"screen\" or a path" else "a path"}")
            if (type == PluginToolValues.SPECIAL_TYPE_FILE && text == PluginToolValues.IMAGE_SCREEN) return invalid("$parameter is a file parameter and takes a path, not \"screen\"")
            inputs += PluginToolInput(parameter, type, text)
        }

        val wanted = LinkedHashMap<String, String>()
        outputs?.entrySet()?.forEach { (name, value) ->
            if (tool.outputs.none { it.name == name }) {
                val declared = tool.outputs.joinToString { it.name }.ifEmpty { "none" }
                return invalid("${tool.fullName} declares no output named $name (declared: $declared)")
            }
            val path = (value as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim().orEmpty()
            if (path.isEmpty()) return invalid("output $name needs a path")
            wanted[name] = path
        }

        val timeout = listOfNotNull(tool.timeoutMs, requestedTimeoutMs?.takeIf { it > 0 }, ceilingMs).min()
        if (timeout < 1) return invalid("no time is left for ${tool.fullName}")
        return PluginToolPlanResult.Ready(PluginToolInvocationPlan(tool, filled, inputs, wanted, timeout))
    }

    /** How a provider error code maps onto the bridge error categories (NodeBridgeErrorCategory). */
    fun bridgeCategoryOf(code: String): String = when (code) {
        PluginToolErrorCodes.INVALID_ARGUMENTS, PluginToolErrorCodes.UNKNOWN_TOOL, PluginToolErrorCodes.INPUT_UNREADABLE -> "invalid-request"
        PluginToolErrorCodes.BUSY -> "rate-limited"
        PluginToolErrorCodes.LIMIT_EXCEEDED -> "resource-limit"
        PluginToolErrorCodes.TIMEOUT -> "timeout"
        PluginToolErrorCodes.NOT_READY -> "unavailable"
        else -> "provider-failed"
    }

    private fun invalid(message: String) = PluginToolPlanResult.Invalid(message)
}
