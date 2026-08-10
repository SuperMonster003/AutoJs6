package org.autojs.plugin.lua.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LuaRuntimeGoldenWireTest {
    @Test
    fun cancellationEncodingMatchesProtocolV1GoldenDocument() {
        val value = LuaExecutionCancellation(
            requestId = LuaRuntimeFixtures.REQUEST_ID,
            reason = LuaCancellationReason.REQUESTED,
            elapsedMillis = 42L,
        )

        assertEquals(CANCELLATION_GOLDEN_HEX, LuaRuntimeCodec.encodeCancellation(value).toHexString())
        assertEquals(value, LuaRuntimeCodec.decodeCancellation(CANCELLATION_GOLDEN_HEX.hexToByteArray()))
    }

    @Test
    fun valueEncodingMatchesProtocolV1GoldenDocument() {
        val value = LuaValue.MapValue(
            linkedMapOf(
                "ok" to LuaValue.BooleanValue(true),
                "value" to LuaValue.Int64Value(42L),
            ),
        )

        assertEquals(VALUE_GOLDEN_HEX, LuaValueCodec.encode(value).toHexString())
        assertEquals(value, LuaValueCodec.decode(VALUE_GOLDEN_HEX.hexToByteArray()))
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        HEX[(byte.toInt() ushr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f]
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val HEX = "0123456789abcdef"
        const val CANCELLATION_GOLDEN_HEX =
            "414a365700000001000000684c550016000000010000000000000003" +
                "0000000100000005000000010000001000112233445566778899aabbccddeeff" +
                "0000000200000001000000010000000400000001" +
                "00000003000000020000000100000008000000000000002a"
        const val VALUE_GOLDEN_HEX =
            "414a365700000001000001584c550001000000010000000000000003" +
                "0000000100000001000000010000000400000008" +
                "0000000800000006000000000000007f" +
                "414a3657000000010000007f4c550002000000010000000000000002" +
                "000000010000000400000001000000026f6b" +
                "00000002000000060000000100000041" +
                "414a365700000001000000414c550001000000010000000000000002" +
                "0000000100000001000000010000000400000002" +
                "0000000200000003000000010000000101" +
                "00000008000000060000000000000089" +
                "414a365700000001000000894c550002000000010000000000000002" +
                "0000000100000004000000010000000576616c7565" +
                "00000002000000060000000100000048" +
                "414a365700000001000000484c550001000000010000000000000002" +
                "0000000100000001000000010000000400000003" +
                "00000003000000020000000100000008000000000000002a"
    }
}
