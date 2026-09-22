package org.autojs.autojs.project

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Strict, Android-independent parser shared by catalog discovery and execution admission. */
object AgentManifestParser {
    private val manifestKeys = setOf("id", "description", "parameters", "result", "risk", "confirm", "timeoutMs", "examples", "tags")
    private val scalarTypes = setOf("string", "number", "integer", "boolean")
    private val propertyKeys = setOf("type", "description", "enum", "default", "minimum", "maximum", "minLength", "maxLength")
    private val parameterName = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
    private val explicitId = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val mainKeys = setOf("main", "mainName", "mainScript", "mainScriptName", "mainScriptFile",
        "mainScriptFileName", "mainFile", "mainFileName").map(::normalizeMainKey).toSet()

    /** Returns null for an unregistered project. Invalid registrations fail without private detail. */
    @JvmStatic
    fun parseProject(json: String, fallbackId: String): AgentManifest? = manifestGuard {
        val project = parseObject(json)
        val value = project.get("agent")?.takeUnless { it.isJsonNull } ?: return@manifestGuard null
        require(value.isJsonObject)
        parseManifest(value.asJsonObject, fallbackId)
    }

    /** Main-entry aliases follow ProjectConfig; ambiguous aliases are rejected for execution. */
    @JvmStatic
    fun projectMain(json: String): String = manifestGuard {
        val project = parseObject(json)
        val declared = project.entrySet().filter { normalizeMainKey(it.key) in mainKeys }.map { it.value.strictString() }
        require(declared.distinct().size <= 1)
        (declared.firstOrNull() ?: ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME).also {
            require(it.isNotBlank() && !it.contains('\u0000'))
        }
    }

