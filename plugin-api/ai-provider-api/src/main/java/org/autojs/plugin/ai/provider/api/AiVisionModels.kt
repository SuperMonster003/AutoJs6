package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import java.security.MessageDigest
import java.util.Collections

/** Image quotas are independent of the unchanged text/context byte quota. */
class AiVisionCapabilities(
    val maximumImages: Int = AiProviderLimits.MAX_IMAGES,
    val maximumImageBytes: Long = AiProviderLimits.MAX_IMAGE_BYTES,
    val maximumTotalImageBytes: Long = AiProviderLimits.MAX_TOTAL_IMAGE_BYTES,
    val maximumImageEdge: Int = AiProviderLimits.MAX_IMAGE_EDGE,
    val maximumImagePixels: Long = AiProviderLimits.MAX_IMAGE_PIXELS,
    acceptedImageMimeTypes: List<String> = AiProviderMimeType.IMAGE_VALUES.toList(),
) {
    val acceptedImageMimeTypes: List<String> = Collections.unmodifiableList(ArrayList(acceptedImageMimeTypes))

    init {
        require(maximumImages in 1..AiProviderLimits.MAX_IMAGES)
        require(maximumImageBytes in 1..AiProviderLimits.MAX_IMAGE_BYTES)
        require(maximumTotalImageBytes in maximumImageBytes..AiProviderLimits.MAX_TOTAL_IMAGE_BYTES)
        require(maximumImageEdge in 1..AiProviderLimits.MAX_IMAGE_EDGE)
        require(maximumImagePixels in 1..AiProviderLimits.MAX_IMAGE_PIXELS)
        require(this.acceptedImageMimeTypes.isNotEmpty())
        require(this.acceptedImageMimeTypes.distinct().size == this.acceptedImageMimeTypes.size)
        require(this.acceptedImageMimeTypes.all { it in AiProviderMimeType.IMAGE_VALUES })
    }

    fun intersect(other: AiVisionCapabilities): AiVisionCapabilities? {
        val mimes = acceptedImageMimeTypes.filter { it in other.acceptedImageMimeTypes }
        if (mimes.isEmpty()) return null
        return AiVisionCapabilities(minOf(maximumImages, other.maximumImages),
            minOf(maximumImageBytes, other.maximumImageBytes),
            minOf(maximumTotalImageBytes, other.maximumTotalImageBytes),
            minOf(maximumImageEdge, other.maximumImageEdge), minOf(maximumImagePixels, other.maximumImagePixels), mimes)
    }

    private fun fields() = listOf(maximumImages, maximumImageBytes, maximumTotalImageBytes,
        maximumImageEdge, maximumImagePixels, acceptedImageMimeTypes)
    override fun equals(other: Any?) = other is AiVisionCapabilities && fields() == other.fields()
    override fun hashCode() = fields().hashCode()
    override fun toString() = "AiVisionCapabilities(maximumImages=$maximumImages, maximumImageBytes=$maximumImageBytes, " +
        "maximumTotalImageBytes=$maximumTotalImageBytes, maximumImageEdge=$maximumImageEdge, " +
        "maximumImagePixels=$maximumImagePixels, acceptedImageMimeTypes=$acceptedImageMimeTypes)"
}

data class AiImageMetadata(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
    init {
        require(width in 1..AiProviderLimits.MAX_IMAGE_EDGE && height in 1..AiProviderLimits.MAX_IMAGE_EDGE)
        require(pixels <= AiProviderLimits.MAX_IMAGE_PIXELS)
    }
}

data class AiImageQuotaSnapshot(val count: Int, val bytes: Long)

object AiProviderImagePolicy {
    fun requireProtocol(version: AiProtocolVersion) {
        require(version.major == 2 && version >= AiProviderProtocol.PROTOCOL_V2_1) {
            "Image input requires negotiated AI Provider 2.1"
        }
    }

    /** Called before opening any descriptor. Decoded image bounds must also be checked by each receiver. */
    fun validate(parts: List<AiContentPart>, capabilities: AiVisionCapabilities): AiImageQuotaSnapshot {
        require(parts.size <= capabilities.maximumImages) { "Image count exceeds capability" }
        var total = 0L
        parts.forEach { part ->
            val image = requireNotNull(part.image) { "Image metadata is missing" }
            require(part.payload.mimeType in capabilities.acceptedImageMimeTypes)
            require(part.payload.declaredLengthBytes <= capabilities.maximumImageBytes)
            require(image.width <= capabilities.maximumImageEdge && image.height <= capabilities.maximumImageEdge)
            require(image.pixels <= capabilities.maximumImagePixels)
            total += part.payload.declaredLengthBytes
        }
        require(total <= capabilities.maximumTotalImageBytes) { "Image bytes exceed capability" }
        return AiImageQuotaSnapshot(parts.size, total)
    }

    fun materialize(part: AiContentPart, readDescriptor: (Int, Long) -> ByteArray): ByteArray {
        require(part.isImage)
        val payload = part.payload
        val bytes = readDescriptor(requireNotNull(payload.descriptorIndex), payload.declaredLengthBytes)
        require(bytes.size.toLong() == payload.declaredLengthBytes) { "Image length does not match" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == payload.sha256) { "Image digest does not match" }
        return bytes
    }
}
