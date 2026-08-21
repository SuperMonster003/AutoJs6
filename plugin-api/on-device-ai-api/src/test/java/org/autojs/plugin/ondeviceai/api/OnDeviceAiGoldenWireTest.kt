package org.autojs.plugin.ondeviceai.api

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceAiGoldenWireTest {

    @Test
    fun firstModelPageRequestMatchesProtocolV12GoldenDocument() {
        val request = AiModelListRequest(
            protocolVersion = OnDeviceAiProtocol.PROTOCOL_V1_2,
            pageSize = 32,
        )

        val encoded = OnDeviceAiCodec.encodeModelListRequest(request)
        assertEquals(GOLDEN_HEX, encoded.toHexString())
        assertEquals(request, OnDeviceAiCodec.decodeModelListRequest(GOLDEN_HEX.hexToByteArray()))
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
            "414a365700000001000000584f440002000000010000000000000003" +
                "0000000100000001000000010000000400000001" +
                "0000000200000001000000010000000400000002" +
                "0000000300000001000000010000000400000020"
    }
}
