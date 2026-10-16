package org.autojs.autojs.core.plugin.tool

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.plugin.tool.api.PluginToolCatalogJson
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolNames
import org.autojs.plugin.tool.api.PluginToolValues

/**
 * One output a tool declares (docs/dev/plugin-tool-protocol-v1.md): a file returned as a descriptor, or a directory the
 * plugin fills through an IPluginToolOutputTree (MCP roadmap D53), whose [mimeType] is `inode/directory`.
 */
data class PluginToolOutput(val name: String, val mimeType: String, val description: String?, val kind: String = PluginToolValues.OUTPUT_KIND_FILE) {
    val isDirectory: Boolean get() = kind == PluginToolValues.OUTPUT_KIND_DIRECTORY
}

/**
 * One usable tool of an accepted catalog; [fullName] is what consumers see, [specialParameters] maps the image and file
 * parameters the host resolves to their special type (PluginToolSchema.specialParameters).
 */
data class PluginToolDefinition(
    val name: String,
    val fullName: String,
    val title: String?,
    val description: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject?,
    val risk: String,
    val timeoutMs: Long,
    val maxConcurrency: Int,
    val outputs: List<PluginToolOutput>,
    val specialParameters: Map<String, String> = emptyMap(),
    /** The file names each directory parameter asks for (PluginToolSchema.directoryFiles); absent means every regular file. */
    val directoryFiles: Map<String, List<String>> = emptyMap(),
)

/** An accepted catalog: the usable tools and why the others were dropped. */
data class PluginToolCatalog(
    val shortName: String,
    val sharedFamily: String?,
    val tools: List<PluginToolDefinition>,
    val diagnostics: List<String>,
)

sealed class PluginToolCatalogParse {

    data class Accepted(val catalog: PluginToolCatalog) : PluginToolCatalogParse()

    data class Refused(val reason: String) : PluginToolCatalogParse()
}

/**
 * Validates a provider's catalog document (MCP Server roadmap P12 D30 / D31 / D33, docs/dev/plugin-tool-protocol-v1.md).
 * A document that is not a JSON object, breaks a limit, uses an invalid or reserved short name, declares a shared family
 * it may not use, or repeats a tool name is refused as a whole; a tool with an invalid name, field or schema is dropped
 * alone and the reason kept as a diagnostic. Fields this version does not know are ignored, so providers can append
 * them. Pure Kotlin on top of Gson, so the JVM tests cover it.
 *
 * zh-CN: 校验提供者的目录文档. 不是 JSON 对象, 超出上限, 短名无效或保留, 声明了不允许的共享家族, 或工具名重复的文档整体拒绝;
 * 名称, 字段或 schema 无效的工具单独剔除并保留诊断. 本版本不认识的字段被忽略, 以便提供者追加. 基于 Gson 的纯 Kotlin, JVM 测试可覆盖.
 */
object PluginToolCatalogParser {

    /**
     * Families that several OFFICIAL providers may share in contract version 1 (D33). Version 1 only validates the field;
     * tools keep the provider's short name until provider selection exists (D44).
     */
    val SHARED_FAMILIES: Set<String> = setOf("ocr")

