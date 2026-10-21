package org.autojs.autojs.core.plugin.mail

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.nio.charset.StandardCharsets

/**
 * Host-side codec of the JSON documents carried by the mail contract (roadmap P1.2, decision D25:
 * JSON envelope). Pure Kotlin on top of Gson, so the JVM tests cover it without Android.
 */
object MailJson {

    /** Request envelope; [json] is the encoded `{id, op, args}` document. */
    data class Request(val id: String, val op: String, val json: String)

    /** Response envelope; [result] is the encoded `result` element, [error] the mapped error. */
    data class Response(val id: String?, val ok: Boolean, val result: String?, val error: MailError?)

    private const val FIELD_DEBUG = "debug"

    /** [debug] holds the redacted protocol trace lines of a `debug: true` account (decision D28). */
    data class Progress(val id: String?, val transferred: Long, val total: Long?, val debug: List<String>? = null, val hasTransfer: Boolean = true)

    data class Event(
        val type: String,
        val seq: Long,
        val generation: Long,
        val folder: String?,
        val message: String?,
        val mode: String?,
        val reason: String?,
        val error: MailError?,
        val json: String,
    )

    data class Status(val state: String, val reason: String?, val lastError: MailError?, val json: String)

    data class WatchOptions(val folder: String, val mode: String?, val pollIntervalMs: Long?, val fetchBody: Boolean? = null)

