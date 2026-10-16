package org.autojs.autojs.core.plugin.tool

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.autojs.plugin.tool.api.PluginToolCatalogJson
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolValues
import java.math.BigDecimal

/**
 * The JSON Schema subset of the plugin tool contract (MCP Server roadmap P12 D31, docs/dev/plugin-tool-protocol-v1.md):
 * [checkInput] / [checkOutput] validate a schema when the host loads a catalog, [validate] checks call arguments
 * against it, [withDefaults] fills the defaults of absent properties, and [specialParameters] lists the image and file
 * parameters the host resolves. Nodes are `object`, `string`, `integer`, `number`, `boolean`, `array` or a `oneOf` of
 * nodes; unknown keywords make a schema invalid so that a constraint the author relied on is never ignored. Written
 * for this subset instead of a general validator library (no Jackson or second org.json, no R8 reflection rules, and
 * the subset's own rules such as top-level-only special types stay explicit). Pure Kotlin on top of Gson.
 *
 * zh-CN: 插件工具契约的 JSON Schema 子集: [checkInput] / [checkOutput] 在载入目录时校验 schema, [validate] 按其校验调用参数,
 * [withDefaults] 为缺省属性填入默认值, [specialParameters] 列出宿主负责解析的图片与文件参数. 未知关键字使 schema 无效, 以免作者
 * 依赖的约束被忽略. 为此子集专门编写, 未用通用校验库. 基于 Gson 的纯 Kotlin.
 */
object PluginToolSchema {

    private val TYPES = setOf("object", "string", "integer", "number", "boolean", "array")
    private val COMMON_KEYS = setOf("type", "title", "description", "default", "enum")
    private val KEYS_BY_TYPE = mapOf(
        "object" to COMMON_KEYS + setOf("properties", "required", "additionalProperties"),
        "string" to COMMON_KEYS + setOf("minLength", "maxLength"),
        "integer" to COMMON_KEYS + setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"),
        "number" to COMMON_KEYS + setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"),
        "boolean" to COMMON_KEYS,
        "array" to COMMON_KEYS + setOf("items", "minItems", "maxItems"),
    )
    private val CHOICE_KEYS = setOf("oneOf", "title", "description", "default")
    private const val MAX_TITLE_CHARS = PluginToolLimits.MAX_TITLE_CHARS
    private const val MAX_DESCRIPTION_CHARS = PluginToolLimits.MAX_DESCRIPTION_CHARS

    /** Why [schema] is not a usable input schema, or null: a closed object within the subset; image and file only as top-level string properties. */
    fun checkInput(schema: JsonObject): String? = check(schema, input = true)

    /** Why [schema] is not a usable output schema, or null: a closed object within the subset, without special types. */
    fun checkOutput(schema: JsonObject): String? = check(schema, input = false)

    /** The plain file names a directory parameter asks for (`x-autojs6-files`); a directory parameter without them is absent. */
    fun directoryFiles(schema: JsonObject): Map<String, List<String>> {
        val properties = schema.get("properties") as? JsonObject ?: return emptyMap()
        return properties.entrySet().mapNotNull { (name, node) ->
            ((node as? JsonObject)?.get(PluginToolCatalogJson.DIRECTORY_FILES) as? JsonArray)?.let { files -> name to files.mapNotNull { it.asStringOrNull() } }
        }.toMap(LinkedHashMap())
    }

    /** The image, file and directory parameters of a valid input schema, in declaration order, mapped to their special type. */
    fun specialParameters(schema: JsonObject): Map<String, String> {
        val properties = schema.get("properties") as? JsonObject ?: return emptyMap()
        return properties.entrySet().mapNotNull { (name, node) ->
            ((node as? JsonObject)?.get(PluginToolCatalogJson.SPECIAL_TYPE) as? JsonPrimitive)?.takeIf { it.isString }?.let { name to it.asString }
        }.toMap(LinkedHashMap())
    }

    /** The problems of [value] against a schema accepted by [checkInput] or [checkOutput], as `$.path: reason`; empty when it matches. */
    fun validate(schema: JsonObject, value: JsonElement): List<String> = mutableListOf<String>().also { problems -> validateNode(schema, value, "$", problems) }

