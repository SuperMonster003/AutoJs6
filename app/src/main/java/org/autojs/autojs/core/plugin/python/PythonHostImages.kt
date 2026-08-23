package org.autojs.autojs.core.plugin.python

import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Looper
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs

/** Fixed bounds for execution-local Python screenshot transfer. */
internal object PythonHostImagePolicy {
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val MAX_CHUNK_BYTES = 32 * 1024
    const val MAX_DIMENSION = 8_192
    const val MAX_PIXELS = 16 * 1024 * 1024
    const val MIN_QUALITY = 1
    const val MAX_QUALITY = 100
    const val MAX_IMAGE_ID_BYTES = 64
    const val MAX_COLOR = 0x00ff_ffff
    const val MAX_COLOR_THRESHOLD = 255
    const val MAX_TEMPLATE_BYTES = 1024 * 1024
    const val MAX_TEMPLATE_CHUNK_BYTES = 24 * 1024
    const val MAX_TEMPLATE_CHUNK_BASE64_BYTES = MAX_TEMPLATE_CHUNK_BYTES / 3 * 4
    const val MAX_TEMPLATE_DIMENSION = 2_048
    const val MAX_TEMPLATE_PIXELS = 1024 * 1024
    const val MAX_TEMPLATE_MATCH_REGION_PIXELS = 4 * 1024 * 1024
    const val MAX_TEMPLATE_MATCH_COMPARISONS = 16 * 1024 * 1024
    const val MAX_TEMPLATE_ANCHORS = 16
    const val MAX_TEMPLATE_ID_BYTES = 64

    val FORMATS = setOf("png", "jpeg")
    val IMAGE_ID = Regex("image-[1-9][0-9]*")
    val TEMPLATE_ID = Regex("template-[1-9][0-9]*")
    val SHA256 = Regex("[0-9a-f]{64}")
}

internal data class PythonHostImageRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0 && y >= 0) { "Python Host image region origin is invalid" }
        require(width > 0 && height > 0) { "Python Host image region size is invalid" }
        require(
            x.toLong() + width <= PythonHostImagePolicy.MAX_DIMENSION &&
                y.toLong() + height <= PythonHostImagePolicy.MAX_DIMENSION,
        ) { "Python Host image region exceeds the fixed dimension bound" }
    }
}

internal data class PythonHostColorMatch(
    val found: Boolean,
    val x: Int,
    val y: Int,
) {
    init {
        require(
            (found && x >= 0 && y >= 0) || (!found && x == NOT_FOUND && y == NOT_FOUND),
        ) { "Python Host color match coordinates are inconsistent" }
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("found", found)
        addProperty("x", x)
        addProperty("y", y)
    }

    companion object {
        const val NOT_FOUND = -1
        private const val SCHEMA = "autojs6-python-color-match-v1"

        fun found(x: Int, y: Int) = PythonHostColorMatch(true, x, y)

        fun missing() = PythonHostColorMatch(false, NOT_FOUND, NOT_FOUND)
    }
}

internal interface PythonHostPixelSource {
    val width: Int
    val height: Int

    fun readRow(x: Int, y: Int, width: Int, destination: IntArray)
}

