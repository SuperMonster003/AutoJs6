package org.autojs.autojs.engine

import org.autojs.plugin.lua.runtime.api.LuaValue
import org.autojs.plugin.lua.runtime.api.LuaValueLimits
import org.autojs.plugin.lua.runtime.api.LuaValueValidation
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.IdentityHashMap

/** Converts host-owned execution arguments into a bounded, immutable protocol snapshot. */
internal object LuaExecutionArgumentMapper {

    fun map(arguments: Map<String, *>): LuaValue.MapValue {
        val limits = LuaValueLimits()
        val value = State(limits).mapRoot(arguments)
        LuaValueValidation.validate(value, limits)
        return value
    }

    private class State(private val limits: LuaValueLimits) {
        private val activeContainers = IdentityHashMap<Any, Boolean>()
        private var nodes = 0
        private var dataBytes = 0

        fun mapRoot(arguments: Map<String, *>): LuaValue.MapValue {
            touchNode(depth = 0, label = "Lua execution arguments")
            return convertMap(arguments, depth = 0, label = "Lua execution arguments")
        }

        private fun convert(value: Any?, depth: Int, label: String): LuaValue {
            touchNode(depth, label)
            return when (value) {
                null -> invalid("$label cannot be null")
                is Boolean -> LuaValue.BooleanValue(value)
                is Byte -> LuaValue.Int64Value(value.toLong())
                is Short -> LuaValue.Int64Value(value.toLong())
                is Int -> LuaValue.Int64Value(value.toLong())
                is Long -> LuaValue.Int64Value(value)
                is Float -> convertFloat(value.toDouble(), label)
                is Double -> convertFloat(value, label)
                is String -> {
                    addText(value, limits.maxStringOrBytes, "$label string")
                    LuaValue.StringValue(value)
                }
                is ByteArray -> {
                    addData(value.size, limits.maxStringOrBytes, "$label bytes")
                    LuaValue.BytesValue(value)
                }
                is BooleanArray -> convertArray(value, value.size, depth, label) { value[it] }
                is ShortArray -> convertArray(value, value.size, depth, label) { value[it] }
                is IntArray -> convertArray(value, value.size, depth, label) { value[it] }
                is LongArray -> convertArray(value, value.size, depth, label) { value[it] }
                is FloatArray -> convertArray(value, value.size, depth, label) { value[it] }
                is DoubleArray -> convertArray(value, value.size, depth, label) { value[it] }
                is Array<*> -> convertArray(value, value.size, depth, label) { value[it] }
                is List<*> -> convertArray(value, value.size, depth, label) { value[it] }
                is Map<*, *> -> convertMap(value, depth, label)
                else -> invalid("$label has unsupported type ${value.javaClass.name}")
            }
        }

        private fun convertFloat(value: Double, label: String): LuaValue.Float64Value {
            if (!value.isFinite()) invalid("$label must be a finite floating-point value")
            return LuaValue.Float64Value(value)
        }

        private fun convertArray(
            identity: Any,
            size: Int,
            depth: Int,
            label: String,
            valueAt: (Int) -> Any?,
        ): LuaValue.ArrayValue {
            requireContainerSize(size, "$label array")
            return withContainer(identity, label) {
                val values = ArrayList<LuaValue>(size)
                repeat(size) { index ->
                    values += convert(valueAt(index), depth + 1, "$label array entry $index")
                }
                LuaValue.ArrayValue(values)
            }
        }

        private fun convertMap(value: Map<*, *>, depth: Int, label: String): LuaValue.MapValue {
            requireContainerSize(value.size, "$label map")
            return withContainer(value, label) {
                val values = LinkedHashMap<String, LuaValue>(value.size)
                value.entries.forEachIndexed { index, entry ->
                    val key = entry.key as? String
                        ?: invalid("$label map entry $index must have a string key")
                    addText(key, limits.maxMapKeyBytes, "$label map entry $index key")
                    values[key] = convert(entry.value, depth + 1, "$label map entry $index value")
                }
                LuaValue.MapValue(values)
            }
        }

        private fun touchNode(depth: Int, label: String) {
            if (depth > limits.maxDepth) invalid("$label exceeds Lua value depth ${limits.maxDepth}")
            if (nodes >= limits.maxNodes) invalid("$label exceeds Lua value node count ${limits.maxNodes}")
            nodes += 1
        }

        private fun requireContainerSize(size: Int, label: String) {
            if (size !in 0..limits.maxContainerEntries) {
                invalid("$label has an invalid size or exceeds ${limits.maxContainerEntries} entries")
            }
        }

        private fun addText(value: String, perItemLimit: Int, label: String) {
            if (value.length > perItemLimit) invalid("$label exceeds $perItemLimit UTF-8 bytes")
            val size = try {
                Charsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
                    .remaining()
            } catch (error: Exception) {
                throw IllegalArgumentException("$label is not valid UTF-8 text", error)
            }
            addData(size, perItemLimit, label)
        }

        private fun addData(size: Int, perItemLimit: Int, label: String) {
            if (size > perItemLimit) invalid("$label exceeds $perItemLimit bytes")
            if (dataBytes > limits.maxDataBytes - size) {
                invalid("Lua execution arguments exceed ${limits.maxDataBytes} data bytes")
            }
            dataBytes += size
        }

        private inline fun <T> withContainer(identity: Any, label: String, block: () -> T): T {
            if (activeContainers.put(identity, true) != null) invalid("$label contains a cycle")
            return try {
                block()
            } finally {
                activeContainers.remove(identity)
            }
        }

        private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
    }
}
