package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest

class JvmSourceBoundedTransferTest {
    @Test
    fun boundedReadReturnsExactBytesAndSha256() {
        val bytes = "public final class Main {}".toByteArray()
        val captured = JvmSourceBoundedTransfer.read(ByteArrayInputStream(bytes), bytes.size.toLong())

        assertArrayEquals(bytes, captured.bytes)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            },
            captured.sha256Hex,
        )
    }

    @Test(expected = JvmSourceStreamLimitExceededException::class)
    fun byteBeyondQuotaIsConsumedOnlyToProveOverflow() {
        JvmSourceBoundedTransfer.read(ByteArrayInputStream(byteArrayOf(1, 2)), 1)
    }

    @Test
    fun longMaximumDoesNotOverflowReadSizeComputation() {
        val captured = JvmSourceBoundedTransfer.read(ByteArrayInputStream(byteArrayOf(1)), Long.MAX_VALUE)
        assertArrayEquals(byteArrayOf(1), captured.bytes)
    }

    @Test(expected = CharacterCodingException::class)
    fun capturedOutputRejectsMalformedUtf8InsteadOfReplacingIt() {
        JvmSourceCapturedOutput(
            bytes = byteArrayOf(0xc3.toByte(), 0x28),
            sha256Hex = "00".repeat(32),
        ).text
    }
}
