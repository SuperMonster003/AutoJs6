package org.autojs.autojs.core.plugin.ai

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.*
import java.io.File
import java.security.MessageDigest

/** Immutable encoded image; raw bytes, hashes and paths are excluded from diagnostics. */
internal class AiPluginImage(bytes: ByteArray, val mimeType: String, val metadata: AiImageMetadata) {
    private val encoded = bytes.copyOf()
    val byteCount: Long get() = encoded.size.toLong()
    val estimatedTokens: Long get() = estimateTokens(metadata)
    private val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    init {
        require(mimeType in AiProviderMimeType.IMAGE_VALUES)
        require(byteCount in 1..AiProviderLimits.MAX_IMAGE_BYTES)
    }

    fun contentPart(index: Int) = AiContentPart(AiPayloadReference(mimeType, byteCount,
        sha256 = digest, descriptorIndex = index), metadata)

    fun openDescriptor(directory: File): ParcelFileDescriptor {
        val file = File.createTempFile("ai-image-", ".bin", directory)
        try {
            file.outputStream().use { it.write(encoded) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    companion object {
        fun estimateTokens(metadata: AiImageMetadata): Long =
            1024L + 4L * ((metadata.width + 31) / 32) * ((metadata.height + 31) / 32)
    }

    override fun toString() = "AiPluginImage(mimeType=$mimeType, bytes=$byteCount, metadata=$metadata)"
}

internal object AiPluginImageDecoder {
    fun decode(bytes: ByteArray, mimeType: String, metadata: AiImageMetadata): AiPluginImage {
        require(bytes.size.toLong() in 1..AiProviderLimits.MAX_IMAGE_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == mimeType && bounds.outWidth == metadata.width && bounds.outHeight == metadata.height) {
            "Decoded image does not match its declaration"
        }
        // Check actual decodability with bounded sampling; never allocate a full 4096-square bitmap.
        val options = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(metadata.width, metadata.height) / 128)
        }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Invalid image" }
        bitmap.recycle()
        return AiPluginImage(bytes, mimeType, metadata)
    }
}

/** No descriptor is opened while a continuation is queued or already cancelled. */
internal class AiPluginImageDescriptors(images: List<AiPluginImage>, private val directory: File) : AiProviderSessionRequestDescriptorFactory {
    private val images = images.toList()
    override val count get() = images.size
    override fun open(): AiProviderSessionRequestDescriptorLease {
        val opened = mutableListOf<ParcelFileDescriptor>()
        try {
            images.forEach { opened += it.openDescriptor(directory) }
            return AndroidAiProviderSessionRequestDescriptors(opened.toTypedArray())
        } catch (failure: Throwable) {
            opened.forEach { runCatching { it.close() } }
            throw failure
        }
    }
}
