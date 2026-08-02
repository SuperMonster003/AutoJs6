package org.autojs.autojs.core.plugin.explorer.output

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32

internal enum class ExplorerActionImageOutputValidationFailure {
    NOT_A_REGULAR_FILE,
    EMPTY,
    FILE_TOO_LARGE,
    UNSUPPORTED_FORMAT,
    MIME_TYPE_NOT_ALLOWED,
    MALFORMED_IMAGE,
    INVALID_DIMENSIONS,
    DIMENSION_TOO_LARGE,
    PIXEL_COUNT_TOO_LARGE,
    IO_ERROR,
}

internal data class ExplorerActionImageOutputValidationResult(
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long = 0L,
    val sha256: String? = null,
    val failure: ExplorerActionImageOutputValidationFailure? = null,
) {
    val isValid: Boolean
        get() = failure == null && mimeType != null && width != null && height != null && sha256 != null
}

internal object ExplorerActionImageOutputValidator {

    fun validate(
        file: File,
        allowedMimeTypes: Set<String>,
        maxOutputBytes: Long,
        maxDimension: Int = MAX_DIMENSION,
        maxPixelCount: Long = MAX_PIXEL_COUNT,
    ): ExplorerActionImageOutputValidationResult {
        if (!file.isFile) return failure(ExplorerActionImageOutputValidationFailure.NOT_A_REGULAR_FILE)
        val size = file.length()
        if (size <= 0L) return failure(ExplorerActionImageOutputValidationFailure.EMPTY, size)
        if (maxOutputBytes <= 0L || size > maxOutputBytes) {
            return failure(ExplorerActionImageOutputValidationFailure.FILE_TOO_LARGE, size)
        }
        return try {
            val image = detect(file)
                ?: return failure(ExplorerActionImageOutputValidationFailure.UNSUPPORTED_FORMAT, size)
            if (image.mimeType !in allowedMimeTypes) {
                return failure(ExplorerActionImageOutputValidationFailure.MIME_TYPE_NOT_ALLOWED, size)
            }
            if (image.width <= 0 || image.height <= 0) {
                return failure(ExplorerActionImageOutputValidationFailure.INVALID_DIMENSIONS, size)
            }
            if (image.width > maxDimension || image.height > maxDimension) {
                return failure(ExplorerActionImageOutputValidationFailure.DIMENSION_TOO_LARGE, size)
            }
            val pixelCount = image.width.toLong() * image.height.toLong()
            if (pixelCount > maxPixelCount) {
                return failure(ExplorerActionImageOutputValidationFailure.PIXEL_COUNT_TOO_LARGE, size)
            }
            ExplorerActionImageOutputValidationResult(
                mimeType = image.mimeType,
                width = image.width,
                height = image.height,
                size = size,
                sha256 = sha256(file),
            )
        } catch (_: IOException) {
            failure(ExplorerActionImageOutputValidationFailure.IO_ERROR, size)
        } catch (_: RuntimeException) {
            failure(ExplorerActionImageOutputValidationFailure.MALFORMED_IMAGE, size)
        }
    }

    fun extensionForMimeType(mimeType: String): String? = when (mimeType) {
        MIME_JPEG -> "jpg"
        MIME_PNG -> "png"
        MIME_WEBP -> "webp"
        else -> null
    }

    private fun detect(file: File): DetectedImage? {
        val prefix = readPrefix(file, 12)
        return when {
            prefix.hasBytes(PNG_SIGNATURE, 0) -> detectPng(file)
            prefix.size >= 2 && prefix.u8(0) == 0xFF && prefix.u8(1) == 0xD8 -> detectJpeg(file)
            prefix.hasAscii("RIFF", 0) && prefix.hasAscii("WEBP", 8) -> detectWebp(file)
            else -> null
        }
    }

