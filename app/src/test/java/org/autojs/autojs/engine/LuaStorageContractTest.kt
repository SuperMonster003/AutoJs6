package org.autojs.autojs.engine

import org.autojs.plugin.lua.runtime.api.LuaHostCallRequest
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.autojs.plugin.lua.runtime.api.LuaValueCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaStorageContractTest {

    @Test
    fun keyGrammarAcceptsOnlyTheFrozenAsciiShape() {
        listOf("a", "_", "A.b-c_9", "k" + "x".repeat(63)).forEach {
            LuaStorageContract.requireValidKey(it)
        }
        listOf("", "1key", ".key", "a/b", "a\\b", "a:b", "with space", "é", "k" + "x".repeat(64)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                LuaStorageContract.requireValidKey(it)
            }
        }
    }

    @Test
    fun storedValuesAreCanonicalTextOnlyLuaTrees() {
        val value = LuaValue.MapValue(
            linkedMapOf(
                "enabled" to LuaValue.BooleanValue(true),
                "items" to LuaValue.ArrayValue(
                    listOf(LuaValue.Int64Value(7), LuaValue.StringValue("保存")),
                ),
            ),
        )

        val encoded = LuaStorageContract.encodeStoredValue(value)

        assertEquals(value, LuaStorageContract.decodeStoredValue(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            LuaStorageContract.encodeStoredValue(LuaValue.Nil)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LuaStorageContract.encodeStoredValue(LuaValue.BytesValue(byteArrayOf(1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LuaStorageContract.encodeStoredValue(
                LuaValue.MapValue(mapOf("nested" to LuaValue.BytesValue(byteArrayOf(1)))),
            )
        }
    }

    @Test
    fun encodedValueLimitLeavesAProvedPutEnvelopeMargin() {
        val smallValue = LuaValue.StringValue("x")
        val request = LuaHostCallRequest(
            executionId = LuaRequestId.fromBytes(ByteArray(16)),
            callId = "c".repeat(LuaRuntimeContract.MAX_CALL_ID_BYTES),
            capability = LuaStorageContract.CAPABILITY,
            arguments = LuaValue.MapValue(
                linkedMapOf(
                    "op" to LuaValue.StringValue("put"),
                    "key" to LuaValue.StringValue("k" + "x".repeat(63)),
                    "value" to smallValue,
                ),
            ),
        )
        val measuredOverhead = LuaRuntimeCodec.encodeHostRequest(request).size - LuaValueCodec.encode(smallValue).size

        assertEquals(LuaStorageContract.MAX_PUT_ENVELOPE_BYTES, measuredOverhead)
        assertTrue(
            LuaStorageContract.MAX_ENCODED_VALUE_BYTES + measuredOverhead <=
                LuaRuntimeContract.MAX_METADATA_BYTES,
        )

        LuaStorageContract.encodeStoredValue(nestedIntegerArrays(entriesPerChild = 730))
        assertThrows(IllegalArgumentException::class.java) {
            LuaStorageContract.encodeStoredValue(nestedIntegerArrays(entriesPerChild = 735))
        }
    }

    private fun nestedIntegerArrays(entriesPerChild: Int) = LuaValue.ArrayValue(
        List(4) {
            LuaValue.ArrayValue(List(entriesPerChild) { index -> LuaValue.Int64Value(index.toLong()) })
        },
    )
}
