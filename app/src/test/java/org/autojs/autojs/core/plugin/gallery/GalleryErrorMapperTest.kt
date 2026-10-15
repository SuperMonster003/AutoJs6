package org.autojs.autojs.core.plugin.gallery

import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

class GalleryErrorMapperTest {

    @Test
    fun unknownCodesCollapseToInternalAndKeepTheirName() {
        val error = GalleryErrorMapper.normalize("NOPE", "detail")
        assertEquals(GalleryErrorCodes.INTERNAL, error.code)
        assertEquals("unknown error code NOPE: detail", error.message)
        assertEquals("the gallery plugin has no photo access", GalleryErrorMapper.normalize(GalleryErrorCodes.PERMISSION_REQUIRED, " ").message)
    }

    @Test
    fun encodedPluginExceptionsKeepTheirCode() {
        val error = GalleryErrorMapper.fromThrowable(IllegalArgumentException("DECODE_FAILED: truncated JPEG"), GalleryErrorMapper.Phase.CALL)
        assertEquals(GalleryErrorCodes.DECODE_FAILED, error.code)
        assertEquals("truncated JPEG", error.message)
        assertNull(GalleryErrorMapper.encoded(IllegalStateException("plain message")))
    }

    @Test
    fun hostFailuresMapByPhase() {
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, GalleryErrorMapper.fromThrowable(IllegalArgumentException("bad"), GalleryErrorMapper.Phase.CALL).code)
        assertEquals(GalleryErrorCodes.PLUGIN_UNAVAILABLE, GalleryErrorMapper.fromThrowable(IllegalStateException("none"), GalleryErrorMapper.Phase.DISCOVERY).code)
        assertEquals(GalleryErrorCodes.INTERNAL, GalleryErrorMapper.fromThrowable(IllegalStateException("odd"), GalleryErrorMapper.Phase.CALL).code)
        assertEquals(GalleryErrorCodes.CANCELLED, GalleryErrorMapper.fromThrowable(CancellationException("stop"), GalleryErrorMapper.Phase.CALL).code)
        assertEquals(GalleryErrorCodes.FILE_NOT_FOUND, GalleryErrorMapper.fromThrowable(FileNotFoundException("x"), GalleryErrorMapper.Phase.OPEN).code)
        assertEquals(GalleryErrorCodes.IO, GalleryErrorMapper.fromThrowable(IOException("disk"), GalleryErrorMapper.Phase.OUTPUT).code)
        val thrown = GalleryErrorMapper.failure(GalleryErrorCodes.OUTPUT_EXISTS, "a.png")
        assertEquals(thrown.error, GalleryErrorMapper.fromThrowable(thrown, GalleryErrorMapper.Phase.CALL))
    }

    @Test
    fun messagesAreTruncatedToTheContractCeiling() {
        val long = "x".repeat(GalleryContract.MAX_ERROR_MESSAGE_BYTES * 2)
        val error = GalleryErrorMapper.normalize(GalleryErrorCodes.IO, long)
        assertTrue(error.message.toByteArray(StandardCharsets.UTF_8).size <= GalleryContract.MAX_ERROR_MESSAGE_BYTES)
        assertEquals("short", GalleryErrorMapper.truncate("short", 16))
    }

    @Test
    fun peersNegotiateWithinTheHostRange() {
        assertEquals(1, GalleryPeer.negotiate(1, null))
        assertEquals(1, GalleryPeer.negotiate(1, 5))
        assertEquals(1, GalleryPeer.negotiate(1, 0))
        assertTrue(GalleryPeer(1, setOf(GalleryContract.FEATURE_PICKER)).supports(GalleryContract.FEATURE_PICKER))
        assertTrue(!GalleryPeer.LEGACY.supports(GalleryContract.FEATURE_CONVERT))
    }
}
