package org.autojs.autojs.engine

import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaExecutionArgumentMapperTest {

    @Test
    fun mapsOnlyTheFrozenV1HostValueShapes() {
        val mapped = LuaExecutionArgumentMapper.map(
            mapOf(
                "boolean" to true,
                "byte" to 1.toByte(),
                "short" to 2.toShort(),
                "int" to 3,
                "long" to 4L,
                "float" to 1.25f,
                "double" to 2.5,
                "string" to "text",
                "bytes" to byteArrayOf(5, 6),
                "array" to arrayOf<Any>("first", 2L),
                "ints" to intArrayOf(7, 8),
                "map" to mapOf("nested" to false),
            ),
        )

        assertEquals(LuaValue.BooleanValue(true), mapped.values["boolean"])
        assertEquals(LuaValue.Int64Value(1L), mapped.values["byte"])
        assertEquals(LuaValue.Int64Value(2L), mapped.values["short"])
        assertEquals(LuaValue.Int64Value(3L), mapped.values["int"])
        assertEquals(LuaValue.Int64Value(4L), mapped.values["long"])
        assertEquals(LuaValue.Float64Value(1.25), mapped.values["float"])
        assertEquals(LuaValue.Float64Value(2.5), mapped.values["double"])
        assertEquals(LuaValue.StringValue("text"), mapped.values["string"])
        assertEquals(LuaValue.BytesValue(byteArrayOf(5, 6)), mapped.values["bytes"])
        assertEquals(
            LuaValue.ArrayValue(listOf(LuaValue.StringValue("first"), LuaValue.Int64Value(2L))),
            mapped.values["array"],
        )
        assertEquals(
            LuaValue.ArrayValue(listOf(LuaValue.Int64Value(7L), LuaValue.Int64Value(8L))),
            mapped.values["ints"],
        )
        assertEquals(
            LuaValue.MapValue(mapOf("nested" to LuaValue.BooleanValue(false))),
            mapped.values["map"],
        )
    }

    @Test
    fun rejectsCyclesNullsNonStringKeysAndNonFiniteNumbers() {
        val cycle = mutableListOf<Any>()
        cycle.add(cycle)
        val invalid = listOf<Map<String, *>>(
            mapOf("value" to cycle),
            mapOf("value" to listOf(null)),
            mapOf("value" to mapOf(1 to "not-a-string-key")),
            mapOf("value" to Double.NaN),
            mapOf("value" to Float.POSITIVE_INFINITY),
        )

        invalid.forEach { arguments ->
            assertThrows(IllegalArgumentException::class.java) {
                LuaExecutionArgumentMapper.map(arguments)
            }
        }
    }

    @Test
    fun rejectsContainersAndPayloadsBeyondProtocolQuotas() {
        val tooManyEntries = List(LuaRuntimeContract.MAX_VALUE_CONTAINER_ENTRIES + 1) { it }
        val tooMuchData = List(5) { "x".repeat(LuaRuntimeContract.MAX_VALUE_STRING_OR_BYTES) }

        val entriesFailure = assertThrows(IllegalArgumentException::class.java) {
            LuaExecutionArgumentMapper.map(mapOf("value" to tooManyEntries))
        }
        val dataFailure = assertThrows(IllegalArgumentException::class.java) {
            LuaExecutionArgumentMapper.map(mapOf("value" to tooMuchData))
        }

        assertTrue(entriesFailure.message.orEmpty().contains("entries"))
        assertTrue(dataFailure.message.orEmpty().contains("data bytes"))
    }
}
