package org.autojs.plugin.ai.common.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AiCommonGoldenWireTest {

    @Test
    fun protocolRangeEncodingMatchesProtocolV1GoldenDocument() {
        val range = AiProtocolRange(
            minimum = AiProtocolVersion(1, 0),
            maximum = AiProtocolVersion(1, 2),
        )

        val encoded = AiCommonCodec.encodeProtocolRange(range)
        assertEquals(GOLDEN_HEX, encoded.toHexString())
        assertEquals(range, AiCommonCodec.decodeProtocolRange(GOLDEN_HEX.hexToByteArray()))
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
        const val GOLDEN_HEX =
            "414a3657000000010000006c41490001000000010000000000000004" +
                "0000000100000001000000010000000400000001" +
                "0000000200000001000000010000000400000000" +
                "0000000300000001000000010000000400000001" +
                "0000000400000001000000010000000400000002"
    }
}
