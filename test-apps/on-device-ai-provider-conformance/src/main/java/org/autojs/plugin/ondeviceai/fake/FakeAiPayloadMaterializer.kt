package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.OnDeviceAiPayloadPolicy
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.AiToolResultBatch

data class FakeMaterializedRequest(
    val prompt: String,
    val inputBytes: Long,
)

object FakeAiPayloadMaterializer {
    fun materializeRequest(
        request: OnDeviceAiRequest,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): FakeMaterializedRequest {
        var inputBytes = 0L
        val messageParts = buildList {
            request.messages.forEach { message ->
                message.parts.forEach { part ->
                    val bytes = materialize(part.payload, readDescriptor)
                    inputBytes = safeAdd(inputBytes, bytes.size.toLong())
                    add(AiValidation.decodeUtf8(bytes))
                }
            }
        }
        request.tools.forEach { tool ->
            AiValidation.decodeUtf8(materialize(tool.inputSchema, readDescriptor))
        }
        request.options.responseSchema?.let { schema ->
            AiValidation.decodeUtf8(materialize(schema, readDescriptor))
        }
        return FakeMaterializedRequest(
            prompt = messageParts.joinToString(separator = "\n"),
            inputBytes = inputBytes,
        )
    }

    fun materializeToolResults(
        batch: AiToolResultBatch,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): List<String> = batch.results.map { result ->
        AiValidation.decodeUtf8(materialize(result.output, readDescriptor))
    }

    private fun materialize(
        payload: AiPayloadReference,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): ByteArray {
        val bytes = payload.inlineBytes ?: readDescriptor(
            requireNotNull(payload.descriptorIndex),
            payload.declaredLengthBytes,
        )
        OnDeviceAiPayloadPolicy.validateMaterialized(payload, bytes)
        return bytes
    }

    private fun safeAdd(left: Long, right: Long): Long {
        require(right >= 0L && left <= Long.MAX_VALUE - right) { "Materialized byte count overflow" }
        return left + right
    }
}