/** Bounded row-major RGB search which never retains Android image objects. */
internal object PythonHostColorSearch {
    fun find(
        bitmap: Bitmap,
        color: Int,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostColorMatch = find(
        source = object : PythonHostPixelSource {
            override val width = bitmap.width
            override val height = bitmap.height

            override fun readRow(x: Int, y: Int, width: Int, destination: IntArray) {
                bitmap.getPixels(destination, 0, width, x, y, width, 1)
            }
        },
        color = color,
        threshold = threshold,
        region = region,
    )

    fun find(
        source: PythonHostPixelSource,
        color: Int,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostColorMatch {
        require(color in 0..PythonHostImagePolicy.MAX_COLOR) {
            "Python Host color is outside the RGB range"
        }
        require(threshold in 0..PythonHostImagePolicy.MAX_COLOR_THRESHOLD) {
            "Python Host color threshold is invalid"
        }
        require(
            source.width in 1..PythonHostImagePolicy.MAX_DIMENSION &&
                source.height in 1..PythonHostImagePolicy.MAX_DIMENSION &&
                source.width.toLong() * source.height <= PythonHostImagePolicy.MAX_PIXELS,
        ) { "Python Host pixel source exceeds its fixed bounds" }
        val search = region ?: PythonHostImageRegion(0, 0, source.width, source.height)
        require(
            search.x.toLong() + search.width <= source.width &&
                search.y.toLong() + search.height <= source.height,
        ) { "Python Host color search region exceeds the screenshot" }

        val targetRed = color ushr 16 and 0xff
        val targetGreen = color ushr 8 and 0xff
        val targetBlue = color and 0xff
        val row = IntArray(search.width)
        try {
            for (y in search.y until search.y + search.height) {
                source.readRow(search.x, y, search.width, row)
                row.forEachIndexed { offset, pixel ->
                    if (
                        abs((pixel ushr 16 and 0xff) - targetRed) <= threshold &&
                        abs((pixel ushr 8 and 0xff) - targetGreen) <= threshold &&
                        abs((pixel and 0xff) - targetBlue) <= threshold
                    ) {
                        return PythonHostColorMatch.found(search.x + offset, y)
                    }
                }
            }
            return PythonHostColorMatch.missing()
        } finally {
            row.fill(0)
        }
    }
}

internal data class PythonHostDecodedImageTemplate(
    val width: Int,
    val height: Int,
    /** Ownership transfers to [PythonHostImageTemplateScope]. */
    val pixels: IntArray,
)

internal class PythonHostImageTemplate private constructor(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val opaqueOffsets: IntArray,
    val anchorOffsets: IntArray,
) {
    fun clear() {
        pixels.fill(0)
        opaqueOffsets.fill(0)
        anchorOffsets.fill(0)
    }

    companion object {
        fun fromDecoded(decoded: PythonHostDecodedImageTemplate): PythonHostImageTemplate {
            if (decoded.width <= 0 || decoded.height <= 0) {
                throw PythonHostCapabilityInvalidImageTemplateException()
            }
            if (
                decoded.width > PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION ||
                decoded.height > PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION ||
                decoded.width.toLong() * decoded.height > PythonHostImagePolicy.MAX_TEMPLATE_PIXELS
            ) {
                throw PythonHostCapabilityResultLimitException()
            }
            if (decoded.pixels.size != decoded.width * decoded.height) {
                throw PythonHostCapabilityInvalidImageTemplateException()
            }
            val opaqueOffsets = IntArray(decoded.pixels.count(::isOpaque))
            var opaqueIndex = 0
            decoded.pixels.forEachIndexed { offset, pixel ->
                if (isOpaque(pixel)) opaqueOffsets[opaqueIndex++] = offset
            }
            if (opaqueOffsets.isEmpty()) {
                throw PythonHostCapabilityInvalidImageTemplateException()
            }
            val anchorCount = minOf(PythonHostImagePolicy.MAX_TEMPLATE_ANCHORS, opaqueOffsets.size)
            val anchors = IntArray(anchorCount)
            if (anchorCount == 1) {
                anchors[0] = opaqueOffsets[0]
            } else {
                anchors.indices.forEach { index ->
                    anchors[index] = opaqueOffsets[
                        index * (opaqueOffsets.size - 1) / (anchorCount - 1)
                    ]
                }
            }
            return PythonHostImageTemplate(
                width = decoded.width,
                height = decoded.height,
                pixels = decoded.pixels,
                opaqueOffsets = opaqueOffsets,
                anchorOffsets = anchors,
            )
        }

        private fun isOpaque(pixel: Int): Boolean = pixel ushr 24 == 0xff
    }
}

internal data class PythonHostImageTemplateDescriptor(
    val id: String,
    val format: String,
    val byteLength: Int,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("id", id)
        addProperty("format", format)
        addProperty("byteLength", byteLength)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-image-template-v1"
    }
}

internal data class PythonHostImageTemplateChunkAck(
    val templateId: String,
    val offset: Int,
    val nextOffset: Int,
    val complete: Boolean,
    val width: Int,
    val height: Int,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("templateId", templateId)
        addProperty("offset", offset)
        addProperty("nextOffset", nextOffset)
        addProperty("complete", complete)
        addProperty("width", width)
        addProperty("height", height)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-image-template-chunk-v1"
    }
}

