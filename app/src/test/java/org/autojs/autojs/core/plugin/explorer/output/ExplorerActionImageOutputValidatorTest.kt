package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

class ExplorerActionImageOutputValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsPngJpegAndWebpByContentRatherThanExtension() {
        val cases = listOf(
            Triple("misleading.bin", png(320, 240), ExplorerActionImageOutputValidator.MIME_PNG),
            Triple("photo.dat", jpeg(640, 480), ExplorerActionImageOutputValidator.MIME_JPEG),
            Triple("image.raw", webpVp8x(800, 600), ExplorerActionImageOutputValidator.MIME_WEBP),
        )
        cases.forEach { (name, bytes, mimeType) ->
            val file = temporaryFolder.newFile(name).apply { writeBytes(bytes) }
            val result = ExplorerActionImageOutputValidator.validate(file, setOf(mimeType), 1_000_000L)

            assertTrue("$name: $result", result.isValid)
            assertEquals(mimeType, result.mimeType)
            assertEquals(64, result.sha256?.length)
        }
    }

    @Test
    fun rejectsMismatchedMimeAndOversizedDimensions() {
        val file = temporaryFolder.newFile("photo.png").apply { writeBytes(png(20_000, 100)) }
        val mismatch = ExplorerActionImageOutputValidator.validate(
            file,
            setOf(ExplorerActionImageOutputValidator.MIME_JPEG),
            1_000_000L,
        )
        assertFalse(mismatch.isValid)
        assertEquals(ExplorerActionImageOutputValidationFailure.MIME_TYPE_NOT_ALLOWED, mismatch.failure)

        val oversized = ExplorerActionImageOutputValidator.validate(
            file,
            setOf(ExplorerActionImageOutputValidator.MIME_PNG),
            1_000_000L,
        )
        assertEquals(ExplorerActionImageOutputValidationFailure.DIMENSION_TOO_LARGE, oversized.failure)
    }

    @Test
    fun rejectsEmptyMalformedAndByteOversizedOutputs() {
        val empty = temporaryFolder.newFile("empty.png")
        assertEquals(
            ExplorerActionImageOutputValidationFailure.EMPTY,
            ExplorerActionImageOutputValidator.validate(empty, setOf("image/png"), 100L).failure,
        )

        val malformed = temporaryFolder.newFile("bad.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(
            ExplorerActionImageOutputValidationFailure.UNSUPPORTED_FORMAT,
            ExplorerActionImageOutputValidator.validate(malformed, setOf("image/png"), 100L).failure,
        )

        val tooLarge = temporaryFolder.newFile("large.png").apply { writeBytes(png(10, 10)) }
        assertEquals(
            ExplorerActionImageOutputValidationFailure.FILE_TOO_LARGE,
            ExplorerActionImageOutputValidator.validate(tooLarge, setOf("image/png"), 8L).failure,
        )
    }

    @Test
    fun rejectsTruncatedCorruptOrTrailingImageContainers() {
        val validPng = png(10, 10)
        val corruptPng = validPng.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val trailingPng = validPng + byteArrayOf(0)
        val truncatedJpeg = jpeg(10, 10).copyOf(jpeg(10, 10).size - 1)
        val trailingJpeg = jpeg(10, 10) + byteArrayOf(0)
        val trailingWebp = webpVp8x(10, 10) + byteArrayOf(0)
        listOf(
            corruptPng to ExplorerActionImageOutputValidator.MIME_PNG,
            trailingPng to ExplorerActionImageOutputValidator.MIME_PNG,
            truncatedJpeg to ExplorerActionImageOutputValidator.MIME_JPEG,
            trailingJpeg to ExplorerActionImageOutputValidator.MIME_JPEG,
            trailingWebp to ExplorerActionImageOutputValidator.MIME_WEBP,
        ).forEachIndexed { index, (bytes, mimeType) ->
            val file = temporaryFolder.newFile("invalid-$index").apply { writeBytes(bytes) }
            assertFalse(
                "$mimeType container unexpectedly passed",
                ExplorerActionImageOutputValidator.validate(file, setOf(mimeType), 1_000_000L).isValid,
            )
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val ihdr = ByteArray(13).apply {
            putBigEndianInt(0, width)
            putBigEndianInt(4, height)
            this[8] = 8
            this[9] = 2
        }
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            pngChunk("IHDR", ihdr) +
            pngChunk("IDAT", byteArrayOf(1)) +
            pngChunk("IEND", byteArrayOf())
    }

    private fun pngChunk(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                val typeBytes = type.encodeToByteArray()
                output.writeInt(payload.size)
                output.write(typeBytes)
                output.write(payload)
                output.writeInt(CRC32().apply {
                    update(typeBytes)
                    update(payload)
                }.value.toInt())
            }
            bytes.toByteArray()
        }

    private fun jpeg(width: Int, height: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xC0.toByte(),
        0x00, 0x0B,
        0x08,
        (height shr 8).toByte(), height.toByte(),
        (width shr 8).toByte(), width.toByte(),
        0x01, 0x01, 0x11, 0x00,
        0xFF.toByte(), 0xDA.toByte(),
        0x00, 0x08,
        0x01, 0x01, 0x00, 0x00, 0x3F, 0x00,
        0x01, 0x02,
        0xFF.toByte(), 0xD9.toByte(),
    )

    private fun webpVp8x(width: Int, height: Int): ByteArray = ByteArray(30).apply {
        "RIFF".encodeToByteArray().copyInto(this, 0)
        putLittleEndianInt(4, 22)
        "WEBP".encodeToByteArray().copyInto(this, 8)
        "VP8X".encodeToByteArray().copyInto(this, 12)
        putLittleEndianInt(16, 10)
        putLittleEndian24(24, width - 1)
        putLittleEndian24(27, height - 1)
    }

    private fun ByteArray.putBigEndianInt(offset: Int, value: Int) {
        this[offset] = (value shr 24).toByte()
        this[offset + 1] = (value shr 16).toByte()
        this[offset + 2] = (value shr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.putLittleEndianInt(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
        this[offset + 2] = (value shr 16).toByte()
        this[offset + 3] = (value shr 24).toByte()
    }

    private fun ByteArray.putLittleEndian24(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
        this[offset + 2] = (value shr 16).toByte()
    }
}
