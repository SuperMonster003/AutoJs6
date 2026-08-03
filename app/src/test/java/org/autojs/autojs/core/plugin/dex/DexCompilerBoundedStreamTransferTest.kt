package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

class DexCompilerBoundedStreamTransferTest {

    @Test
    fun exactLimitIsCopiedAndSummarized() {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE * 2) { (it * 31).toByte() }
        val output = ByteArrayOutputStream()

        val summary = DexCompilerBoundedStreamTransfer.copy(
            ByteArrayInputStream(bytes),
            output,
            bytes.size.toLong(),
        )

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), summary.sizeBytes)
        assertEquals(sha256(bytes), summary.sha256)
    }

    @Test
    fun oneByteBeyondLimitIsDetectedWithoutWritingBeyondLimit() {
        val bytes = ByteArray(257) { it.toByte() }
        val output = ByteArrayOutputStream()

        val error = assertThrows(DexCompilerStreamLimitExceededException::class.java) {
            DexCompilerBoundedStreamTransfer.copy(ByteArrayInputStream(bytes), output, 256)
        }

        assertEquals(256, error.limitBytes)
        assertArrayEquals(bytes.copyOf(256), output.toByteArray())
    }

    @Test
    fun cancellationBeforeReadDoesNotConsumeOrWrite() {
        val input = CountingInputStream(byteArrayOf(1, 2, 3))
        val output = ByteArrayOutputStream()

        assertThrows(DexCompilerStreamCancelledException::class.java) {
            DexCompilerBoundedStreamTransfer.copy(input, output, 3) { true }
        }

        assertEquals(0, input.readCount)
        assertEquals(0, output.size())
    }

    @Test
    fun cancellationBetweenReadAndWriteDoesNotWriteTheReadChunk() {
        val input = CountingInputStream(byteArrayOf(1, 2, 3))
        val output = ByteArrayOutputStream()

        assertThrows(DexCompilerStreamCancelledException::class.java) {
            DexCompilerBoundedStreamTransfer.copy(input, output, 3) { input.readCount > 0 }
        }

        assertEquals(1, input.readCount)
        assertEquals(0, output.size())
    }

    @Test
    fun zeroLengthReadsDoNotChangeTheDigestOrSize() {
        val bytes = byteArrayOf(9, 8, 7)
        val input = object : InputStream() {
            private var zeroReturned = false
            private val delegate = ByteArrayInputStream(bytes)

            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!zeroReturned) {
                    zeroReturned = true
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }
        val output = ByteArrayOutputStream()

        val summary = DexCompilerBoundedStreamTransfer.copy(input, output, 3)

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(sha256(bytes), summary.sha256)
    }

    @Test
    fun longMaximumDoesNotOverflowTheLookaheadReadSize() {
        val output = ByteArrayOutputStream()

        val summary = DexCompilerBoundedStreamTransfer.copy(
            ByteArrayInputStream(ByteArray(0)),
            output,
            Long.MAX_VALUE,
        )

        assertEquals(0L, summary.sizeBytes)
        assertEquals(sha256(ByteArray(0)), summary.sha256)
    }

    private class CountingInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var readCount = 0

        override fun read(): Int {
            readCount++
            return delegate.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCount++
            return delegate.read(buffer, offset, length)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
