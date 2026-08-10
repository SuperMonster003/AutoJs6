package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object LuaValueCodec {
    fun encode(value: LuaValue): ByteArray {
        LuaValueValidation.validate(value)
        return encodeValue(value)
    }

    fun decode(bytes: ByteArray): LuaValue {
        val value = decodeValue(bytes, depth = 0)
        LuaValueValidation.validate(value)
        return value
    }

    private fun encodeValue(value: LuaValue): ByteArray {
        val writer = writer(LuaRuntimeContract.SCHEMA_VALUE)
        when (value) {
            LuaValue.Nil -> writer.int32(ValueTags.KIND, ValueKind.NIL.wireCode, requiredForReader = true)
            is LuaValue.BooleanValue -> writer
                .int32(ValueTags.KIND, ValueKind.BOOLEAN.wireCode, requiredForReader = true)
                .boolean(ValueTags.BOOLEAN, value.value, requiredForReader = true)
            is LuaValue.Int64Value -> writer
                .int32(ValueTags.KIND, ValueKind.INT64.wireCode, requiredForReader = true)
                .int64(ValueTags.INT64, value.value, requiredForReader = true)
            is LuaValue.Float64Value -> writer
                .int32(ValueTags.KIND, ValueKind.FLOAT64.wireCode, requiredForReader = true)
                .float64(ValueTags.FLOAT64, value.value, requiredForReader = true)
            is LuaValue.StringValue -> writer
                .int32(ValueTags.KIND, ValueKind.STRING.wireCode, requiredForReader = true)
                .string(ValueTags.STRING, value.value, requiredForReader = true)
            is LuaValue.BytesValue -> writer
                .int32(ValueTags.KIND, ValueKind.BYTES.wireCode, requiredForReader = true)
                .bytes(ValueTags.BYTES, value.toByteArray(), requiredForReader = true)
            is LuaValue.ArrayValue -> {
                writer.int32(ValueTags.KIND, ValueKind.ARRAY.wireCode, requiredForReader = true)
                value.values.forEach { child -> writer.document(ValueTags.CHILD, encodeValue(child)) }
            }
            is LuaValue.MapValue -> {
                writer.int32(ValueTags.KIND, ValueKind.MAP.wireCode, requiredForReader = true)
                value.values.entries.sortedWith { left, right ->
                    compareUtf8(left.key, right.key)
                }.forEach { (key, child) ->
                    writer.document(
                        ValueTags.MAP_ENTRY,
                        writer(LuaRuntimeContract.SCHEMA_MAP_ENTRY)
                            .string(MapEntryTags.KEY, key, requiredForReader = true)
                            .document(MapEntryTags.VALUE, encodeValue(child), requiredForReader = true)
                            .encode(),
                    )
                }
            }
        }
        return writer.encode()
    }

    private fun decodeValue(bytes: ByteArray, depth: Int): LuaValue {
        if (depth > LuaRuntimeContract.MAX_VALUE_DEPTH) {
            throw LuaContractException(
                LuaContractViolation.INVALID_VALUE,
                "Lua value depth exceeds ${LuaRuntimeContract.MAX_VALUE_DEPTH}",
            )
        }
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_VALUE, ValueTags.ALL, ValueTags.REPEATED)
        val kind = enumByCode<ValueKind>(document.requireInt32(ValueTags.KIND), "Lua value kind")
        return when (kind) {
            ValueKind.NIL -> {
                requireExactTags(document, setOf(ValueTags.KIND), "nil")
                LuaValue.Nil
            }
            ValueKind.BOOLEAN -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.BOOLEAN), "boolean")
                LuaValue.BooleanValue(document.requireBoolean(ValueTags.BOOLEAN))
            }
            ValueKind.INT64 -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.INT64), "int64")
                LuaValue.Int64Value(document.requireInt64(ValueTags.INT64))
            }
            ValueKind.FLOAT64 -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.FLOAT64), "float64")
                LuaValue.Float64Value(document.requireFloat64(ValueTags.FLOAT64))
            }
            ValueKind.STRING -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.STRING), "string")
                LuaValue.StringValue(document.requireString(ValueTags.STRING))
            }
            ValueKind.BYTES -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.BYTES), "bytes")
                LuaValue.BytesValue(document.requireBytes(ValueTags.BYTES))
            }
            ValueKind.ARRAY -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.CHILD), "array", allowMissing = true)
                LuaValue.ArrayValue(
                    document.documents(ValueTags.CHILD).map { child -> decodeValue(child, depth + 1) },
                )
            }
            ValueKind.MAP -> {
                requireExactTags(document, setOf(ValueTags.KIND, ValueTags.MAP_ENTRY), "map", allowMissing = true)
                val values = linkedMapOf<String, LuaValue>()
                document.documents(ValueTags.MAP_ENTRY).forEach { encodedEntry ->
                    val entry = decode(
                        encodedEntry,
                        LuaRuntimeContract.SCHEMA_MAP_ENTRY,
                        MapEntryTags.ALL,
                    )
                    val key = entry.requireString(MapEntryTags.KEY)
                    if (values.containsKey(key)) {
                        throw LuaContractException(
                            LuaContractViolation.INVALID_VALUE,
                            "Lua map contains a duplicate decoded key",
                        )
                    }
                    values[key] = decodeValue(entry.requireDocument(MapEntryTags.VALUE), depth + 1)
                }
                LuaValue.MapValue(values)
            }
        }
    }

    private fun decode(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes)
        .requireSchema(schemaId, LuaRuntimeContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private fun requireExactTags(
        document: TaggedWireDocument,
        allowed: Set<Int>,
        kind: String,
        allowMissing: Boolean = false,
    ) {
        val unexpected = (document.tags intersect ValueTags.ALL) - allowed
        if (unexpected.isNotEmpty()) {
            throw LuaContractException(
                LuaContractViolation.INVALID_VALUE,
                "Lua $kind value contains incompatible fields $unexpected",
            )
        }
        if (!allowMissing && !document.tags.containsAll(allowed)) {
            throw LuaContractException(LuaContractViolation.INVALID_VALUE, "Lua $kind value is incomplete")
        }
    }

    private inline fun <reified T> enumByCode(code: Int, label: String): T where T : Enum<T>, T : WireCode {
        return enumValues<T>().firstOrNull { it.wireCode == code }
            ?: throw LuaContractException(LuaContractViolation.UNKNOWN_ENUM, "Unknown $label $code")
    }

    private fun writer(schemaId: Int): TaggedWireWriter = TaggedWireWriter(
        schemaId,
        LuaRuntimeContract.SCHEMA_MAJOR,
        LuaRuntimeContract.SCHEMA_MINOR,
    )

    private fun compareUtf8(left: String, right: String): Int {
        val a = left.toByteArray(Charsets.UTF_8)
        val b = right.toByteArray(Charsets.UTF_8)
        val shared = minOf(a.size, b.size)
        for (index in 0 until shared) {
            val comparison = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return a.size.compareTo(b.size)
    }

    private interface WireCode {
        val wireCode: Int
    }

    private enum class ValueKind(override val wireCode: Int) : WireCode {
        NIL(1),
        BOOLEAN(2),
        INT64(3),
        FLOAT64(4),
        STRING(5),
        BYTES(6),
        ARRAY(7),
        MAP(8),
    }

    private object ValueTags {
        const val KIND = 1
        const val BOOLEAN = 2
        const val INT64 = 3
        const val FLOAT64 = 4
        const val STRING = 5
        const val BYTES = 6
        const val CHILD = 7
        const val MAP_ENTRY = 8
        val ALL = (1..8).toSet()
        val REPEATED = setOf(CHILD, MAP_ENTRY)
    }

    private object MapEntryTags {
        const val KEY = 1
        const val VALUE = 2
        val ALL = setOf(KEY, VALUE)
    }
}
