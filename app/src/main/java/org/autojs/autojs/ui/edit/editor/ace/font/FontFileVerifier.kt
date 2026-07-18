package org.autojs.autojs.ui.edit.editor.ace.font

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object FontFileVerifier {
    const val FORMAT_WOFF2 = "woff2"
    const val MIME_WOFF2 = "font/woff2"
    const val WOFF2_HEADER_SIZE = 48L
    const val MAX_FONT_BYTES = 64L * 1024L * 1024L

    data class Result(val sizeBytes: Long, val sha256: String)

    @JvmStatic
    @Throws(FontFileValidationException::class)
    fun verify(file: File, expectedSize: Long, expectedSha256: String): Result {
        try {
            FileInputStream(file).use { input ->
                return verify(input, expectedSize, expectedSha256)
            }
        } catch (e: FontFileValidationException) {
            throw e
        } catch (e: Exception) {
            throw FontFileValidationException("Unable to read downloaded font", e)
        }
    }

    @JvmStatic
    @Throws(FontFileValidationException::class)
    fun verify(input: InputStream, expectedSize: Long, expectedSha256: String): Result {
        validateExpectation(expectedSize, expectedSha256)
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(WOFF2_HEADER_SIZE.toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var headerBytes = 0
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > expectedSize || total > MAX_FONT_BYTES) {
                throw FontFileValidationException(
                    "Font size exceeds expected size ($expectedSize bytes)",
                )
            }
            if (headerBytes < header.size) {
                val copy = minOf(read, header.size - headerBytes)
                buffer.copyInto(header, headerBytes, 0, copy)
                headerBytes += copy
            }
            digest.update(buffer, 0, read)
        }
        return validateResult(header, headerBytes, total, digest.digest(), expectedSize, expectedSha256)
    }

    internal fun validateExpectation(expectedSize: Long, expectedSha256: String) {
        if (expectedSize !in WOFF2_HEADER_SIZE..MAX_FONT_BYTES) {
            throw FontFileValidationException("Invalid expected font size: $expectedSize")
        }
        if (!AceFontIds.validSha256.matches(expectedSha256.lowercase())) {
            throw FontFileValidationException("Invalid expected SHA-256 digest")
        }
    }

    internal fun validateResult(
        header: ByteArray,
        headerBytes: Int,
        total: Long,
        digest: ByteArray,
        expectedSize: Long,
        expectedSha256: String,
    ): Result {
        if (total != expectedSize) {
            throw FontFileValidationException("Font size mismatch: expected $expectedSize, received $total")
        }
        validateWoff2Header(header, headerBytes, total)
        val actualSha256 = digest.toHex()
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            throw FontFileValidationException(
                "Font SHA-256 mismatch: expected ${expectedSha256.lowercase()}, received $actualSha256",
            )
        }
        return Result(total, actualSha256)
    }

    internal fun validateWoff2Header(header: ByteArray, headerBytes: Int, actualSize: Long) {
        if (headerBytes < WOFF2_HEADER_SIZE) {
            throw FontFileValidationException("WOFF2 file is shorter than its 48-byte header")
        }
        if (header[0] != 'w'.code.toByte() || header[1] != 'O'.code.toByte() ||
            header[2] != 'F'.code.toByte() || header[3] != '2'.code.toByte()
        ) {
            throw FontFileValidationException("Downloaded file does not have the WOFF2 signature")
        }
        val declaredLength = readUInt32BigEndian(header, 8)
        if (declaredLength != actualSize) {
            throw FontFileValidationException(
                "WOFF2 header length mismatch: header declares $declaredLength, received $actualSize",
            )
        }
        val tableCount = readUInt16BigEndian(header, 12)
        if (tableCount == 0) throw FontFileValidationException("WOFF2 header contains no font tables")
        if (readUInt16BigEndian(header, 14) != 0) {
            throw FontFileValidationException("WOFF2 header reserved field must be zero")
        }
    }

    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun readUInt16BigEndian(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

class FontFileValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