    /** Only the first leading JSDoc block, completed within the first 8 KiB, can register a file. */
    @JvmStatic
    fun parseHeader(bytes: ByteArray, fallbackId: String): AgentManifest? = manifestGuard {
        val header = firstHeader(bytes) ?: return@manifestGuard null
        val fields = mutableListOf<Pair<String, String>>()
        header.removePrefix("/**").removeSuffix("*/").lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim().removePrefix("*").trim()
            if (line.startsWith("@")) {
                val match = Regex("^@([A-Za-z]+)(?:\\s+(.*))?$").matchEntire(line)
                    ?: throw IllegalArgumentException()
                fields += match.groupValues[1] to match.groupValues[2].trim()
            } else if (line.isNotEmpty() && fields.isNotEmpty()) {
                val previous = fields.last()
                require(previous.first in setOf("description", "example", "result"))
                fields[fields.lastIndex] = previous.first to "${previous.second} $line"
            }
        }
        if (fields.none { it.first == "agent" }) return@manifestGuard null
        require(fields.count { it.first == "agent" } == 1 && fields.first { it.first == "agent" }.second.isEmpty())
        val allowed = setOf("agent", "description", "param", "result", "risk", "confirm", "timeout", "example", "tag")
        require(fields.all { it.first in allowed })
        listOf("description", "result", "risk", "confirm", "timeout").forEach { name ->
            require(fields.count { it.first == name } <= 1)
        }
        fun single(name: String): String? = fields.firstOrNull { it.first == name }?.second
        val value = JsonObject().apply {
            single("description")?.let { addProperty("description", it) }
            single("risk")?.let { addProperty("risk", it) }
            single("confirm")?.let { addProperty("confirm", it) }
            single("timeout")?.let { addProperty("timeoutMs", it.toLong()) }
            single("result")?.let { add("result", parseResult(it)) }
            add("examples", JsonArray().apply { fields.filter { it.first == "example" }.forEach { add(it.second) } })
            add("tags", JsonArray().apply { fields.filter { it.first == "tag" }.forEach { add(it.second) } })
        }
        val schema = AgentManifest.emptyParameters()
        val properties = schema.getAsJsonObject("properties")
        val required = schema.getAsJsonArray("required")
        fields.filter { it.first == "param" }.forEach { (_, declaration) ->
            // Explicit closing delimiters work with both JVM regex and Android's ICU parser.
            val match = Regex("""^\{([^}]+)\}\s+(\[[^\]]+\]|[^\s]+)(?:\s+(.*))?$""").matchEntire(declaration)
                ?: throw IllegalArgumentException()
            val typeParts = match.groupValues[1].split('=', limit = 2)
            val type = typeParts[0].trim()
            require(type in scalarTypes)
            val token = match.groupValues[2]
            val optional = token.startsWith("[")
            val nameDefault = (if (optional) token.substring(1, token.length - 1) else token).split('=', limit = 2)
            val name = nameDefault[0].trim()
            require(parameterName.matches(name) && !properties.has(name))
            require(optional || nameDefault.size == 1)
            val property = JsonObject().apply {
                addProperty("type", type)
                match.groupValues[3].takeIf(String::isNotBlank)?.let { addProperty("description", it) }
                if (typeParts.size == 2) add("enum", JsonArray().apply {
                    typeParts[1].split('|').forEach { add(parseDefault(type, it.trim())) }
                })
                if (nameDefault.size == 2) add("default", parseDefault(type, nameDefault[1].trim()))
            }
            properties.add(name, property)
            if (!optional) required.add(name)
        }
        value.add("parameters", schema)
        parseManifest(value, fallbackId)
    }

    /** Validates the declared subset and returns a copy with defaults applied; extra keys fail closed. */
    @JvmStatic
    fun validateArguments(manifest: AgentManifest, arguments: JsonObject): JsonObject = argumentGuard {
        require(arguments.toString().toByteArray(Charsets.UTF_8).size <= AiAgentContract.MAX_SCRIPT_ARGUMENTS_BYTES)
        val schema = validateSchema(manifest.parameters)
        val properties = schema.getAsJsonObject("properties")
        require(arguments.keySet().all(properties::has))
        val result = arguments.deepCopy()
        for ((name, spec) in properties.entrySet()) {
            val property = spec.asJsonObject
            if (!result.has(name) && property.has("default")) result.add(name, property.get("default").deepCopy())
            if (result.has(name)) validateValue(property, result.get(name))
        }
        require(schema.getAsJsonArray("required").all { result.has(it.asString) })
        require(result.toString().toByteArray(Charsets.UTF_8).size <= AiAgentContract.MAX_SCRIPT_ARGUMENTS_BYTES)
        result
    }

    private fun parseManifest(value: JsonObject, fallbackId: String): AgentManifest {
        require(value.keySet().all { it in manifestKeys })
        val id = if (value.has("id")) value.string("id").also { require(explicitId.matches(it)) } else normalizeId(fallbackId)
        val description = value.string("description").also { require(it.isNotBlank()) }
        val parameters = value.get("parameters")?.let {
            require(it.isJsonObject)
            validateSchema(it.asJsonObject)
        } ?: AgentManifest.emptyParameters()
        val result = value.get("result")?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonObject)
            it.asJsonObject.deepCopy()
        }
        val risk = value.optionalString("risk") ?: "normal"
        require(risk in setOf("readonly", "normal", "sensitive"))
        val declaredConfirm = value.optionalString("confirm") ?: "never"
        require(declaredConfirm in setOf("never", "before-run"))
        val timeout = value.get("timeoutMs")?.let(::integer) ?: 60_000L
        require(timeout in 1..AiAgentContract.MAX_TOOL_TIMEOUT_MS)
        return AgentManifest(id, description, parameters, result, risk,
            if (risk == "sensitive") "before-run" else declaredConfirm, timeout,
            value.strings("examples"), value.strings("tags"))
    }

    /** Object root plus primitive properties; supported constraints cannot be silently ignored. */
    private fun validateSchema(input: JsonObject): JsonObject {
        require(input.keySet().all { it in setOf("type", "properties", "required", "additionalProperties") })
        require(input.string("type") == "object")
        if (input.has("additionalProperties")) {
            val extra = input.get("additionalProperties")
            require(extra.isJsonPrimitive && extra.asJsonPrimitive.isBoolean && !extra.asBoolean)
        }
        val properties = input.get("properties")?.let { require(it.isJsonObject); it.asJsonObject } ?: JsonObject()
        val required = input.get("required")?.let { value ->
            require(value.isJsonArray)
            value.asJsonArray.map { it.strictString() }.also { require(it.distinct().size == it.size) }
        } ?: emptyList()
        require(required.all(properties::has))
        for ((name, entry) in properties.entrySet()) {
            require(parameterName.matches(name) && entry.isJsonObject)
            val property = entry.asJsonObject
            require(property.keySet().all { it in propertyKeys })
            val type = property.string("type")
            require(type in scalarTypes)
            property.optionalString("description")
            if (property.has("minimum") || property.has("maximum")) require(type in setOf("number", "integer"))
            val minimum = property.get("minimum")?.let(::number)
            val maximum = property.get("maximum")?.let(::number)
            require(minimum == null || maximum == null || minimum <= maximum)
            if (property.has("minLength") || property.has("maxLength")) require(type == "string")
            val minLength = property.get("minLength")?.let(::integer)
            val maxLength = property.get("maxLength")?.let(::integer)
            require(minLength == null || minLength >= 0)
            require(maxLength == null || maxLength >= 0)
            require(minLength == null || maxLength == null || minLength <= maxLength)
            property.get("enum")?.let { values ->
                require(values.isJsonArray && values.asJsonArray.size() > 0)
                val enum = values.asJsonArray.toList()
                enum.forEach { validateValue(property, it, checkEnum = false) }
                require(enum.indices.all { index -> (0 until index).none { equivalent(enum[index], enum[it]) } })
            }
            property.get("default")?.let { validateValue(property, it) }
        }
        return input.deepCopy().apply {
            add("properties", properties.deepCopy())
            add("required", JsonArray().apply { required.forEach { add(it) } })
            addProperty("additionalProperties", false)
        }
    }

    private fun validateValue(spec: JsonObject, value: JsonElement, checkEnum: Boolean = true) {
        require(value.isJsonPrimitive)
        val primitive = value.asJsonPrimitive
        when (spec.string("type")) {
            "string" -> {
                require(primitive.isString)
                val count = primitive.asString.codePointCount(0, primitive.asString.length).toLong()
                spec.get("minLength")?.let { require(count >= integer(it)) }
                spec.get("maxLength")?.let { require(count <= integer(it)) }
            }
            "boolean" -> require(primitive.isBoolean)
            "number", "integer" -> {
                val numeric = number(value)
                if (spec.string("type") == "integer") require(numeric.stripTrailingZeros().scale() <= 0)
                spec.get("minimum")?.let { require(numeric >= number(it)) }
                spec.get("maximum")?.let { require(numeric <= number(it)) }
            }
            else -> throw IllegalArgumentException()
        }
        if (checkEnum && spec.has("enum")) require(spec.getAsJsonArray("enum").any { equivalent(value, it) })
    }

    private fun equivalent(left: JsonElement, right: JsonElement): Boolean =
        if (left.isJsonPrimitive && right.isJsonPrimitive && left.asJsonPrimitive.isNumber && right.asJsonPrimitive.isNumber)
            number(left).compareTo(number(right)) == 0 else left == right

    private fun parseDefault(type: String, text: String): JsonElement = when (type) {
        "string" -> if (text.startsWith('"')) parseJson(text).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) } else JsonPrimitive(text)
        "boolean" -> { require(text == "true" || text == "false"); JsonPrimitive(text.toBoolean()) }
        "number", "integer" -> parseJson(text).also {
            val value = number(it)
            if (type == "integer") require(value.stripTrailingZeros().scale() <= 0)
        }
        else -> throw IllegalArgumentException()
    }

    private fun parseResult(text: String): JsonObject {
        val typed = Regex("""^\{(object|string|number|integer|boolean)\}(?:\s+(.*))?$""").matchEntire(text)
            ?: return parseObject(text)
        val type = typed.groupValues[1]
        val body = typed.groupValues[2].trim()
        return JsonObject().apply {
            addProperty("type", type)
            if (type == "object" && body.startsWith("{") && body.endsWith("}")) {
                val properties = JsonObject()
                val declarations = body.substring(1, body.length - 1).trim()
                if (declarations.isNotEmpty()) declarations.split(',').forEach {
                    val parts = it.split(':', limit = 2).map(String::trim)
                    require(parts.size == 2 && parameterName.matches(parts[0]) && parts[1] in scalarTypes && !properties.has(parts[0]))
                    properties.add(parts[0], JsonObject().apply { addProperty("type", parts[1]) })
                }
                add("properties", properties)
            } else if (body.isNotEmpty()) addProperty("description", body)
        }
    }

    private fun firstHeader(source: ByteArray): String? {
        val bytes = source.copyOfRange(0, minOf(source.size, AiAgentContract.MAX_MANIFEST_HEADER_BYTES))
        var index = if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) 3 else 0
        fun at(offset: Int, value: Char) = offset < bytes.size && bytes[offset] == value.code.toByte()
        while (true) {
            while (index < bytes.size && bytes[index].toInt().toChar() in " \t\r\n") index++
            if (at(index, '/') && at(index + 1, '/')) {
                while (index < bytes.size && !at(index, '\n')) index++
            } else break
        }
        if (!at(index, '/') || !at(index + 1, '*')) return null
        val start = index
        index += 2
        while (index + 1 < bytes.size && !(at(index, '*') && at(index + 1, '/'))) index++
        if (index + 1 >= bytes.size) {
            require(!String(bytes, Charsets.UTF_8).contains("@agent"))
            return null
        }
        if (!at(start + 2, '*')) return null
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, index + 2 - start)).toString()
    }

    private fun normalizeId(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
            .replace(Regex("[\\p{M}]"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (normalized.isNotEmpty()) return normalized
        val digest = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
        return "script-$digest"
    }

    // Match ProjectConfig's FuzzyDeserializer for entry aliases only; manifest fields stay strict.
    private fun normalizeMainKey(key: String) = key.replace(Regex("[^a-zA-Z0-9]"), "").lowercase(Locale.ROOT)

    private fun JsonObject.string(key: String) = requireNotNull(get(key)).strictString()
    private fun JsonObject.optionalString(key: String): String? = get(key)?.let { it.strictString() }
    private fun JsonElement.strictString(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString)
        return asString
    }
    private fun JsonObject.strings(key: String): List<String> = get(key)?.let { value ->
        require(value.isJsonArray)
        value.asJsonArray.map { it.strictString().also { text -> require(text.isNotBlank()) } }
    } ?: emptyList()
    private fun number(value: JsonElement): BigDecimal {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        require(value.asDouble.isFinite())
        return value.asBigDecimal
    }
    private fun integer(value: JsonElement): Long = number(value).longValueExact()
    private fun parseJson(json: String): JsonElement {
        AiJsonSyntaxValidator.requireValid(json)
        return JsonParser.parseString(json)
    }
    private fun parseObject(json: String): JsonObject = parseJson(json).also { require(it.isJsonObject) }.asJsonObject

    private inline fun <T> manifestGuard(block: () -> T): T = try { block() } catch (_: Exception) {
        throw AgentManifestException("SCRIPT_NOT_REGISTERED", "Script registration is missing or invalid.")
    }
    private inline fun <T> argumentGuard(block: () -> T): T = try { block() } catch (_: Exception) {
        throw AgentManifestException("TOOL_ARGUMENTS_INVALID", "Script arguments do not match the registered schema.")
    }
}
