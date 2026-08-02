package org.autojs.plugin.ai.common.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object AiCommonCodec {
    fun encodeProtocolRange(value: AiProtocolRange): ByteArray =
        TaggedWireWriter(AiCommonProtocol.SCHEMA_PROTOCOL_RANGE, 1, 0)
            .int32(1, value.minimum.major, true)
            .int32(2, value.minimum.minor, true)
            .int32(3, value.maximum.major, true)
            .int32(4, value.maximum.minor, true)
            .encode()

    fun decodeProtocolRange(bytes: ByteArray): AiProtocolRange {
        val document = document(bytes, AiCommonProtocol.SCHEMA_PROTOCOL_RANGE, setOf(1, 2, 3, 4))
        return AiProtocolRange(
            minimum = AiProtocolVersion(document.requireInt32(1), document.requireInt32(2)),
            maximum = AiProtocolVersion(document.requireInt32(3), document.requireInt32(4)),
        )
    }

    fun encodeProviderInfo(value: AiProviderInfo): ByteArray =
        TaggedWireWriter(AiCommonProtocol.SCHEMA_PROVIDER_INFO, 1, 0)
            .string(1, value.providerId, true)
            .string(2, value.displayName, true)
            .string(3, value.implementationVersionName, true)
            .int64(4, value.implementationVersionCode, true)
            .document(5, encodeProtocolRange(value.protocolRange), true)
            .int32(6, value.locality, true)
            .int32(7, value.credentialMode, true)
            .strings(8, value.declaredHttpsOrigins)
            .strings(9, value.supportedAbis)
            .int64(10, value.minimumHostVersionCode, true)
            .apply { value.maximumHostVersionCode?.let { int64(11, it) } }
            .encode()

    fun decodeProviderInfo(bytes: ByteArray): AiProviderInfo {
        val document = document(
            bytes,
            AiCommonProtocol.SCHEMA_PROVIDER_INFO,
            knownTags = (1..11).toSet(),
            repeatedTags = setOf(8, 9),
        )
        return AiProviderInfo(
            providerId = document.requireString(1),
            displayName = document.requireString(2),
            implementationVersionName = document.requireString(3),
            implementationVersionCode = document.requireInt64(4),
            protocolRange = decodeProtocolRange(document.requireDocument(5)),
            locality = document.requireInt32(6),
            credentialMode = document.requireInt32(7),
            declaredHttpsOrigins = document.strings(8),
            supportedAbis = document.strings(9),
            minimumHostVersionCode = document.requireInt64(10),
            maximumHostVersionCode = document.optionalInt64(11),
        )
    }

    fun encodeError(value: AiError): ByteArray =
        TaggedWireWriter(AiCommonProtocol.SCHEMA_ERROR, 1, 0)
            .int32(1, value.code, true)
            .apply { value.message?.let { string(2, it) } }
            .int32(3, value.retryDisposition, true)
            .apply { value.providerCode?.let { string(4, it) } }
            .encode()

    fun decodeError(bytes: ByteArray): AiError {
        val document = document(bytes, AiCommonProtocol.SCHEMA_ERROR, setOf(1, 2, 3, 4))
        return AiError(
            code = document.requireInt32(1),
            message = document.optionalString(2),
            retryDisposition = document.requireInt32(3),
            providerCode = document.optionalString(4),
        )
    }

    fun encodeUsage(value: AiUsage): ByteArray = TaggedWireWriter(AiCommonProtocol.SCHEMA_USAGE, 1, 0)
        .apply { value.inputTokens?.let { int64(1, it) } }
        .apply { value.outputTokens?.let { int64(2, it) } }
        .apply { value.totalTokens?.let { int64(3, it) } }
        .apply { value.reasoningTokens?.let { int64(4, it) } }
        .apply { value.cachedInputTokens?.let { int64(5, it) } }
        .encode()

    fun decodeUsage(bytes: ByteArray): AiUsage {
        val document = document(bytes, AiCommonProtocol.SCHEMA_USAGE, (1..5).toSet())
        return AiUsage(
            inputTokens = document.optionalInt64(1),
            outputTokens = document.optionalInt64(2),
            totalTokens = document.optionalInt64(3),
            reasoningTokens = document.optionalInt64(4),
            cachedInputTokens = document.optionalInt64(5),
        )
    }

    fun encodePayloadReference(value: AiPayloadReference): ByteArray =
        TaggedWireWriter(AiCommonProtocol.SCHEMA_PAYLOAD_REFERENCE, 1, 0)
            .string(1, value.mimeType, true)
            .int64(2, value.declaredLengthBytes, true)
            .apply { value.sha256?.let { string(3, it) } }
            .apply { value.inlineBytes?.let { bytes(4, it) } }
            .apply { value.descriptorIndex?.let { fileDescriptorRef(5, it) } }
            .apply { value.charset?.let { string(6, it) } }
            .encode()

    fun decodePayloadReference(bytes: ByteArray): AiPayloadReference {
        val document = document(bytes, AiCommonProtocol.SCHEMA_PAYLOAD_REFERENCE, (1..6).toSet())
        return AiPayloadReference(
            mimeType = document.requireString(1),
            declaredLengthBytes = document.requireInt64(2),
            sha256 = document.optionalString(3),
            inlineBytes = document.optionalBytes(4),
            descriptorIndex = document.optionalFileDescriptorRef(5),
            charset = document.optionalString(6),
        )
    }

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes)
        .requireSchema(schemaId, 1)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)
}