    /** A copy of [value] where absent properties that declare a default get it, recursively through nested objects. */
    fun withDefaults(schema: JsonObject, value: JsonObject): JsonObject {
        val copy = value.deepCopy()
        val properties = schema.get("properties") as? JsonObject ?: return copy
        for ((name, node) in properties.entrySet()) {
            val property = node as? JsonObject ?: continue
            val present = copy.get(name)
            when {
                present == null || present.isJsonNull -> property.get("default")?.let { copy.add(name, it.deepCopy()) }
                property.get("type")?.asStringOrNull() == "object" && present is JsonObject -> copy.add(name, withDefaults(property, present))
            }
        }
        return copy
    }

    private fun check(schema: JsonObject, input: Boolean): String? = runCatching {
        require(schema.get("type")?.asStringOrNull() == "object") { "the root is not of type object" }
        val specials = intArrayOf(0)
        checkNode(schema, "$", depth = 1, rootProperty = false, input = input, specials = specials)
        require(specials[0] <= PluginToolLimits.MAX_INPUTS) { "more than ${PluginToolLimits.MAX_INPUTS} image, file or directory parameters" }
        null
    }.getOrElse { it.message ?: "the schema is invalid" }

    /** Checks one node and its children; [rootProperty] marks a direct property of the root, the only place for special types. */
    private fun checkNode(node: JsonObject, path: String, depth: Int, rootProperty: Boolean, input: Boolean, specials: IntArray) {
        require(depth <= PluginToolLimits.MAX_SCHEMA_DEPTH) { "$path: nested deeper than ${PluginToolLimits.MAX_SCHEMA_DEPTH} levels" }
        node.get(PluginToolCatalogJson.SPECIAL_TYPE)?.let { special ->
            require(input) { "$path: ${PluginToolCatalogJson.SPECIAL_TYPE} is only allowed in input schemas" }
            require(rootProperty) { "$path: ${PluginToolCatalogJson.SPECIAL_TYPE} is only allowed on top-level properties" }
            require(special.asStringOrNull() in PluginToolValues.SPECIAL_TYPES) { "$path: ${PluginToolCatalogJson.SPECIAL_TYPE} must be ${PluginToolValues.SPECIAL_TYPES.joinToString(" or ")}" }
            require(node.get("type")?.asStringOrNull() == "string") { "$path: an image, file or directory parameter must be of type string" }
            specials[0]++
        }
        node.get(PluginToolCatalogJson.DIRECTORY_FILES)?.let { files ->
            val keyword = PluginToolCatalogJson.DIRECTORY_FILES
            require(node.get(PluginToolCatalogJson.SPECIAL_TYPE)?.asStringOrNull() == PluginToolValues.SPECIAL_TYPE_DIRECTORY) {
                "$path: $keyword is only allowed beside ${PluginToolCatalogJson.SPECIAL_TYPE} ${PluginToolValues.SPECIAL_TYPE_DIRECTORY}"
            }
            val names = (files as? JsonArray)?.map { it.asStringOrNull() ?: throw IllegalArgumentException("$path: $keyword entries must be strings") }
                ?: throw IllegalArgumentException("$path: $keyword is not an array")
            require(names.size in 1..PluginToolLimits.MAX_DIRECTORY_FILES) { "$path: $keyword needs 1 to ${PluginToolLimits.MAX_DIRECTORY_FILES} names" }
            require(names.toSet().size == names.size) { "$path: $keyword repeats a name" }
            names.forEach { name -> require(isPlainFileName(name)) { "$path: $keyword entry \"$name\" is not a plain file name" } }
        }
        val keys = node.keySet() - PluginToolCatalogJson.SPECIAL_TYPE - PluginToolCatalogJson.DIRECTORY_FILES
        node.get("title")?.let { require(it.asStringOrNull()?.let { title -> title.length <= MAX_TITLE_CHARS } == true) { "$path: title is not a string of at most $MAX_TITLE_CHARS characters" } }
        node.get("description")?.let { require(it.asStringOrNull()?.let { text -> text.length <= MAX_DESCRIPTION_CHARS } == true) { "$path: description is not a string of at most $MAX_DESCRIPTION_CHARS characters" } }

        if (node.has("oneOf")) {
            unknownKeys(keys, CHOICE_KEYS)?.let { throw IllegalArgumentException("$path: unsupported keywords $it beside oneOf") }
            val choices = node.get("oneOf") as? JsonArray ?: throw IllegalArgumentException("$path: oneOf is not an array")
            require(choices.size() >= 2) { "$path: oneOf needs at least 2 schemas" }
            choices.forEachIndexed { index, choice ->
                val choiceNode = choice as? JsonObject ?: throw IllegalArgumentException("$path.oneOf[$index]: not an object")
                checkNode(choiceNode, "$path.oneOf[$index]", depth + 1, rootProperty = false, input = input, specials = specials)
            }
            node.get("default")?.let { value -> requireValue(node, value, "$path: default") }
            return
        }

        val type = node.get("type")?.asStringOrNull()
        require(type in TYPES) { "$path: type must be one of ${TYPES.joinToString()}" }
        unknownKeys(keys, KEYS_BY_TYPE.getValue(type!!))?.let { throw IllegalArgumentException("$path: unsupported keywords $it for type $type") }
        when (type) {
            "object" -> {
                val additional = node.get("additionalProperties")
                require(additional is JsonPrimitive && additional.isBoolean && !additional.asBoolean) { "$path: objects must be closed (additionalProperties: false)" }
                val properties = when (val value = node.get("properties")) {
                    null -> JsonObject()
                    is JsonObject -> value
                    else -> throw IllegalArgumentException("$path: properties is not an object")
                }
                require(properties.size() <= PluginToolLimits.MAX_SCHEMA_PROPERTIES) { "$path: more than ${PluginToolLimits.MAX_SCHEMA_PROPERTIES} properties" }
                node.get("required")?.let { value ->
                    val names = (value as? JsonArray)?.map { it.asStringOrNull() ?: throw IllegalArgumentException("$path: required holds a non-string") }
                        ?: throw IllegalArgumentException("$path: required is not an array")
                    require(names.toSet().size == names.size) { "$path: required repeats a name" }
                    names.firstOrNull { !properties.has(it) }?.let { throw IllegalArgumentException("$path: required names the unknown property $it") }
                }
                for ((name, child) in properties.entrySet()) {
                    require(name.isNotEmpty() && name.length <= 64) { "$path: property names must have 1 to 64 characters" }
                    val childNode = child as? JsonObject ?: throw IllegalArgumentException("$path.$name: not an object")
                    checkNode(childNode, "$path.$name", depth + 1, rootProperty = depth == 1, input = input, specials = specials)
                }
            }
            "string" -> {
                val min = nonNegativeInt(node, "minLength", path)
                val max = nonNegativeInt(node, "maxLength", path)
                require(min == null || max == null || min <= max) { "$path: minLength is above maxLength" }
            }
            "integer", "number" -> {
                val bounds = listOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum").associateWith { key -> node.get(key)?.let { number(it) ?: throw IllegalArgumentException("$path: $key is not a number") } }
                val low = listOfNotNull(bounds["minimum"], bounds["exclusiveMinimum"]).maxOrNull()
                val high = listOfNotNull(bounds["maximum"], bounds["exclusiveMaximum"]).minOrNull()
                require(low == null || high == null || low <= high) { "$path: the lower bound is above the upper bound" }
            }
            "array" -> {
                val items = node.get("items") as? JsonObject ?: throw IllegalArgumentException("$path: items must be one schema")
                require(!items.has("oneOf") && items.get("type")?.asStringOrNull() != "array") { "$path: array items must be scalars or objects" }
                checkNode(items, "$path[]", depth + 1, rootProperty = false, input = input, specials = specials)
                val min = nonNegativeInt(node, "minItems", path)
                val max = nonNegativeInt(node, "maxItems", path)
                require(min == null || max == null || min <= max) { "$path: minItems is above maxItems" }
            }
        }
        node.get("enum")?.let { values ->
            require(type in setOf("string", "integer", "number", "boolean")) { "$path: enum is only allowed on scalars" }
            val list = (values as? JsonArray)?.toList() ?: throw IllegalArgumentException("$path: enum is not an array")
            require(list.isNotEmpty()) { "$path: enum is empty" }
            list.forEachIndexed { index, value ->
                require(problems(node, value, path, checkEnum = false).isEmpty()) { "$path: enum[$index] does not match the type and bounds" }
                require((0 until index).none { equivalent(value, list[it]) }) { "$path: enum repeats a value" }
            }
        }
        node.get("default")?.let { value -> requireValue(node, value, "$path: default") }
    }

