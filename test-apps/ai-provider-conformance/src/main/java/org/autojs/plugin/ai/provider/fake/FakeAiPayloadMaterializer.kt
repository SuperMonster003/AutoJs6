package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiProviderPayloadPolicy
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiToolResultBatch
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiProviderImagePolicy

data class FakeMaterializedRequest(
    val prompt: String,
    val inputBytes: Long,
    val imageCount: Int = 0,
)

object FakeAiPayloadMaterializer {
    fun materializeRequest(request: AiProviderRequest, readDescriptor: (Int, Long) -> ByteArray): FakeMaterializedRequest =
        materializeRequest(request, readDescriptor) { _, _ -> error("Image decoder is required") }

    fun materializeRequest(
        request: AiProviderRequest,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
        validateImage: (AiContentPart, ByteArray) -> Unit,
    ): FakeMaterializedRequest {
        var inputBytes = 0L
        val messageParts = buildList {
            request.messages.forEach { message ->
                message.parts.forEach { part ->
                    if (part.isImage) validateImage(part, AiProviderImagePolicy.materialize(part, readDescriptor))
                    else {
                        val bytes = materialize(part.payload, readDescriptor)
                        inputBytes = safeAdd(inputBytes, bytes.size.toLong())
                        add(AiValidation.decodeUtf8(bytes))
                    }
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
            imageCount = request.messages.sumOf { it.parts.count { part -> part.isImage } },
        )
    }

    fun materializeToolResults(batch: AiToolResultBatch, readDescriptor: (Int, Long) -> ByteArray): List<String> =
        materializeToolResults(batch, readDescriptor) { _, _ -> error("Image decoder is required") }

    fun materializeToolResults(
        batch: AiToolResultBatch,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
        validateImage: (AiContentPart, ByteArray) -> Unit,
    ): List<String> = batch.results.map { result ->
        val text = AiValidation.decodeUtf8(materialize(result.output, readDescriptor))
        result.imageParts.forEach { validateImage(it, AiProviderImagePolicy.materialize(it, readDescriptor)) }
        text + if (result.imageParts.isEmpty()) "" else ":images=${result.imageParts.size}"
    }

    private fun materialize(
        payload: AiPayloadReference,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): ByteArray {
        val bytes = payload.inlineBytes ?: readDescriptor(
            requireNotNull(payload.descriptorIndex),
            payload.declaredLengthBytes,
        )
        AiProviderPayloadPolicy.validateMaterialized(payload, bytes)
        return bytes
    }

    private fun safeAdd(left: Long, right: Long): Long {
        require(right >= 0L && left <= Long.MAX_VALUE - right) { "Materialized byte count overflow" }
        return left + right
    }
}
