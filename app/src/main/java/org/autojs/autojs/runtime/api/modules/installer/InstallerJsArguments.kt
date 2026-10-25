package org.autojs.autojs.runtime.api.modules.installer

import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** Snapshots Rhino values before any asynchronous work; source File/Uri values never go through JSON. */
internal object InstallerJsArguments {
    fun source(value: Any?): Any? = Snapshot().copy(value, source = true, depth = 0)

    /** Missing / undefined is distinct from explicit null, including for optional scalar arguments. */
    fun json(value: Any?): JsonElement? = when {
        value is Undefined || value === Scriptable.NOT_FOUND -> null
        else -> toJson(Snapshot().copy(value, source = false, depth = 0))
    }

    fun optional(args: Array<out Any?>, index: Int): JsonElement? = if (index >= args.size) null else json(args[index])

    fun count(name: String, args: Array<out Any?>, range: IntRange) {
        if (args.size !in range) invalid("$name expects ${if (range.first == range.last) range.first else "${range.first} to ${range.last}"} arguments")
    }

    private class Snapshot {
        private val active = IdentityHashMap<Any, Boolean>()
        private var nodes = 0

        fun copy(value: Any?, source: Boolean, depth: Int): Any? {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) invalid("installer argument is too large or deeply nested")
            if (value == null) return null
            if (value is Undefined || value === Scriptable.NOT_FOUND) invalid("installer argument must not contain undefined or sparse entries")
            if (value is Callable) invalid("installer argument must not contain functions")
            return when (value) {
                is String -> value
                is CharSequence -> value.toString()
                is Boolean -> value
                is Number -> {
                    if (!value.toDouble().isFinite()) invalid("installer argument numbers must be finite")
                    // Do not carry mutable Java Number subclasses into a background worker.
                    when (value) {
                        is Byte, is Short, is Int, is Long -> value.toLong()
                        else -> value.toDouble()
                    }
                }
                is File -> if (source) File(value.path) else invalid("installer options must be a JSON object")
                is Uri -> if (source) value.toString() else invalid("installer options must be a JSON object")
                is Wrapper -> nested(value) { copy(value.unwrap(), source, depth + 1) }
                is NativeArray -> nested(value) {
                    if (value.length > MAX_ARRAY_LENGTH) invalid("installer argument array exceeds $MAX_ARRAY_LENGTH items")
                    Collections.unmodifiableList(List(value.length.toInt()) { index ->
                        if (!value.has(index, value)) invalid("installer source arrays must not be sparse")
                        copy(value.get(index, value), source, depth + 1)
                    })
                }
                is NativeObject -> nested(value) {
                    if (value.prototype != null && value.prototype !== ScriptableObject.getObjectPrototype(value)) {
                        invalid("installer arguments must use plain objects")
                    }
                    val ids = value.ids
                    if (ids.size > MAX_OBJECT_KEYS) invalid("installer argument object is too large")
                    val result = linkedMapOf<String, Any?>()
                    ids.forEach { id ->
                        val key = when (id) { is String -> id; is Number -> id.toInt().toString(); else -> invalid("installer object keys must be strings") }
                        val item = if (id is Number) value.get(id.toInt(), value) else value.get(key, value)
                        result[key] = copy(item, source, depth + 1)
                    }
                    Collections.unmodifiableMap(result)
                }
                is Scriptable -> invalid("installer arguments must use paths, File, Uri or plain objects")
                is List<*> -> nested(value) {
                    if (value.size > MAX_ARRAY_LENGTH) invalid("installer argument array exceeds $MAX_ARRAY_LENGTH items")
                    Collections.unmodifiableList(value.map { copy(it, source, depth + 1) })
                }
                is Array<*> -> nested(value) {
                    if (value.size > MAX_ARRAY_LENGTH) invalid("installer argument array exceeds $MAX_ARRAY_LENGTH items")
                    Collections.unmodifiableList(value.map { copy(it, source, depth + 1) })
                }
                is Map<*, *> -> nested(value) {
                    if (value.size > MAX_OBJECT_KEYS || value.keys.any { it !is String }) invalid("installer object keys must be strings")
                    Collections.unmodifiableMap(value.entries.associateTo(linkedMapOf()) { (key, item) -> key as String to copy(item, source, depth + 1) })
                }
                else -> invalid("unsupported installer argument type: ${value.javaClass.simpleName}")
            }
        }

        private inline fun <T> nested(value: Any, block: () -> T): T {
            if (active.put(value, true) != null) invalid("installer arguments must not contain cycles")
            return try { block() } finally { active.remove(value) }
        }
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is List<*> -> JsonArray().apply { value.forEach { add(toJson(it)) } }
        is Map<*, *> -> JsonObject().apply { value.forEach { (key, item) -> add(key as String, toJson(item)) } }
        else -> invalid("installer options must contain JSON values")
    }

    private fun invalid(message: String): Nothing = throw InstallerJsErrors.failure(InstallerErrorCodes.INVALID_ARGUMENT, message)
    private const val MAX_DEPTH = 16
    private const val MAX_NODES = 8192
    private const val MAX_ARRAY_LENGTH = 64
    private const val MAX_OBJECT_KEYS = 64
}
