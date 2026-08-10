package org.autojs.plugin.lua.runtime.api

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * The complete V1 cross-process value model.
 *
 * Lua functions, coroutines, userdata, metatables, cyclic tables, Java objects,
 * Android objects, paths, and URI objects are intentionally not representable.
 */
sealed class LuaValue {
    object Nil : LuaValue()

    class BooleanValue(val value: Boolean) : LuaValue() {
        override fun equals(other: Any?): Boolean = other is BooleanValue && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    class Int64Value(val value: Long) : LuaValue() {
        override fun equals(other: Any?): Boolean = other is Int64Value && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    class Float64Value(val value: Double) : LuaValue() {
        init {
            require(value.isFinite()) { "Lua floating-point values must be finite" }
        }

        override fun equals(other: Any?): Boolean =
            other is Float64Value && value.toBits() == other.value.toBits()

        override fun hashCode(): Int = value.toBits().hashCode()
    }

    class StringValue(val value: String) : LuaValue() {
        override fun equals(other: Any?): Boolean = other is StringValue && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    class BytesValue(value: ByteArray) : LuaValue() {
        private val snapshot = value.copyOf()

        fun toByteArray(): ByteArray = snapshot.copyOf()

        val size: Int
            get() = snapshot.size

        override fun equals(other: Any?): Boolean = other is BytesValue && snapshot.contentEquals(other.snapshot)
        override fun hashCode(): Int = snapshot.contentHashCode()
    }

    class ArrayValue(values: Collection<LuaValue>) : LuaValue() {
        val values: List<LuaValue> = Collections.unmodifiableList(ArrayList(values))

        override fun equals(other: Any?): Boolean = other is ArrayValue && values == other.values
        override fun hashCode(): Int = values.hashCode()
    }

    class MapValue(values: Map<String, LuaValue>) : LuaValue() {
        val values: Map<String, LuaValue> = Collections.unmodifiableMap(LinkedHashMap(values))

        override fun equals(other: Any?): Boolean = other is MapValue && values == other.values
        override fun hashCode(): Int = values.hashCode()
    }

    final override fun toString(): String = when (this) {
        Nil -> "LuaValue.Nil"
        is BooleanValue -> "LuaValue.Boolean"
        is Int64Value -> "LuaValue.Int64"
        is Float64Value -> "LuaValue.Float64"
        is StringValue -> "LuaValue.String(utf8Bytes=${value.toByteArray(Charsets.UTF_8).size})"
        is BytesValue -> "LuaValue.Bytes(size=$size)"
        is ArrayValue -> "LuaValue.Array(size=${values.size})"
        is MapValue -> "LuaValue.Map(size=${values.size})"
    }
}

data class LuaValueLimits(
    val maxDepth: Int = LuaRuntimeContract.MAX_VALUE_DEPTH,
    val maxNodes: Int = LuaRuntimeContract.MAX_VALUE_NODES,
    val maxDataBytes: Int = LuaRuntimeContract.MAX_VALUE_DATA_BYTES,
    val maxContainerEntries: Int = LuaRuntimeContract.MAX_VALUE_CONTAINER_ENTRIES,
    val maxStringOrBytes: Int = LuaRuntimeContract.MAX_VALUE_STRING_OR_BYTES,
    val maxMapKeyBytes: Int = LuaRuntimeContract.MAX_MAP_KEY_BYTES,
) {
    init {
        require(maxDepth >= 0)
        require(maxNodes > 0)
        require(maxDataBytes >= 0)
        require(maxContainerEntries >= 0)
        require(maxStringOrBytes >= 0)
        require(maxMapKeyBytes >= 0)
    }
}

data class LuaValueQuotaSnapshot(
    val nodes: Int,
    val dataBytes: Int,
    val maximumDepth: Int,
)

object LuaValueValidation {
    fun validate(
        value: LuaValue,
        limits: LuaValueLimits = LuaValueLimits(),
    ): LuaValueQuotaSnapshot {
        val state = State(limits)
        state.visit(value, 0)
        return LuaValueQuotaSnapshot(state.nodes, state.dataBytes, state.maximumDepth)
    }

    private class State(private val limits: LuaValueLimits) {
        var nodes = 0
        var dataBytes = 0
        var maximumDepth = 0

        fun visit(value: LuaValue, depth: Int) {
            requireValue(depth <= limits.maxDepth) { "Lua value depth exceeds ${limits.maxDepth}" }
            requireValue(nodes < limits.maxNodes) { "Lua value node count exceeds ${limits.maxNodes}" }
            nodes += 1
            maximumDepth = maxOf(maximumDepth, depth)
            when (value) {
                LuaValue.Nil,
                is LuaValue.BooleanValue,
                is LuaValue.Int64Value,
                -> Unit

                is LuaValue.Float64Value ->
                    requireValue(value.value.isFinite()) { "Lua floating-point values must be finite" }

                is LuaValue.StringValue -> addData(
                    utf8Size(value.value, "Lua string"),
                    limits.maxStringOrBytes,
                    "Lua string",
                )

                is LuaValue.BytesValue -> addData(value.size, limits.maxStringOrBytes, "Lua bytes")

                is LuaValue.ArrayValue -> {
                    requireValue(value.values.size <= limits.maxContainerEntries) {
                        "Lua array entry count exceeds ${limits.maxContainerEntries}"
                    }
                    value.values.forEach { child ->
                        requireValue(child !== LuaValue.Nil) {
                            "Lua arrays must be dense and cannot contain nil"
                        }
                        visit(child, depth + 1)
                    }
                }

                is LuaValue.MapValue -> {
                    requireValue(value.values.size <= limits.maxContainerEntries) {
                        "Lua map entry count exceeds ${limits.maxContainerEntries}"
                    }
                    value.values.forEach { (key, child) ->
                        requireValue(child !== LuaValue.Nil) {
                            "Lua maps cannot contain nil values"
                        }
                        addData(utf8Size(key, "Lua map key"), limits.maxMapKeyBytes, "Lua map key")
                        visit(child, depth + 1)
                    }
                }
            }
        }

        private fun addData(size: Int, perItemLimit: Int, label: String) {
            requireValue(size <= perItemLimit) { "$label exceeds $perItemLimit bytes" }
            requireValue(dataBytes <= limits.maxDataBytes - size) {
                "Lua value data exceeds ${limits.maxDataBytes} bytes"
            }
            dataBytes += size
        }
    }

    private fun utf8Size(value: String, label: String): Int = try {
        val encoder = Charsets.UTF_8.newEncoder()
        encoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        encoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        encoder.encode(java.nio.CharBuffer.wrap(value)).remaining()
    } catch (e: Exception) {
        throw LuaContractException(LuaContractViolation.INVALID_VALUE, "$label is not valid UTF-8 text", e)
    }

    private inline fun requireValue(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw LuaContractException(LuaContractViolation.INVALID_VALUE, message())
        }
    }
}