internal data class PythonHostImageMatch(
    val found: Boolean,
    val x: Int,
    val y: Int,
) {
    init {
        require(
            (found && x >= 0 && y >= 0) || (!found && x == NOT_FOUND && y == NOT_FOUND),
        ) { "Python Host image match coordinates are inconsistent" }
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("found", found)
        addProperty("x", x)
        addProperty("y", y)
    }

    companion object {
        const val NOT_FOUND = -1
        private const val SCHEMA = "autojs6-python-image-match-v1"

        fun found(x: Int, y: Int) = PythonHostImageMatch(true, x, y)

        fun missing() = PythonHostImageMatch(false, NOT_FOUND, NOT_FOUND)
    }
}

/** Owns at most one bounded encoded upload or decoded template for a Python execution. */
internal class PythonHostImageTemplateScope(
    private val decode: (format: String, bytes: ByteArray) -> PythonHostDecodedImageTemplate =
        PythonHostImageTemplateDecoder::decode,
) : AutoCloseable {
    private var pending: PendingTemplate? = null
    private var retained: RetainedTemplate? = null
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun begin(format: String, byteLength: Int, sha256: String): PythonHostImageTemplateDescriptor {
        check(!closed) { "Python Host image template scope is closed" }
        require(format in PythonHostImagePolicy.FORMATS) { "Python Host template format is invalid" }
        require(byteLength in 1..PythonHostImagePolicy.MAX_TEMPLATE_BYTES) {
            "Python Host template byte length is invalid"
        }
        require(PythonHostImagePolicy.SHA256.matches(sha256)) {
            "Python Host template SHA-256 is invalid"
        }

        clearRetained()
        generation += 1L
        val descriptor = PythonHostImageTemplateDescriptor(
            id = "template-$generation",
            format = format,
            byteLength = byteLength,
        )
        val bytes = try {
            ByteArray(byteLength)
        } catch (error: OutOfMemoryError) {
            throw PythonHostCapabilityResultLimitException(error)
        }
        pending = PendingTemplate(descriptor, sha256, bytes)
        return descriptor
    }

    @Synchronized
    fun writeChunk(templateId: String, offset: Int, data: String): PythonHostImageTemplateChunkAck {
        check(!closed) { "Python Host image template scope is closed" }
        val upload = pending
            ?.takeIf { it.descriptor.id == templateId }
            ?: throw PythonHostCapabilityStaleTemplateException()
        var decodedChunk = ByteArray(0)
        try {
            require(offset == upload.nextOffset) { "Python Host template chunk offset is not contiguous" }
            decodedChunk = runCatching { Base64.getDecoder().decode(data) }
                .getOrElse { error ->
                    throw IllegalArgumentException("Python Host template chunk is not Base64", error)
                }
            require(Base64.getEncoder().encodeToString(decodedChunk) == data) {
                "Python Host template chunk Base64 is not canonical"
            }
            require(decodedChunk.size in 1..PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BYTES) {
                "Python Host template chunk size is invalid"
            }
            require(offset <= upload.bytes.size - decodedChunk.size) {
                "Python Host template chunk exceeds its declared byte length"
            }
            val nextOffset = offset + decodedChunk.size
            if (nextOffset < upload.bytes.size) {
                require(decodedChunk.size == PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BYTES) {
                    "Python Host template upload has a short non-final chunk"
                }
            }
            decodedChunk.copyInto(upload.bytes, destinationOffset = offset)
            upload.nextOffset = nextOffset
            if (nextOffset < upload.bytes.size) {
                return PythonHostImageTemplateChunkAck(
                    templateId = templateId,
                    offset = offset,
                    nextOffset = nextOffset,
                    complete = false,
                    width = 0,
                    height = 0,
                )
            }

            if (sha256(upload.bytes) != upload.sha256) {
                throw PythonHostCapabilityInvalidImageTemplateException()
            }
            val decoded = decode.invoke(upload.descriptor.format, upload.bytes)
            var templateOwnershipTransferred = false
            try {
                val template = PythonHostImageTemplate.fromDecoded(decoded)
                retained = RetainedTemplate(upload.descriptor, template)
                templateOwnershipTransferred = true
                pending = null
                upload.bytes.fill(0)
                return PythonHostImageTemplateChunkAck(
                    templateId = templateId,
                    offset = offset,
                    nextOffset = nextOffset,
                    complete = true,
                    width = template.width,
                    height = template.height,
                )
            } finally {
                if (!templateOwnershipTransferred) decoded.pixels.fill(0)
            }
        } catch (error: Throwable) {
            clearRetained()
            throw error
        } finally {
            decodedChunk.fill(0)
        }
    }

    @Synchronized
    fun find(
        templateId: String,
        source: PythonHostPixelSource,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostImageMatch {
        check(!closed) { "Python Host image template scope is closed" }
        val template = retained
            ?.takeIf { it.descriptor.id == templateId }
            ?.template
            ?: throw PythonHostCapabilityStaleTemplateException()
        return PythonHostTemplateSearch.find(source, template, threshold, region)
    }

    @Synchronized
    fun release(templateId: String) {
        check(!closed) { "Python Host image template scope is closed" }
        if (pending?.descriptor?.id != templateId && retained?.descriptor?.id != templateId) {
            throw PythonHostCapabilityStaleTemplateException()
        }
        clearRetained()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        clearRetained()
    }

    private fun clearRetained() {
        pending?.bytes?.fill(0)
        pending = null
        retained?.template?.clear()
        retained = null
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val encoded = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xff
            encoded[index * 2] = HEX[unsigned ushr 4]
            encoded[index * 2 + 1] = HEX[unsigned and 0x0f]
        }
        digest.fill(0)
        return String(encoded)
    }

    private data class PendingTemplate(
        val descriptor: PythonHostImageTemplateDescriptor,
        val sha256: String,
        val bytes: ByteArray,
        var nextOffset: Int = 0,
    )

    private data class RetainedTemplate(
        val descriptor: PythonHostImageTemplateDescriptor,
        val template: PythonHostImageTemplate,
    )

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

internal object PythonHostImageTemplateDecoder {
    fun decode(format: String, bytes: ByteArray): PythonHostDecodedImageTemplate {
        if (!hasSignature(bytes, format)) {
            throw PythonHostCapabilityInvalidImageTemplateException()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        } catch (error: OutOfMemoryError) {
            throw PythonHostCapabilityResultLimitException(error)
        } catch (error: RuntimeException) {
            throw PythonHostCapabilityInvalidImageTemplateException(error)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PythonHostCapabilityInvalidImageTemplateException()
        }
        if (
            bounds.outWidth > PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION ||
            bounds.outHeight > PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight > PythonHostImagePolicy.MAX_TEMPLATE_PIXELS
        ) {
            throw PythonHostCapabilityResultLimitException()
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw PythonHostCapabilityInvalidImageTemplateException()
        } catch (error: OutOfMemoryError) {
            throw PythonHostCapabilityResultLimitException(error)
        } catch (error: PythonHostCapabilityInvalidImageTemplateException) {
            throw error
        } catch (error: RuntimeException) {
            throw PythonHostCapabilityInvalidImageTemplateException(error)
        }
        var pixels = IntArray(0)
        try {
            if (bitmap.width != bounds.outWidth || bitmap.height != bounds.outHeight) {
                throw PythonHostCapabilityInvalidImageTemplateException()
            }
            pixels = try {
                IntArray(bitmap.width * bitmap.height)
            } catch (error: OutOfMemoryError) {
                throw PythonHostCapabilityResultLimitException(error)
            }
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return PythonHostDecodedImageTemplate(bitmap.width, bitmap.height, pixels).also {
                pixels = IntArray(0)
            }
        } finally {
            pixels.fill(0)
            bitmap.recycle()
        }
    }

    private fun hasSignature(bytes: ByteArray, format: String): Boolean = when (format) {
        "png" -> bytes.size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
        "jpeg" -> bytes.size >= 4 &&
            bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() &&
            bytes[bytes.lastIndex - 1] == 0xff.toByte() &&
            bytes[bytes.lastIndex] == 0xd9.toByte()
        else -> false
    }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4e,
        0x47,
        0x0d,
        0x0a,
        0x1a,
        0x0a,
    )
}