    fun encodeRequest(id: String, op: String, argsJson: String?): Request {
        require(id.isNotBlank()) { "request id must not be blank" }
        require(MailContract.isKnownOp(op)) { "unknown mail operation: $op" }
        val args = argsJson?.takeIf { it.isNotBlank() }?.let { text ->
            parseElement(text, MailErrorCodes.INVALID_ARGUMENT, "arguments").let { element ->
                if (!element.isJsonObject) {
                    throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.INVALID_ARGUMENT, "arguments must be a JSON object", retryable = false))
                }
                element.asJsonObject
            }
        } ?: JsonObject()
        val envelope = JsonObject().apply {
            addProperty(MailContract.FIELD_ID, id)
            addProperty(MailContract.FIELD_OP, op)
            add(MailContract.FIELD_ARGS, args)
        }
        val json = envelope.toString()
        checkEnvelopeSize(json, "request")
        return Request(id, op, json)
    }

    fun encodeWatchOptions(options: WatchOptions, generation: Long): String {
        require(options.folder.isNotBlank()) { "watch folder must not be blank" }
        options.mode?.let { require(it in MailContract.WATCH_MODES) { "unknown watch mode: $it" } }
        options.pollIntervalMs?.let { require(it >= MailContract.MIN_POLL_INTERVAL_MS) { "poll interval below ${MailContract.MIN_POLL_INTERVAL_MS} ms" } }
        return JsonObject().apply {
            addProperty(MailContract.FIELD_FOLDER, options.folder)
            options.mode?.let { addProperty(MailContract.FIELD_MODE, it) }
            options.pollIntervalMs?.let { addProperty(MailContract.FIELD_POLL_INTERVAL_MS, it) }
            options.fetchBody?.let { addProperty(MailContract.FIELD_FETCH_BODY, it) }
            addProperty(MailContract.FIELD_GENERATION, generation)
        }.toString()
    }

    fun decodeResponse(json: String?): Response {
        val root = parseObject(json, "response")
        val ok = root.get(MailContract.FIELD_OK)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
            ?: throw malformed("response", "missing boolean ok")
        val id = optionalString(root, MailContract.FIELD_ID)
        return if (ok) {
            Response(id, true, root.get(MailContract.FIELD_RESULT)?.takeUnless { it.isJsonNull }?.toString(), null)
        } else {
            val error = root.get(MailContract.FIELD_ERROR)?.takeIf { it.isJsonObject }?.asJsonObject
            Response(id, false, null, decodeError(error) ?: MailErrorMapper.normalize(MailErrorCodes.INTERNAL, "response carries no error", retryable = false))
        }
    }

    fun decodeProgress(json: String?): Progress {
        val root = parseObject(json, "progress")
        val transferred = optionalLong(root, MailContract.FIELD_TRANSFERRED)
        val debug = root.get(FIELD_DEBUG)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { line -> line.takeIf { it.isJsonPrimitive }?.asString }
            ?.takeIf { it.isNotEmpty() }
        return Progress(optionalString(root, MailContract.FIELD_ID), (transferred ?: 0L).coerceAtLeast(0L),
            optionalLong(root, MailContract.FIELD_TOTAL)?.takeIf { it >= 0L }, debug, transferred != null)
    }

    fun decodeEvent(json: String?): Event {
        val root = parseObject(json, "event")
        val type = optionalString(root, MailContract.FIELD_TYPE)?.takeIf { it.isNotBlank() } ?: throw malformed("event", "missing type")
        return Event(
            type = type,
            seq = optionalLong(root, MailContract.FIELD_SEQ) ?: 0L,
            generation = optionalLong(root, MailContract.FIELD_GENERATION) ?: 0L,
            folder = optionalString(root, MailContract.FIELD_FOLDER),
            message = root.get(MailContract.FIELD_MESSAGE)?.takeIf { it.isJsonObject }?.toString(),
            mode = optionalString(root, MailContract.FIELD_MODE),
            reason = optionalString(root, MailContract.FIELD_REASON),
            error = decodeError(root.get(MailContract.FIELD_ERROR)?.takeIf { it.isJsonObject }?.asJsonObject),
            json = root.toString(),
        )
    }

    fun decodeStatus(json: String?): Status {
        val root = parseObject(json, "status")
        val state = optionalString(root, MailContract.FIELD_STATE)?.takeIf { it in MailContract.STATES } ?: throw malformed("status", "missing state")
        return Status(
            state = state,
            reason = optionalString(root, MailContract.FIELD_REASON),
            lastError = decodeError(root.get(MailContract.FIELD_LAST_ERROR)?.takeIf { it.isJsonObject }?.asJsonObject),
            json = root.toString(),
        )
    }

    /** Parses [json] as an object; the failure is reported as `INVALID_ARGUMENT` with [label]. */
    fun requireObject(json: String?, label: String): String {
        val element = parseElement(json.orEmpty(), MailErrorCodes.INVALID_ARGUMENT, label)
        if (!element.isJsonObject) {
            throw MailPluginException(MailErrorMapper.normalize(MailErrorCodes.INVALID_ARGUMENT, "$label must be a JSON object", retryable = false))
        }
        return element.toString()
    }

    fun utf8Length(text: String): Int = text.toByteArray(StandardCharsets.UTF_8).size

    fun checkEnvelopeSize(json: String, label: String) {
        val size = utf8Length(json)
        if (size > MailContract.MAX_ENVELOPE_BYTES) {
            throw MailPluginException(
                MailErrorMapper.normalize(
                    MailErrorCodes.LIMIT_EXCEEDED,
                    "$label envelope of $size bytes exceeds ${MailContract.MAX_ENVELOPE_BYTES} bytes",
                    retryable = false,
                ),
            )
        }
    }

    private fun decodeError(error: JsonObject?): MailError? {
        error ?: return null
        val details = error.get(MailContract.FIELD_ERROR_DETAILS)?.takeUnless { it.isJsonNull }?.toString()
        val retryable = error.get(MailContract.FIELD_ERROR_RETRYABLE)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
        return MailErrorMapper.normalize(
            code = optionalString(error, MailContract.FIELD_ERROR_CODE),
            message = optionalString(error, MailContract.FIELD_ERROR_MESSAGE),
            retryable = retryable,
            details = details,
        )
    }

    private fun parseObject(json: String?, label: String): JsonObject {
        val element = parseElement(json.orEmpty(), MailErrorCodes.INTERNAL, label)
        if (!element.isJsonObject) throw malformed(label, "not a JSON object")
        return element.asJsonObject
    }

    private fun parseElement(json: String, code: String, label: String): JsonElement {
        if (json.isBlank()) {
            throw MailPluginException(MailErrorMapper.normalize(code, "$label is empty", retryable = false))
        }
        return try {
            JsonParser.parseString(json)
        } catch (e: RuntimeException) {
            throw MailPluginException(MailErrorMapper.normalize(code, "$label is not valid JSON", retryable = false), e)
        }
    }

    private fun malformed(label: String, detail: String): MailPluginException {
        return MailPluginException(MailErrorMapper.normalize(MailErrorCodes.INTERNAL, "malformed $label: $detail", retryable = false))
    }

    private fun optionalString(root: JsonObject, name: String): String? {
        val element = root.get(name) ?: return null
        return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else null
    }

    private fun optionalLong(root: JsonObject, name: String): Long? {
        val element = root.get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return runCatching { element.asLong }.getOrNull()
    }
}