    private fun requireValue(node: JsonObject, value: JsonElement, label: String) {
        val problems = problems(node, value, "$", checkEnum = true)
        require(problems.isEmpty()) { "$label does not match its schema (${problems.first()})" }
    }

    private fun problems(node: JsonObject, value: JsonElement, path: String, checkEnum: Boolean): List<String> =
        mutableListOf<String>().also { validateNode(node, value, path, it, checkEnum) }

    private fun validateNode(node: JsonObject, value: JsonElement, path: String, problems: MutableList<String>, checkEnum: Boolean = true) {
        node.get("oneOf")?.let { choices ->
            val matching = (choices as JsonArray).count { problems(it.asJsonObject, value, path, checkEnum).isEmpty() }
            if (matching != 1) problems += "$path: matches $matching of the ${choices.size()} oneOf schemas instead of exactly one"
            return
        }
        when (node.get("type")?.asStringOrNull()) {
            "object" -> {
                val obj = value as? JsonObject ?: return run { problems += "$path: expected an object" }
                val properties = node.get("properties") as? JsonObject ?: JsonObject()
                (node.get("required") as? JsonArray)?.map { it.asString }?.filter { !obj.has(it) || obj.get(it).isJsonNull }?.forEach { problems += "$path.$it: is required" }
                for ((name, child) in obj.entrySet()) {
                    val property = properties.get(name) as? JsonObject
                    when {
                        property == null -> problems += "$path.$name: is not a parameter of this tool"
                        // JSON null counts as absent; a required property was reported above.
                        child.isJsonNull -> Unit
                        else -> validateNode(property, child, "$path.$name", problems, checkEnum)
                    }
                }
            }
            "string" -> {
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: return run { problems += "$path: expected a string" }
                val length = text.codePointCount(0, text.length)
                node.get("minLength")?.asInt?.let { if (length < it) problems += "$path: shorter than $it characters" }
                node.get("maxLength")?.asInt?.let { if (length > it) problems += "$path: longer than $it characters" }
            }
            "integer", "number" -> {
                val number = (value as? JsonPrimitive)?.takeIf { it.isNumber }?.let { number(it) } ?: return run { problems += "$path: expected a number" }
                if (node.get("type").asString == "integer" && number.stripTrailingZeros().scale() > 0) problems += "$path: expected a whole number"
                node.get("minimum")?.let(::number)?.let { if (number < it) problems += "$path: below the minimum $it" }
                node.get("maximum")?.let(::number)?.let { if (number > it) problems += "$path: above the maximum $it" }
                node.get("exclusiveMinimum")?.let(::number)?.let { if (number <= it) problems += "$path: not above $it" }
                node.get("exclusiveMaximum")?.let(::number)?.let { if (number >= it) problems += "$path: not below $it" }
            }
            "boolean" -> if ((value as? JsonPrimitive)?.isBoolean != true) problems += "$path: expected true or false"
            "array" -> {
                val array = value as? JsonArray ?: return run { problems += "$path: expected an array" }
                node.get("minItems")?.asInt?.let { if (array.size() < it) problems += "$path: fewer than $it items" }
                node.get("maxItems")?.asInt?.let { if (array.size() > it) problems += "$path: more than $it items" }
                val items = node.get("items").asJsonObject
                array.forEachIndexed { index, item -> validateNode(items, item, "$path[$index]", problems, checkEnum) }
            }
        }
        if (checkEnum) {
            val enum = node.get("enum") as? JsonArray
            if (enum != null && enum.none { equivalent(value, it) }) problems += "$path: not one of ${enum.joinToString()}"
        }
    }

    /** A file name without directories: not empty, not `.` or `..`, no slash, backslash or control character, at most 255 characters. */
    private fun isPlainFileName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 255 && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.isISOControl() }

    private fun unknownKeys(keys: Set<String>, allowed: Set<String>): String? = (keys - allowed).takeIf { it.isNotEmpty() }?.sorted()?.joinToString()

    private fun nonNegativeInt(node: JsonObject, key: String, path: String): Long? = node.get(key)?.let { value ->
        val number = number(value)?.takeIf { it.signum() >= 0 && it.stripTrailingZeros().scale() <= 0 }
            ?: throw IllegalArgumentException("$path: $key is not a non-negative whole number")
        runCatching { number.longValueExact() }.getOrElse { throw IllegalArgumentException("$path: $key is too large") }
    }

    private fun number(value: JsonElement): BigDecimal? = (value as? JsonPrimitive)?.takeIf { it.isNumber }?.asBigDecimal

    private fun equivalent(left: JsonElement, right: JsonElement): Boolean {
        val l = number(left)
        val r = number(right)
        return if (l != null && r != null) l.compareTo(r) == 0 else left == right
    }

    private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.asString
}
