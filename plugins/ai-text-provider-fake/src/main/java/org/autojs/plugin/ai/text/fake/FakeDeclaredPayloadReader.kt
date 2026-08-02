package org.autojs.plugin.ai.text.fake

import org.autojs.plugin.ai.common.api.AiCommonLimits
import java.io.InputStream

object FakeDeclaredPayloadReader {
    fun readExactly(
        input: InputStream,
        declaredLengthBytes: Long,
        maximumLengthBytes: Long = AiCommonLimits.MAX_PAYLOAD_BYTES,
        verifyProducerCompletion: () -> Unit = {},
    ): ByteArray {
        require(maximumLengthBytes in 0L..AiCommonLimits.MAX_PAYLOAD_BYTES)
        require(declaredLengthBytes in 0L..maximumLengthBytes) { "Descriptor payload is too large" }
        require(declaredLengthBytes <= Int.MAX_VALUE.toLong()) { "Descriptor payload cannot be materialized" }
        val output = ByteArray(declaredLengthBytes.toInt())
        var offset = 0
        while (offset < output.size) {
            val count = input.read(output, offset, output.size - offset)
            require(count >= 0) { "Descriptor payload ended before its declared length" }
            if (count == 0) {
                val single = input.read()
                require(single >= 0) { "Descriptor payload ended before its declared length" }
                output[offset] = single.toByte()
                offset += 1
            } else {
                offset += count
            }
        }
        require(input.read() == -1) { "Descriptor payload exceeds its declared length" }
        verifyProducerCompletion()
        return output
    }
}
