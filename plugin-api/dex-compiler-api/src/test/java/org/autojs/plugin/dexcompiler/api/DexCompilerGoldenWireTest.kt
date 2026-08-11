package org.autojs.plugin.dexcompiler.api

import org.junit.Assert.assertEquals
import org.junit.Test

class DexCompilerGoldenWireTest {

    @Test
    fun compileRequestEncodingPreservesProtocolV10GoldenDocument() {
        val encoded = DexCompilerCodec.encodeRequest(DexCompilerFixtures.request())

        assertEquals(V1_REQUEST_GOLDEN_HEX, encoded.toHexString())
        assertEquals(
            DexCompilerContract.PROTOCOL_V1_0,
            DexCompilerCodec.decodeRequest(V1_REQUEST_GOLDEN_HEX.hexToByteArray()).protocolVersion,
        )
    }

    @Test
    fun cancellationEncodingMatchesProtocolV1GoldenDocument() {
        val value = DexCompileCancellation(
            requestId = DexCompilerFixtures.requestId,
            reason = DexCompilerCancellationReason.REQUESTED,
            phase = DexCompilerFailurePhase.COMPILATION,
            elapsedMillis = 42L,
        )

        val encoded = DexCompilerCodec.encodeCancellation(value)
        assertEquals(GOLDEN_HEX, encoded.toHexString())

        val decoded = DexCompilerCodec.decodeCancellation(GOLDEN_HEX.hexToByteArray())
        assertEquals(DexCompilerFixtures.requestId, decoded.requestId)
        assertEquals(DexCompilerCancellationReason.REQUESTED, decoded.reason)
        assertEquals(DexCompilerFailurePhase.COMPILATION, decoded.phase)
        assertEquals(42L, decoded.elapsedMillis)
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
            "414a3657000000010000007c00000215000000010000000000000004" +
                "00000001000000050000000100000010000102030405060708090a0b0c0d0e0f" +
                "0000000200000001000000010000000400000001" +
                "0000000300000001000000010000000400000004" +
                "00000004000000020000000100000008000000000000002a"
        const val V1_REQUEST_GOLDEN_HEX =
            "414a3657000000010000019d0000021000000001000000000000000f" +
                "00000001000000050000000100000010000102030405060708090a0b0c0d0e0f" +
                "0000000200000001000000010000000400000001" +
                "0000000300000001000000010000000400000000" +
                "000000040000000400000001000000036a6172" +
                "000000050000000400000001000000076465782d7a6970" +
                "0000000600000004000000010000000772656c65617365" +
                "0000000700000001000000010000000400000018" +
                "000000080000000200000001000000080000000000001000" +
                "000000090000000500000001000000201310ca2c8932dbd118668bd97442558a" +
                "3e1f546b5e15c69699dfbd6024f548d8" +
                "0000000a0000000200000001000000080000000001000000" +
                "0000000b00000001000000010000000400000001" +
                "0000000c000000050000000100000020283ce7ae3e88729f5f00dab49205fd29" +
                "067929e2d3107b17317ebe94b45e0b6a" +
                "0000000d00000001000000010000000400000001" +
                "0000000e00000001000000010000000400010000" +
                "0000000f000000020000000100000008000000000001d4c0"
    }
}
