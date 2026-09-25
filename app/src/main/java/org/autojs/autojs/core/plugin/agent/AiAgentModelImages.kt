package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonElement
import org.autojs.autojs.core.plugin.ai.AiPluginImage
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.common.api.AiDescriptorReferencePolicy
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.*

internal class AiAgentModelImageRef(val messageIndex: Int?, val part: AiContentPart) {
    val descriptorIndex: Int get() = checkNotNull(part.payload.descriptorIndex)
    val estimatedTokens: Long get() = AiPluginImage.estimateTokens(checkNotNull(part.image))
    override fun toString() = "AiAgentModelImageRef(messageIndex=$messageIndex, metadata=${part.image}, bytes=${part.payload.declaredLengthBytes})"
}

internal object AiAgentModelImages {
    fun parse(value: JsonElement?, withMessageIndex: Boolean): List<AiAgentModelImageRef> {
        if (value == null || value.isJsonNull) return emptyList()
        require(value.isJsonArray && value.asJsonArray.size() <= AiAgentContract.MAX_MODEL_IMAGES)
        val fields = setOf("descriptorIndex", "mimeType", "byteLength", "width", "height", "sha256") +
            if (withMessageIndex) setOf("messageIndex") else emptySet()
        return value.asJsonArray.map { item ->
            require(item.isJsonObject && item.asJsonObject.keySet() == fields)
            val row = item.asJsonObject
            fun number(key: String): Long {
                val field = row[key]
                require(field.isJsonPrimitive && field.asJsonPrimitive.isNumber)
                return field.asBigDecimal.longValueExact()
            }
            fun integer(key: String): Int = number(key).also { require(it in 0..Int.MAX_VALUE) }.toInt()
            fun string(key: String): String {
                val field = row[key]
                require(field.isJsonPrimitive && field.asJsonPrimitive.isString)
                return field.asString
            }
            AiAgentModelImageRef(if (withMessageIndex) integer("messageIndex") else null,
                AiContentPart(AiPayloadReference(string("mimeType"), number("byteLength"),
                    sha256 = string("sha256"), descriptorIndex = integer("descriptorIndex")),
                    AiImageMetadata(integer("width"), integer("height"))))
        }.also { validate(it, it.size, exact = false) }
    }

    fun validate(refs: List<AiAgentModelImageRef>, descriptorCount: Int, exact: Boolean = true) {
        AiProviderImagePolicy.validate(refs.map { it.part }, AiVisionCapabilities())
        if (exact) AiDescriptorReferencePolicy.validateExactReferences(refs.map { it.descriptorIndex },
            descriptorCount, AiAgentContract.MAX_MODEL_IMAGES)
    }
}