/** Deterministic row-major masked RGB template search with a hard comparison budget. */
internal object PythonHostTemplateSearch {
    fun find(
        bitmap: Bitmap,
        template: PythonHostImageTemplate,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostImageMatch = find(
        source = object : PythonHostPixelSource {
            override val width = bitmap.width
            override val height = bitmap.height

            override fun readRow(x: Int, y: Int, width: Int, destination: IntArray) {
                bitmap.getPixels(destination, 0, width, x, y, width, 1)
            }
        },
        template = template,
        threshold = threshold,
        region = region,
    )

    fun find(
        source: PythonHostPixelSource,
        template: PythonHostImageTemplate,
        threshold: Int,
        region: PythonHostImageRegion?,
        maximumComparisons: Int = PythonHostImagePolicy.MAX_TEMPLATE_MATCH_COMPARISONS,
    ): PythonHostImageMatch {
        require(threshold in 0..PythonHostImagePolicy.MAX_COLOR_THRESHOLD) {
            "Python Host image threshold is invalid"
        }
        require(maximumComparisons > 0) { "Python Host image comparison budget is invalid" }
        require(
            source.width in 1..PythonHostImagePolicy.MAX_DIMENSION &&
                source.height in 1..PythonHostImagePolicy.MAX_DIMENSION &&
                source.width.toLong() * source.height <= PythonHostImagePolicy.MAX_PIXELS,
        ) { "Python Host pixel source exceeds its fixed bounds" }
        val search = region ?: PythonHostImageRegion(0, 0, source.width, source.height)
        require(
            search.x.toLong() + search.width <= source.width &&
                search.y.toLong() + search.height <= source.height,
        ) { "Python Host image search region exceeds the screenshot" }
        if (search.width.toLong() * search.height > PythonHostImagePolicy.MAX_TEMPLATE_MATCH_REGION_PIXELS) {
            throw PythonHostCapabilityResultLimitException()
        }
        require(template.width <= search.width && template.height <= search.height) {
            "Python Host template does not fit the image search region"
        }

        val screenPixels = try {
            IntArray(search.width * search.height)
        } catch (error: OutOfMemoryError) {
            throw PythonHostCapabilityResultLimitException(error)
        }
        val row = IntArray(search.width)
        try {
            repeat(search.height) { rowOffset ->
                source.readRow(search.x, search.y + rowOffset, search.width, row)
                row.copyInto(screenPixels, destinationOffset = rowOffset * search.width)
            }
            var comparisons = 0
            val candidateWidth = search.width - template.width + 1
            val candidateHeight = search.height - template.height + 1
            for (candidateY in 0 until candidateHeight) {
                for (candidateX in 0 until candidateWidth) {
                    var anchorsMatch = true
                    for (templateOffset in template.anchorOffsets) {
                        comparisons += 1
                        if (comparisons > maximumComparisons) {
                            throw PythonHostCapabilityResultLimitException()
                        }
                        if (!matches(screenPixels, search.width, candidateX, candidateY, template, templateOffset, threshold)) {
                            anchorsMatch = false
                            break
                        }
                    }
                    if (!anchorsMatch) continue

                    var templateMatches = true
                    for (templateOffset in template.opaqueOffsets) {
                        comparisons += 1
                        if (comparisons > maximumComparisons) {
                            throw PythonHostCapabilityResultLimitException()
                        }
                        if (!matches(screenPixels, search.width, candidateX, candidateY, template, templateOffset, threshold)) {
                            templateMatches = false
                            break
                        }
                    }
                    if (templateMatches) {
                        return PythonHostImageMatch.found(search.x + candidateX, search.y + candidateY)
                    }
                }
            }
            return PythonHostImageMatch.missing()
        } finally {
            row.fill(0)
            screenPixels.fill(0)
        }
    }

    private fun matches(
        screenPixels: IntArray,
        screenWidth: Int,
        candidateX: Int,
        candidateY: Int,
        template: PythonHostImageTemplate,
        templateOffset: Int,
        threshold: Int,
    ): Boolean {
        val templateY = templateOffset / template.width
        val templateX = templateOffset - templateY * template.width
        val screenPixel = screenPixels[(candidateY + templateY) * screenWidth + candidateX + templateX]
        val templatePixel = template.pixels[templateOffset]
        return abs((screenPixel ushr 16 and 0xff) - (templatePixel ushr 16 and 0xff)) <= threshold &&
            abs((screenPixel ushr 8 and 0xff) - (templatePixel ushr 8 and 0xff)) <= threshold &&
            abs((screenPixel and 0xff) - (templatePixel and 0xff)) <= threshold
    }
}

internal data class PythonHostCapturedScreenImage(
    val format: String,
    val width: Int,
    val height: Int,
    /** Ownership transfers to [PythonHostScreenImageScope]. */
    val bytes: ByteArray,
)

internal data class PythonHostScreenImageDescriptor(
    val id: String,
    val format: String,
    val width: Int,
    val height: Int,
    val byteLength: Int,
    val sha256: String,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("id", id)
        addProperty("format", format)
        addProperty("width", width)
        addProperty("height", height)
        addProperty("byteLength", byteLength)
        addProperty("sha256", sha256)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-screen-image-v1"
    }
}