    /**
     * @param official whether the provider is OFFICIAL (the only level that may declare a shared family).
     * @param schemaCheck the reason a tool's input schema is unusable, or null (the subset of D31 by default).
     * @param outputSchemaCheck the same for an output schema.
     */
    fun parse(
        json: String,
        official: Boolean,
        schemaCheck: (JsonObject) -> String? = PluginToolSchema::checkInput,
        outputSchemaCheck: (JsonObject) -> String? = PluginToolSchema::checkOutput,
    ): PluginToolCatalogParse {
        val bytes = json.toByteArray(Charsets.UTF_8).size
        if (bytes > PluginToolLimits.MAX_CATALOG_BYTES) {
            return refused("the catalog has $bytes bytes, more than ${PluginToolLimits.MAX_CATALOG_BYTES}")
        }
        val root = runCatching { JsonParser.parseString(json) }.getOrElse { return refused("the catalog is not JSON: ${it.message}") }
        if (root !is JsonObject) return refused("the catalog is not a JSON object")

        val shortName = root.string(PluginToolCatalogJson.SHORT_NAME) ?: return refused("shortName is missing")
        if (!PluginToolNames.isValidShortName(shortName)) {
            return refused("shortName \"$shortName\" does not match ${PluginToolNames.SHORT_NAME_PATTERN.pattern}")
        }
        if (PluginToolNames.isReservedShortName(shortName)) return refused("shortName \"$shortName\" is reserved")

        val sharedFamily = when (val value = root.opt(PluginToolCatalogJson.SHARED_FAMILY)) {
            null -> null
            else -> (value as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return refused("sharedFamily is not a string")
        }
        if (sharedFamily != null) {
            if (sharedFamily !in SHARED_FAMILIES) return refused("sharedFamily \"$sharedFamily\" is not a shared family of this host")
            if (!official) return refused("only OFFICIAL plugins may declare the shared family \"$sharedFamily\"")
        }

        val entries = root.get(PluginToolCatalogJson.TOOLS) as? JsonArray ?: return refused("tools is not an array")
        if (entries.size() == 0) return refused("tools is empty")
        if (entries.size() > PluginToolLimits.MAX_TOOLS_PER_PLUGIN) {
            return refused("tools has ${entries.size()} entries, more than ${PluginToolLimits.MAX_TOOLS_PER_PLUGIN}")
        }
        val names = entries.mapNotNull { (it as? JsonObject)?.string(PluginToolCatalogJson.NAME) }
        names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.takeIf { it.isNotEmpty() }?.let { duplicated ->
            return refused("tool names repeat: ${duplicated.sorted().joinToString()}")
        }

        val diagnostics = mutableListOf<String>()
        val tools = entries.mapIndexedNotNull { index, entry ->
            when (val tool = parseTool(shortName, entry, schemaCheck, outputSchemaCheck)) {
                is ToolParse.Usable -> tool.definition
                is ToolParse.Dropped -> null.also { diagnostics += "tool #$index${tool.name?.let { " ($it)" }.orEmpty()} dropped: ${tool.reason}" }
            }
        }
        if (tools.isEmpty()) return refused("no usable tool: ${diagnostics.joinToString("; ")}")
        return PluginToolCatalogParse.Accepted(PluginToolCatalog(shortName, sharedFamily, tools, diagnostics))
    }

    private sealed class ToolParse {
        data class Usable(val definition: PluginToolDefinition) : ToolParse()
        data class Dropped(val name: String?, val reason: String) : ToolParse()
    }

    private fun parseTool(shortName: String, entry: JsonElement, schemaCheck: (JsonObject) -> String?, outputSchemaCheck: (JsonObject) -> String?): ToolParse {
        val tool = entry as? JsonObject ?: return ToolParse.Dropped(null, "not an object")
        val name = tool.string(PluginToolCatalogJson.NAME) ?: return ToolParse.Dropped(null, "name is missing")
        fun drop(reason: String) = ToolParse.Dropped(name, reason)
        if (!PluginToolNames.isValidToolName(name)) return drop("name does not match ${PluginToolNames.TOOL_NAME_PATTERN.pattern}")
        val fullName = PluginToolNames.fullName(shortName, name)
        if (!PluginToolNames.fitsMcpName(fullName)) return drop("$fullName has more than ${PluginToolLimits.MAX_FULL_NAME_CHARS} characters")

        val title = when (tool.opt(PluginToolCatalogJson.TITLE)) {
            null -> null
            else -> tool.string(PluginToolCatalogJson.TITLE)?.takeIf { it.length <= PluginToolLimits.MAX_TITLE_CHARS }
                ?: return drop("title is not a string of at most ${PluginToolLimits.MAX_TITLE_CHARS} characters")
        }
        val description = tool.string(PluginToolCatalogJson.DESCRIPTION)?.takeIf { it.isNotBlank() && it.length <= PluginToolLimits.MAX_DESCRIPTION_CHARS }
            ?: return drop("description is not a non-blank string of at most ${PluginToolLimits.MAX_DESCRIPTION_CHARS} characters")

        val inputSchema = tool.get(PluginToolCatalogJson.INPUT_SCHEMA) as? JsonObject ?: return drop("inputSchema is not an object")
        schemaCheck(inputSchema)?.let { return drop("inputSchema: $it") }
        val outputSchema = when (val value = tool.opt(PluginToolCatalogJson.OUTPUT_SCHEMA)) {
            null -> null
            is JsonObject -> value.also { schema -> outputSchemaCheck(schema)?.let { return drop("outputSchema: $it") } }
            else -> return drop("outputSchema is not an object")
        }

        val risk = tool.string(PluginToolCatalogJson.RISK)?.takeIf { it in PluginToolValues.RISKS }
            ?: return drop("risk is not one of ${PluginToolValues.RISKS.joinToString()}")
        val timeoutMs = tool.wholeNumber(PluginToolCatalogJson.TIMEOUT_MS, PluginToolLimits.DEFAULT_TIMEOUT_MS)
            ?.takeIf { it in 1..PluginToolLimits.MAX_TIMEOUT_MS }
            ?: return drop("timeoutMs is not a whole number in 1..${PluginToolLimits.MAX_TIMEOUT_MS}")
        val maxConcurrency = tool.wholeNumber(PluginToolCatalogJson.MAX_CONCURRENCY, PluginToolLimits.DEFAULT_MAX_CONCURRENCY.toLong())
            ?.takeIf { it in 1..PluginToolLimits.MAX_CONCURRENCY.toLong() }
            ?: return drop("maxConcurrency is not a whole number in 1..${PluginToolLimits.MAX_CONCURRENCY}")

        val outputs = when (val value = tool.opt(PluginToolCatalogJson.OUTPUTS)) {
            null -> emptyList()
            is JsonArray -> parseOutputs(value).getOrElse { return drop(it.message ?: "outputs are invalid") }
            else -> return drop("outputs is not an array")
        }
        return ToolParse.Usable(
            PluginToolDefinition(
                name, fullName, title, description, inputSchema, outputSchema, risk, timeoutMs, maxConcurrency.toInt(), outputs,
                PluginToolSchema.specialParameters(inputSchema),
                PluginToolSchema.directoryFiles(inputSchema),
            ),
        )
    }

    private fun parseOutputs(array: JsonArray): Result<List<PluginToolOutput>> = runCatching {
        require(array.size() <= PluginToolLimits.MAX_OUTPUTS) { "outputs has more than ${PluginToolLimits.MAX_OUTPUTS} entries" }
        val outputs = array.map { element ->
            val output = element as? JsonObject ?: throw IllegalArgumentException("an output is not an object")
            val name = output.string(PluginToolCatalogJson.OUTPUT_NAME)?.takeIf(PluginToolNames::isValidToolName)
                ?: throw IllegalArgumentException("an output name does not match ${PluginToolNames.TOOL_NAME_PATTERN.pattern}")
            val kind = output.opt(PluginToolCatalogJson.OUTPUT_KIND)?.let {
                output.string(PluginToolCatalogJson.OUTPUT_KIND)?.takeIf { value -> value in PluginToolValues.OUTPUT_KINDS }
                    ?: throw IllegalArgumentException("output $name has an unknown kind")
            } ?: PluginToolValues.OUTPUT_KIND_FILE
            val mimeType = if (kind == PluginToolValues.OUTPUT_KIND_DIRECTORY) DIRECTORY_MIME_TYPE
            else output.string(PluginToolCatalogJson.OUTPUT_MIME_TYPE)?.takeIf { MIME_TYPE.matches(it) }
                ?: throw IllegalArgumentException("output $name has no valid mimeType")
            val description = output.opt(PluginToolCatalogJson.OUTPUT_DESCRIPTION)?.let {
                output.string(PluginToolCatalogJson.OUTPUT_DESCRIPTION)?.takeIf { text -> text.length <= PluginToolLimits.MAX_DESCRIPTION_CHARS }
                    ?: throw IllegalArgumentException("output $name has an invalid description")
            }
            PluginToolOutput(name, mimeType, description, kind)
        }
        require(outputs.map { it.name }.toSet().size == outputs.size) { "output names repeat" }
        outputs
    }

    private fun refused(reason: String) = PluginToolCatalogParse.Refused(reason)

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /** The value at [key]; JSON null counts as absent, so optional fields may be written as null. */
    private fun JsonObject.opt(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }

    /** The whole number at [key], [default] when absent, null when present but not a whole number. */
    private fun JsonObject.wholeNumber(key: String, default: Long): Long? {
        val value = opt(key) ?: return default
        val number = (value as? JsonPrimitive)?.takeIf { it.isNumber }?.asBigDecimal ?: return null
        return runCatching { number.longValueExact() }.getOrNull()
    }

    private val MIME_TYPE = Regex("^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$")

    const val DIRECTORY_MIME_TYPE = "inode/directory"
}
