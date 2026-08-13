package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LuaValueCodecTest {
    @Test
    fun allValueKindsRoundTrip() {
        val values = listOf(
            LuaValue.Nil,
            LuaValue.BooleanValue(true),
            LuaValue.Int64Value(Long.MIN_VALUE),
            LuaValue.Float64Value(-0.0),
            LuaValue.StringValue("Lua \uD83C\uDF19"),
            LuaValue.BytesValue(byteArrayOf(0, 1, -1)),
            LuaValue.ArrayValue(listOf(LuaValue.Int64Value(1), LuaValue.StringValue("two"))),
            LuaValue.MapValue(linkedMapOf("" to LuaValue.BooleanValue(false), "key" to LuaValue.Int64Value(9))),
        )

        values.forEach { value ->
            assertEquals(value, LuaValueCodec.decode(LuaValueCodec.encode(value)))
        }
    }

    @Test
    fun mapEncodingIsCanonicalAcrossInsertionOrder() {
        val first = LuaValue.MapValue(
            linkedMapOf("z" to LuaValue.Int64Value(1), "ä" to LuaValue.Int64Value(2), "a" to LuaValue.Int64Value(3)),
        )
        val second = LuaValue.MapValue(
            linkedMapOf("a" to LuaValue.Int64Value(3), "z" to LuaValue.Int64Value(1), "ä" to LuaValue.Int64Value(2)),
        )

        assertArrayEquals(LuaValueCodec.encode(first), LuaValueCodec.encode(second))
    }

    @Test
    fun duplicateDecodedMapKeyIsRejected() {
        val encodedChild = LuaValueCodec.encode(LuaValue.Int64Value(1))
        val entry = TaggedWireWriter(LuaRuntimeContract.SCHEMA_MAP_ENTRY, 1, 0)
            .string(1, "duplicate", requiredForReader = true)
            .document(2, encodedChild, requiredForReader = true)
            .encode()
        val map = TaggedWireWriter(LuaRuntimeContract.SCHEMA_VALUE, 1, 0)
            .int32(1, 8, requiredForReader = true)
            .document(8, entry)
            .document(8, entry)
            .encode()

        assertThrows(LuaContractException::class.java) { LuaValueCodec.decode(map) }
    }

    @Test
    fun futureOptionalValueFieldIsSkipped() {
        val value = TaggedWireWriter(LuaRuntimeContract.SCHEMA_VALUE, 1, 1)
            .int32(1, 2, requiredForReader = true)
            .boolean(2, true, requiredForReader = true)
            .string(99, "future optional")
            .encode()

        assertEquals(LuaValue.BooleanValue(true), LuaValueCodec.decode(value))
    }

    @Test
    fun nilInsideContainersIsRejected() {
        assertThrows(LuaContractException::class.java) {
            LuaValueCodec.encode(LuaValue.ArrayValue(listOf(LuaValue.Nil)))
        }
        assertThrows(LuaContractException::class.java) {
            LuaValueCodec.encode(LuaValue.MapValue(mapOf("removed" to LuaValue.Nil)))
        }
    }

    @Test
    fun nonFiniteFloatIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { LuaValue.Float64Value(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { LuaValue.Float64Value(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun depthAndAggregateDataLimitsFailClosed() {
        var deep: LuaValue = LuaValue.StringValue("leaf")
        repeat(LuaRuntimeContract.MAX_VALUE_DEPTH + 1) {
            deep = LuaValue.ArrayValue(listOf(deep))
        }
        assertThrows(LuaContractException::class.java) { LuaValueCodec.encode(deep) }

        val oversized = LuaValue.StringValue("x".repeat(LuaRuntimeContract.MAX_VALUE_STRING_OR_BYTES + 1))
        assertThrows(LuaContractException::class.java) { LuaValueCodec.encode(oversized) }
    }

    @Test
    fun containerEntryBoundaryMatchesTaggedWireFieldLimit() {
        val maximum = LuaValue.ArrayValue(
            List(LuaRuntimeContract.MAX_VALUE_CONTAINER_ENTRIES) { LuaValue.BooleanValue(true) },
        )
        assertEquals(maximum, LuaValueCodec.decode(LuaValueCodec.encode(maximum)))

        val oversized = LuaValue.ArrayValue(
            List(LuaRuntimeContract.MAX_VALUE_CONTAINER_ENTRIES + 1) { LuaValue.BooleanValue(true) },
        )
        assertThrows(LuaContractException::class.java) { LuaValueCodec.encode(oversized) }
    }

    @Test
    fun bytesAndContainersTakeDefensiveCopies() {
        val bytes = byteArrayOf(1, 2, 3)
        val byteValue = LuaValue.BytesValue(bytes)
        bytes[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), byteValue.toByteArray())
        assertNotSame(bytes, byteValue.toByteArray())

        val mutableArray = mutableListOf<LuaValue>(LuaValue.Int64Value(1))
        val array = LuaValue.ArrayValue(mutableArray)
        mutableArray += LuaValue.Int64Value(2)
        assertEquals(1, array.values.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (array.values as MutableList).add(LuaValue.Int64Value(3))
        }

        val mutableMap = linkedMapOf("a" to LuaValue.Int64Value(1))
        val map = LuaValue.MapValue(mutableMap)
        mutableMap["b"] = LuaValue.Int64Value(2)
        assertEquals(setOf("a"), map.values.keys)
        assertThrows(UnsupportedOperationException::class.java) {
            (map.values as MutableMap)["c"] = LuaValue.Int64Value(3)
        }
    }

    @Test
    fun toStringDoesNotExposeStringOrBytes() {
        val secret = "super-secret-token"
        val stringSummary = LuaValue.StringValue(secret).toString()
        val byteSummary = LuaValue.BytesValue(secret.toByteArray()).toString()

        assertEquals(false, stringSummary.contains(secret))
        assertEquals(false, byteSummary.contains(secret))
    }
}
