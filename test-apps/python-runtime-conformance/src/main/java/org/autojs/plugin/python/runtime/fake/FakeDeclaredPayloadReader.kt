package org.autojs.plugin.python.runtime.fake

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class FakeReadPayload(
    val bytes: ByteArray,
    val sha256: ByteArray,
)

object FakeDeclaredPayloadReader {
    fun readExactly(
        input: InputStream,
        declaredLengthBytes: Long,
        maximumLengthBytes: Long,
        shouldStop: () -> Boolean = { false },
        verifyProducerCompletion: () -> Unit = {},
    ): FakeReadPayload {
        require(maximumLengthBytes >= 0L)
        require(declaredLengthBytes in 0L..maximumLengthBytes) { "Payload exceeds its negotiated limit" }
        require(declaredLengthBytes <= Int.MAX_VALUE.toLong()) { "Payload cannot be materialized" }
        val digest = MessageDigest.getInstance("SHA-256")
        val output = ByteArrayOutputStream(declaredLengthBytes.toInt())
        val buffer = ByteArray(minOf(8192L, maxOf(1L, declaredLengthBytes)).toInt())
        var remaining = declaredLengthBytes
        while (remaining > 0L) {
            check(!shouldStop()) { "Payload read was cancelled or reached its deadline" }
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(count >= 0) { "Payload ended before its declared length" }
            if (count == 0) continue
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            remaining -= count.toLong()
        }
        check(!shouldStop()) { "Payload read was cancelled or reached its deadline" }
        require(input.read() == -1) { "Payload exceeds its declared length" }
        verifyProducerCompletion()
        return FakeReadPayload(output.toByteArray(), digest.digest())
    }
}
