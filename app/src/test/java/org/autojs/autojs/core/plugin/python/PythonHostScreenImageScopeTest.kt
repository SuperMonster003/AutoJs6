package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PythonHostScreenImageScopeTest {
    @Test
    fun capturesStrictMetadataAndReadsBoundedOrderedChunks() {
        val encoded = ByteArray(PythonHostImagePolicy.MAX_CHUNK_BYTES + 3) { index ->
            (index % 251).toByte()
        }
        val expected = encoded.copyOf()
        val scope = PythonHostScreenImageScope { format, quality ->
            assertEquals("png", format)
            assertEquals(100, quality)
            PythonHostCapturedScreenImage(format, 300, 400, encoded)
        }

        val descriptor = scope.capture("png", 100)
        assertEquals("image-1", descriptor.id)
        assertEquals("png", descriptor.format)
        assertEquals(300, descriptor.width)
        assertEquals(400, descriptor.height)
        assertEquals(expected.size, descriptor.byteLength)
        assertEquals(sha256(expected), descriptor.sha256)
        assertEquals("autojs6-python-screen-image-v1", descriptor.toJson().get("schema").asString)

        val first = scope.readChunk(descriptor.id, 0)
        assertEquals("autojs6-python-screen-image-chunk-v1", first.toJson().get("schema").asString)
        assertEquals(0, first.offset)
        assertEquals(PythonHostImagePolicy.MAX_CHUNK_BYTES, first.nextOffset)
        assertFalse(first.eof)
        assertArrayEquals(
            expected.copyOfRange(0, PythonHostImagePolicy.MAX_CHUNK_BYTES),
            Base64.getDecoder().decode(first.data),
        )

        val second = scope.readChunk(descriptor.id, first.nextOffset)
        assertEquals(expected.size, second.nextOffset)
        assertTrue(second.eof)
        assertArrayEquals(
            expected.copyOfRange(PythonHostImagePolicy.MAX_CHUNK_BYTES, expected.size),
            Base64.getDecoder().decode(second.data),
        )

        scope.release(descriptor.id)
        assertArrayEquals(ByteArray(encoded.size), encoded)
        assertThrows(PythonHostCapabilityStaleImageException::class.java) {
            scope.readChunk(descriptor.id, 0)
        }
    }

    @Test
    fun replacementInvalidatesAndClearsThePreviousImageAndCloseClearsTheLast() {
        val firstBytes = byteArrayOf(1, 2, 3)
        val secondBytes = byteArrayOf(4, 5, 6)
        var captureCount = 0
        val scope = PythonHostScreenImageScope { format, _ ->
            captureCount += 1
            PythonHostCapturedScreenImage(
                format = format,
                width = 10,
                height = 10,
                bytes = if (captureCount == 1) firstBytes else secondBytes,
            )
        }

        val first = scope.capture("jpeg", 80)
        val second = scope.capture("jpeg", 70)
        assertEquals("image-1", first.id)
        assertEquals("image-2", second.id)
        assertArrayEquals(byteArrayOf(0, 0, 0), firstBytes)
        assertThrows(PythonHostCapabilityStaleImageException::class.java) {
            scope.readChunk(first.id, 0)
        }

        scope.close()
        scope.close()
        assertArrayEquals(byteArrayOf(0, 0, 0), secondBytes)
        assertThrows(IllegalStateException::class.java) {
            scope.capture("jpeg", 70)
        }
    }

    @Test
    fun rejectsInvalidBoundsAndClearsRejectedCaptureBytes() {
        val oversized = ByteArray(PythonHostImagePolicy.MAX_IMAGE_BYTES + 1) { 1 }
        val oversizedScope = PythonHostScreenImageScope { format, _ ->
            PythonHostCapturedScreenImage(format, 100, 100, oversized)
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            oversizedScope.capture("png", 100)
        }
        assertArrayEquals(ByteArray(oversized.size), oversized)

        val invalidDimensions = byteArrayOf(1)
        val dimensionsScope = PythonHostScreenImageScope { format, _ ->
            PythonHostCapturedScreenImage(
                format,
                PythonHostImagePolicy.MAX_DIMENSION + 1,
                1,
                invalidDimensions,
            )
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            dimensionsScope.capture("png", 100)
        }
        assertArrayEquals(byteArrayOf(0), invalidDimensions)
    }

    @Test
    fun rejectsInvalidOptionsAndOffsetsBeforeReturningBytes() {
        var captures = 0
        val scope = PythonHostScreenImageScope { format, _ ->
            captures += 1
            PythonHostCapturedScreenImage(format, 1, 1, byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) { scope.capture("webp", 100) }
        assertThrows(IllegalArgumentException::class.java) { scope.capture("png", 0) }
        assertEquals(0, captures)

        val descriptor = scope.capture("png", 100)
        assertThrows(IllegalArgumentException::class.java) {
            scope.readChunk(descriptor.id, 1)
        }
        scope.close()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { value -> "%02x".format(value.toInt() and 0xff) }
}
