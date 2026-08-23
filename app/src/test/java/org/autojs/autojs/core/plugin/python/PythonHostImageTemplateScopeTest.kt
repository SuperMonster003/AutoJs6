package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PythonHostImageTemplateScopeTest {
    @Test
    fun uploadsStrictOrderedChunksAndClearsEncodedBytesAfterDecode() {
        val encoded = ByteArray(PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BYTES + 3) { index ->
            (index % 251).toByte()
        }
        val decodedPixels = intArrayOf(
            0xff102030.toInt(),
            0x00112233,
            0xff405060.toInt(),
            0xff708090.toInt(),
        )
        var decodedBytes = ByteArray(0)
        val scope = PythonHostImageTemplateScope { format, bytes ->
            assertEquals("png", format)
            assertArrayEquals(encoded, bytes)
            decodedBytes = bytes
            PythonHostDecodedImageTemplate(2, 2, decodedPixels)
        }

        val descriptor = scope.begin("png", encoded.size, sha256(encoded))
        assertEquals("template-1", descriptor.id)
        assertEquals("png", descriptor.format)
        assertEquals(encoded.size, descriptor.byteLength)
        assertEquals("autojs6-python-image-template-v1", descriptor.toJson().get("schema").asString)

        val firstBytes = encoded.copyOfRange(0, PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BYTES)
        val first = scope.writeChunk(
            descriptor.id,
            0,
            Base64.getEncoder().encodeToString(firstBytes),
        )
        assertEquals("autojs6-python-image-template-chunk-v1", first.toJson().get("schema").asString)
        assertEquals(0, first.offset)
        assertEquals(firstBytes.size, first.nextOffset)
        assertFalse(first.complete)
        assertEquals(0, first.width)
        assertEquals(0, first.height)

        val finalBytes = encoded.copyOfRange(first.nextOffset, encoded.size)
        val final = scope.writeChunk(
            descriptor.id,
            first.nextOffset,
            Base64.getEncoder().encodeToString(finalBytes),
        )
        assertEquals(first.nextOffset, final.offset)
        assertEquals(encoded.size, final.nextOffset)
        assertTrue(final.complete)
        assertEquals(2, final.width)
        assertEquals(2, final.height)
        assertArrayEquals(ByteArray(encoded.size), decodedBytes)

        scope.release(descriptor.id)
        assertArrayEquals(IntArray(decodedPixels.size), decodedPixels)
        assertThrows(PythonHostCapabilityStaleTemplateException::class.java) {
            scope.release(descriptor.id)
        }
    }

    @Test
    fun replacementInvalidatesAndClearsThePreviousTemplateAndCloseClearsTheLast() {
        val firstPixels = intArrayOf(0xff010203.toInt())
        val secondPixels = intArrayOf(0xff040506.toInt())
        var decodeCount = 0
        val scope = PythonHostImageTemplateScope { _, _ ->
            decodeCount += 1
            PythonHostDecodedImageTemplate(
                1,
                1,
                if (decodeCount == 1) firstPixels else secondPixels,
            )
        }
        val bytes = byteArrayOf(1, 2, 3)

        val first = complete(scope, "png", bytes)
        val second = complete(scope, "jpeg", bytes)
        assertEquals("template-1", first.id)
        assertEquals("template-2", second.id)
        assertArrayEquals(intArrayOf(0), firstPixels)
        assertThrows(PythonHostCapabilityStaleTemplateException::class.java) {
            scope.release(first.id)
        }

        scope.close()
        scope.close()
        assertArrayEquals(intArrayOf(0), secondPixels)
        assertThrows(IllegalStateException::class.java) {
            scope.begin("png", bytes.size, sha256(bytes))
        }
    }

    @Test
    fun rejectsBrokenHashChunksAndDecodedTemplateBoundsFailClosed() {
        var decodeCalls = 0
        val hashScope = PythonHostImageTemplateScope { _, _ ->
            decodeCalls += 1
            PythonHostDecodedImageTemplate(1, 1, intArrayOf(0xff000000.toInt()))
        }
        val bytes = byteArrayOf(1, 2, 3)
        val hashDescriptor = hashScope.begin("png", bytes.size, "0".repeat(64))
        assertThrows(PythonHostCapabilityInvalidImageTemplateException::class.java) {
            hashScope.writeChunk(
                hashDescriptor.id,
                0,
                Base64.getEncoder().encodeToString(bytes),
            )
        }
        assertEquals(0, decodeCalls)
        assertThrows(PythonHostCapabilityStaleTemplateException::class.java) {
            hashScope.release(hashDescriptor.id)
        }

        val malformedScope = PythonHostImageTemplateScope { _, _ ->
            error("Malformed upload must not decode")
        }
        val malformed = malformedScope.begin("png", 3, sha256(bytes))
        assertThrows(IllegalArgumentException::class.java) {
            malformedScope.writeChunk(malformed.id, 1, "AQI=")
        }
        assertThrows(PythonHostCapabilityStaleTemplateException::class.java) {
            malformedScope.release(malformed.id)
        }

        val shortScope = PythonHostImageTemplateScope { _, _ ->
            error("Short non-final upload must not decode")
        }
        val longBytes = ByteArray(PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BYTES + 1)
        val short = shortScope.begin("png", longBytes.size, sha256(longBytes))
        assertThrows(IllegalArgumentException::class.java) {
            shortScope.writeChunk(short.id, 0, Base64.getEncoder().encodeToString(byteArrayOf(1)))
        }

        val oversizedPixels = IntArray(PythonHostImagePolicy.MAX_TEMPLATE_PIXELS + 1) {
            0xff000000.toInt()
        }
        val dimensionsScope = PythonHostImageTemplateScope { _, _ ->
            PythonHostDecodedImageTemplate(
                PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION,
                PythonHostImagePolicy.MAX_TEMPLATE_DIMENSION,
                oversizedPixels,
            )
        }
        val dimensions = dimensionsScope.begin("png", bytes.size, sha256(bytes))
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            dimensionsScope.writeChunk(
                dimensions.id,
                0,
                Base64.getEncoder().encodeToString(bytes),
            )
        }
        assertArrayEquals(IntArray(oversizedPixels.size), oversizedPixels)

        val transparentPixels = intArrayOf(0x00112233, 0x7f445566)
        val transparentScope = PythonHostImageTemplateScope { _, _ ->
            PythonHostDecodedImageTemplate(2, 1, transparentPixels)
        }
        val transparent = transparentScope.begin("png", bytes.size, sha256(bytes))
        assertThrows(PythonHostCapabilityInvalidImageTemplateException::class.java) {
            transparentScope.writeChunk(
                transparent.id,
                0,
                Base64.getEncoder().encodeToString(bytes),
            )
        }
        assertArrayEquals(IntArray(transparentPixels.size), transparentPixels)
    }

    @Test
    fun rejectsInvalidBeginArgumentsBeforeAllocatingAnUpload() {
        val scope = PythonHostImageTemplateScope { _, _ -> error("Invalid begin must not decode") }

        assertThrows(IllegalArgumentException::class.java) {
            scope.begin("webp", 1, "0".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scope.begin("png", 0, "0".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scope.begin("png", PythonHostImagePolicy.MAX_TEMPLATE_BYTES + 1, "0".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scope.begin("png", 1, "A".repeat(64))
        }
    }

    private fun complete(
        scope: PythonHostImageTemplateScope,
        format: String,
        bytes: ByteArray,
    ): PythonHostImageTemplateDescriptor {
        val descriptor = scope.begin(format, bytes.size, sha256(bytes))
        scope.writeChunk(descriptor.id, 0, Base64.getEncoder().encodeToString(bytes))
        return descriptor
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { value -> "%02x".format(value.toInt() and 0xff) }
}