    private fun detectPng(file: File): DetectedImage? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < PNG_SIGNATURE.size + PNG_CHUNK_OVERHEAD) return null
        val signature = ByteArray(PNG_SIGNATURE.size)
        input.readFully(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) return null
        var width: Int? = null
        var height: Int? = null
        var chunkIndex = 0
        var sawImageData = false
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.filePointer < input.length()) {
            if (input.length() - input.filePointer < PNG_CHUNK_OVERHEAD) return null
            val chunkLength = input.readInt().toLong() and UINT32_MASK
            if (chunkLength > input.length() - input.filePointer - PNG_TYPE_AND_CRC_BYTES) return null
            val typeBytes = ByteArray(4)
            input.readFully(typeBytes)
            val type = typeBytes.toString(Charsets.US_ASCII)
            val crc = CRC32().apply { update(typeBytes) }
            val ihdr = if (type == "IHDR" && chunkLength == PNG_IHDR_LENGTH) {
                ByteArray(PNG_IHDR_LENGTH.toInt())
            } else {
                null
            }
            var copied = 0
            var remaining = chunkLength
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                input.readFully(buffer, 0, count)
                crc.update(buffer, 0, count)
                ihdr?.let { bytes ->
                    System.arraycopy(buffer, 0, bytes, copied, count)
                    copied += count
                }
                remaining -= count
            }
            val expectedCrc = input.readInt().toLong() and UINT32_MASK
            if (crc.value != expectedCrc) return null
            when (type) {
                "IHDR" -> {
                    if (chunkIndex != 0 || chunkLength != PNG_IHDR_LENGTH || width != null) return null
                    val bytes = requireNotNull(ihdr)
                    width = bytes.bigEndianInt(0)
                    height = bytes.bigEndianInt(4)
                }
                "IDAT" -> {
                    if (width == null) return null
                    sawImageData = true
                }
                "IEND" -> {
                    if (chunkLength != 0L || width == null || !sawImageData) return null
                    if (input.filePointer != input.length()) return null
                    return DetectedImage(MIME_PNG, width, requireNotNull(height))
                }
            }
            chunkIndex++
        }
        null
    }

    private fun detectJpeg(file: File): DetectedImage? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < 4L) return null
        if (input.readUnsignedByte() != 0xFF || input.readUnsignedByte() != JPEG_SOI) return null
        var width: Int? = null
        var height: Int? = null
        var sawScan = false
        var marker = readJpegMarker(input, inScan = false) ?: return null
        while (true) {
            when {
                marker == JPEG_EOI -> {
                    if (!sawScan || input.filePointer != input.length()) return null
                    return DetectedImage(MIME_JPEG, width ?: return null, height ?: return null)
                }
                marker == JPEG_SOS -> {
                    if (!skipJpegSegment(input)) return null
                    sawScan = true
                    marker = readJpegMarker(input, inScan = true) ?: return null
                }
                marker in JPEG_START_OF_FRAME_MARKERS -> {
                    if (input.filePointer + 2L > input.length()) return null
                    val segmentLength = input.readUnsignedShort()
                    if (segmentLength < 7 || input.filePointer + segmentLength - 2L > input.length()) return null
                    input.readUnsignedByte()
                    val parsedHeight = input.readUnsignedShort()
                    val parsedWidth = input.readUnsignedShort()
                    if (width != null && (width != parsedWidth || height != parsedHeight)) return null
                    width = parsedWidth
                    height = parsedHeight
                    input.seek(input.filePointer + segmentLength - 7L)
                    marker = readJpegMarker(input, inScan = false) ?: return null
                }
                marker == JPEG_TEM || marker in JPEG_RESTART_MARKERS -> {
                    marker = readJpegMarker(input, inScan = false) ?: return null
                }
                marker == JPEG_SOI || marker == 0x00 || marker == 0xFF -> return null
                else -> {
                    if (!skipJpegSegment(input)) return null
                    marker = readJpegMarker(input, inScan = false) ?: return null
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    private fun readJpegMarker(input: RandomAccessFile, inScan: Boolean): Int? {
        if (inScan) {
            while (input.filePointer < input.length()) {
                if (input.readUnsignedByte() != 0xFF) continue
                var marker: Int
                do {
                    if (input.filePointer >= input.length()) return null
                    marker = input.readUnsignedByte()
                } while (marker == 0xFF)
                if (marker == 0x00 || marker in JPEG_RESTART_MARKERS) continue
                return marker
            }
            return null
        }
        if (input.filePointer >= input.length() || input.readUnsignedByte() != 0xFF) return null
        var marker: Int
        do {
            if (input.filePointer >= input.length()) return null
            marker = input.readUnsignedByte()
        } while (marker == 0xFF)
        return marker
    }

    private fun skipJpegSegment(input: RandomAccessFile): Boolean {
        if (input.filePointer + 2L > input.length()) return false
        val segmentLength = input.readUnsignedShort()
        if (segmentLength < 2 || input.filePointer + segmentLength - 2L > input.length()) return false
        input.seek(input.filePointer + segmentLength - 2L)
        return true
    }

    private fun detectWebp(file: File): DetectedImage? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < WEBP_HEADER_LENGTH) return null
        val header = ByteArray(WEBP_HEADER_LENGTH)
        input.readFully(header)
        if (!header.hasAscii("RIFF", 0) || !header.hasAscii("WEBP", 8)) return null
        val declaredSize = header.littleEndianUInt(4)
        if (declaredSize + RIFF_SIZE_FIELD_OFFSET != input.length()) return null
        var detected: DetectedImage? = null
        while (input.filePointer < input.length()) {
            if (input.length() - input.filePointer < WEBP_CHUNK_HEADER_LENGTH) return null
            val chunkHeader = ByteArray(WEBP_CHUNK_HEADER_LENGTH)
            input.readFully(chunkHeader)
            val chunkType = chunkHeader.ascii(0, 4)
            val chunkSize = chunkHeader.littleEndianUInt(4)
            val payloadStart = input.filePointer
            val payloadEnd = payloadStart + chunkSize
            if (payloadEnd < payloadStart || payloadEnd > input.length()) return null
            val requiredPrefix = when (chunkType) {
                "VP8X", "VP8 " -> 10
                "VP8L" -> 5
                else -> 0
            }
            if (requiredPrefix > 0) {
                if (chunkSize < requiredPrefix) return null
                val bytes = ByteArray(requiredPrefix)
                input.readFully(bytes)
                val dimensions = when (chunkType) {
                    "VP8X" -> DetectedImage(
                        MIME_WEBP,
                        1 + bytes.littleEndian24(4),
                        1 + bytes.littleEndian24(7),
                    )
                    "VP8L" -> {
                        if (bytes.u8(0) != 0x2F) return null
                        val b1 = bytes.u8(1)
                        val b2 = bytes.u8(2)
                        val b3 = bytes.u8(3)
                        val b4 = bytes.u8(4)
                        DetectedImage(
                            MIME_WEBP,
                            1 + (b1 or ((b2 and 0x3F) shl 8)),
                            1 + ((b2 shr 6) or (b3 shl 2) or ((b4 and 0x0F) shl 10)),
                        )
                    }
                    else -> {
                        if (bytes.u8(3) != 0x9D || bytes.u8(4) != 0x01 || bytes.u8(5) != 0x2A) {
                            return null
                        }
                        DetectedImage(
                            MIME_WEBP,
                            bytes.littleEndianUnsignedShort(6) and 0x3FFF,
                            bytes.littleEndianUnsignedShort(8) and 0x3FFF,
                        )
                    }
                }
                if (detected != null && detected != dimensions) return null
                detected = dimensions
            }
            input.seek(payloadEnd)
            if ((chunkSize and 1L) != 0L) {
                if (input.filePointer >= input.length()) return null
                input.readUnsignedByte()
            }
        }
        if (input.filePointer != input.length()) return null
        detected
    }

    private fun readPrefix(file: File, maxBytes: Int): ByteArray {
        val targetSize = minOf(file.length(), maxBytes.toLong()).toInt()
        val result = ByteArray(targetSize)
        var offset = 0
        FileInputStream(file).use { input ->
            while (offset < result.size) {
                val count = input.read(result, offset, result.size - offset)
                if (count < 0) break
                offset += count
            }
        }
        return if (offset == result.size) result else result.copyOf(offset)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun failure(
        failure: ExplorerActionImageOutputValidationFailure,
        size: Long = 0L,
    ) = ExplorerActionImageOutputValidationResult(size = size, failure = failure)

    private fun ByteArray.hasBytes(expected: ByteArray, offset: Int): Boolean =
        size >= offset + expected.size && expected.indices.all { index -> this[offset + index] == expected[index] }

    private fun ByteArray.hasAscii(expected: String, offset: Int): Boolean =
        size >= offset + expected.length && expected.indices.all { index -> u8(offset + index) == expected[index].code }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        buildString(length) { repeat(length) { index -> append(u8(offset + index).toChar()) } }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

    private fun ByteArray.bigEndianInt(offset: Int): Int =
        (u8(offset) shl 24) or (u8(offset + 1) shl 16) or (u8(offset + 2) shl 8) or u8(offset + 3)

    private fun ByteArray.littleEndianUnsignedShort(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

    private fun ByteArray.littleEndian24(offset: Int): Int =
        u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16)

    private fun ByteArray.littleEndianUInt(offset: Int): Long =
        u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)

    private data class DetectedImage(
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    const val MIME_JPEG = "image/jpeg"
    const val MIME_PNG = "image/png"
    const val MIME_WEBP = "image/webp"

    const val MAX_DIMENSION = 16_384
    const val MAX_PIXEL_COUNT = 40_000_000L

    private const val UINT32_MASK = 0xFFFF_FFFFL
    private const val PNG_CHUNK_OVERHEAD = 12L
    private const val PNG_TYPE_AND_CRC_BYTES = 8L
    private const val PNG_IHDR_LENGTH = 13L
    private const val JPEG_SOI = 0xD8
    private const val JPEG_EOI = 0xD9
    private const val JPEG_SOS = 0xDA
    private const val JPEG_TEM = 0x01
    private const val WEBP_HEADER_LENGTH = 12
    private const val WEBP_CHUNK_HEADER_LENGTH = 8
    private const val RIFF_SIZE_FIELD_OFFSET = 8L
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val JPEG_START_OF_FRAME_MARKERS = setOf(
        0xC0, 0xC1, 0xC2, 0xC3,
        0xC5, 0xC6, 0xC7,
        0xC9, 0xCA, 0xCB,
        0xCD, 0xCE, 0xCF,
    )
    private val JPEG_RESTART_MARKERS = 0xD0..0xD7
}