internal data class PythonHostScreenImageChunk(
    val imageId: String,
    val offset: Int,
    val data: String,
    val nextOffset: Int,
    val eof: Boolean,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        addProperty("imageId", imageId)
        addProperty("offset", offset)
        addProperty("data", data)
        addProperty("nextOffset", nextOffset)
        addProperty("eof", eof)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-screen-image-chunk-v1"
    }
}

/**
 * Owns at most one encoded screenshot for a Python execution. Replacing, releasing, or closing the
 * scope overwrites retained bytes before dropping the reference.
 */
internal class PythonHostScreenImageScope(
    private val capture: (format: String, quality: Int) -> PythonHostCapturedScreenImage,
) : AutoCloseable {
    private var retained: RetainedImage? = null
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun capture(format: String, quality: Int): PythonHostScreenImageDescriptor {
        check(!closed) { "Python Host screen image scope is closed" }
        require(format in PythonHostImagePolicy.FORMATS) { "Python Host image format is invalid" }
        require(quality in PythonHostImagePolicy.MIN_QUALITY..PythonHostImagePolicy.MAX_QUALITY) {
            "Python Host image quality is invalid"
        }

        clearRetained()
        generation += 1L
        val captured = capture.invoke(format, quality)
        var retainedOwnership = false
        try {
            check(captured.format == format) { "Python Host image format changed during capture" }
            if (captured.width <= 0 || captured.height <= 0) {
                throw PythonHostCapabilityScreenCaptureFailedException()
            }
            if (
                captured.width > PythonHostImagePolicy.MAX_DIMENSION ||
                captured.height > PythonHostImagePolicy.MAX_DIMENSION ||
                captured.width.toLong() * captured.height > PythonHostImagePolicy.MAX_PIXELS
            ) {
                throw PythonHostCapabilityResultLimitException()
            }
            if (captured.bytes.size !in 1..PythonHostImagePolicy.MAX_IMAGE_BYTES) {
                throw PythonHostCapabilityResultLimitException()
            }

            val id = "image-$generation"
            val descriptor = PythonHostScreenImageDescriptor(
                id = id,
                format = format,
                width = captured.width,
                height = captured.height,
                byteLength = captured.bytes.size,
                sha256 = sha256(captured.bytes),
            )
            retained = RetainedImage(descriptor, captured.bytes)
            retainedOwnership = true
            return descriptor
        } finally {
            if (!retainedOwnership) captured.bytes.fill(0)
        }
    }

    @Synchronized
    fun readChunk(imageId: String, offset: Int): PythonHostScreenImageChunk {
        check(!closed) { "Python Host screen image scope is closed" }
        val image = retained
            ?.takeIf { it.descriptor.id == imageId }
            ?: throw PythonHostCapabilityStaleImageException()
        require(offset in 0 until image.bytes.size) { "Python Host image offset is invalid" }
        val nextOffset = minOf(offset + PythonHostImagePolicy.MAX_CHUNK_BYTES, image.bytes.size)
        val bytes = image.bytes.copyOfRange(offset, nextOffset)
        try {
            return PythonHostScreenImageChunk(
                imageId = imageId,
                offset = offset,
                data = Base64.getEncoder().encodeToString(bytes),
                nextOffset = nextOffset,
                eof = nextOffset == image.bytes.size,
            )
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun release(imageId: String) {
        check(!closed) { "Python Host screen image scope is closed" }
        if (retained?.descriptor?.id != imageId) throw PythonHostCapabilityStaleImageException()
        clearRetained()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        clearRetained()
    }

    private fun clearRetained() {
        retained?.bytes?.fill(0)
        retained = null
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val encoded = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xff
            encoded[index * 2] = HEX[unsigned ushr 4]
            encoded[index * 2 + 1] = HEX[unsigned and 0x0f]
        }
        digest.fill(0)
        return String(encoded)
    }

    private data class RetainedImage(
        val descriptor: PythonHostScreenImageDescriptor,
        val bytes: ByteArray,
    )

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

/** Android 11+ accessibility screenshot capture with a fixed deadline and bounded encoded output. */
internal object PythonHostAccessibilityScreenCapture {
    private const val CAPTURE_TIMEOUT_MILLIS = 4_000L
    private const val RETRY_DELAY_MILLIS = 50L
    private const val MAX_ATTEMPTS = 3
    private const val INTERNAL_FAILURE = -1
    private const val TIMEOUT_FAILURE = -2

    fun capture(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        format: String,
        quality: Int,
    ): PythonHostCapturedScreenImage = useCapturedBitmap(service) { capturedBitmap ->
        PythonHostCapturedScreenImage(
            format = format,
            width = capturedBitmap.width,
            height = capturedBitmap.height,
            bytes = encode(capturedBitmap, format, quality),
        )
    }

    fun <T> useCapturedBitmap(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        action: (Bitmap) -> T,
    ): T {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw PythonHostCapabilityScreenCaptureUnavailableException()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw PythonHostCapabilityScreenCaptureFailedException()
        }
        return useCapturedBitmapApi30(service, action)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun <T> useCapturedBitmapApi30(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        action: (Bitmap) -> T,
    ): T {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CAPTURE_TIMEOUT_MILLIS)
        var bitmap: Bitmap? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) throw PythonHostCapabilityScreenCaptureFailedException()
            when (val result = takeScreenshot(service, remainingNanos)) {
                is ScreenshotResult.Success -> {
                    bitmap = result.bitmap
                    break
                }
                is ScreenshotResult.Failure -> {
                    val canRetry =
                        result.errorCode == AndroidAccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT &&
                            attempt < MAX_ATTEMPTS &&
                            deadlineNanos - System.nanoTime() >
                            TimeUnit.MILLISECONDS.toNanos(RETRY_DELAY_MILLIS)
                    if (!canRetry) throw PythonHostCapabilityScreenCaptureFailedException()
                    try {
                        Thread.sleep(RETRY_DELAY_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw PythonHostCapabilityScreenCaptureFailedException()
                    }
                }
            }
        }
        val capturedBitmap = bitmap ?: throw PythonHostCapabilityScreenCaptureFailedException()
        try {
            validateBitmap(capturedBitmap)
            return action(capturedBitmap)
        } finally {
            capturedBitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshot(
        service: org.autojs.autojs.core.accessibility.AccessibilityService,
        timeoutNanos: Long,
    ): ScreenshotResult {
        val result = CompletableFuture<ScreenshotResult>()
        val callback = object : AndroidAccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AndroidAccessibilityService.ScreenshotResult) {
                val decoded = runCatching { copySoftwareBitmap(screenshot) }
                    .fold(
                        onSuccess = ScreenshotResult::Success,
                        onFailure = { ScreenshotResult.Failure(INTERNAL_FAILURE) },
                    )
                if (!result.complete(decoded) && decoded is ScreenshotResult.Success) {
                    decoded.bitmap.recycle()
                }
            }

            override fun onFailure(errorCode: Int) {
                result.complete(ScreenshotResult.Failure(errorCode))
            }
        }
        runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        }.onFailure {
            result.complete(ScreenshotResult.Failure(INTERNAL_FAILURE))
        }
        return try {
            result.get(timeoutNanos, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            result.complete(ScreenshotResult.Failure(TIMEOUT_FAILURE))
            result.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            result.complete(ScreenshotResult.Failure(TIMEOUT_FAILURE))
            result.getNow(ScreenshotResult.Failure(TIMEOUT_FAILURE))
        } catch (_: ExecutionException) {
            ScreenshotResult.Failure(INTERNAL_FAILURE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun copySoftwareBitmap(screenshot: AndroidAccessibilityService.ScreenshotResult): Bitmap {
        val hardwareBuffer = screenshot.hardwareBuffer
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                ?: throw IllegalStateException("Accessibility screenshot could not be decoded")
            try {
                return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IllegalStateException("Accessibility screenshot could not be copied")
            } finally {
                hardwareBitmap.recycle()
            }
        } finally {
            hardwareBuffer.close()
        }
    }

    private fun validateBitmap(bitmap: Bitmap) {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw PythonHostCapabilityScreenCaptureFailedException()
        }
        if (
            bitmap.width > PythonHostImagePolicy.MAX_DIMENSION ||
            bitmap.height > PythonHostImagePolicy.MAX_DIMENSION ||
            bitmap.width.toLong() * bitmap.height > PythonHostImagePolicy.MAX_PIXELS
        ) {
            throw PythonHostCapabilityResultLimitException()
        }
    }

    private fun encode(bitmap: Bitmap, format: String, quality: Int): ByteArray {
        val output = BoundedByteArrayOutputStream(PythonHostImagePolicy.MAX_IMAGE_BYTES)
        try {
            val compression = when (format) {
                "png" -> Bitmap.CompressFormat.PNG
                "jpeg" -> Bitmap.CompressFormat.JPEG
                else -> throw IllegalArgumentException("Python Host image format is invalid")
            }
            val compressed = runCatching { bitmap.compress(compression, quality, output) }
                .getOrElse { error ->
                    if (output.exceeded) throw PythonHostCapabilityResultLimitException()
                    throw PythonHostCapabilityScreenCaptureFailedException(error)
                }
            if (output.exceeded) throw PythonHostCapabilityResultLimitException()
            if (!compressed || output.size() == 0) {
                throw PythonHostCapabilityScreenCaptureFailedException()
            }
            return output.copyBytes()
        } finally {
            output.clearBytes()
        }
    }

    private sealed interface ScreenshotResult {
        data class Success(val bitmap: Bitmap) : ScreenshotResult
        data class Failure(val errorCode: Int) : ScreenshotResult
    }

    private class BoundedByteArrayOutputStream(
        private val maximumBytes: Int,
    ) : ByteArrayOutputStream() {
        var exceeded = false
            private set

        @Synchronized
        override fun write(value: Int) {
            ensureCapacityFor(1)
            super.write(value)
        }

        @Synchronized
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
                "Python Host image encoder produced an invalid write"
            }
            ensureCapacityFor(length)
            super.write(bytes, offset, length)
        }

        @Synchronized
        fun copyBytes(): ByteArray = toByteArray()

        @Synchronized
        fun clearBytes() {
            buf.fill(0, 0, count)
            reset()
        }

        private fun ensureCapacityFor(additionalBytes: Int) {
            if (additionalBytes > maximumBytes - count) {
                exceeded = true
                throw ImageOutputLimitException()
            }
        }
    }

    private class ImageOutputLimitException : RuntimeException()
}

internal class PythonHostCapabilityScreenCaptureUnavailableException : RuntimeException()

internal class PythonHostCapabilityScreenCaptureFailedException(
    cause: Throwable? = null,
) : RuntimeException(cause)

internal class PythonHostCapabilityStaleImageException : RuntimeException()

internal class PythonHostCapabilityInvalidImageTemplateException(
    cause: Throwable? = null,
) : RuntimeException(cause)

internal class PythonHostCapabilityStaleTemplateException : RuntimeException()
