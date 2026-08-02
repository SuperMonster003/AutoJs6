package org.autojs.plugin.dexcompiler.api

import org.junit.Assert.assertEquals
import org.junit.Test

class DexCompilerGoldenWireTest {

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
    }
}
