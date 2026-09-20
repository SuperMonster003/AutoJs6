package org.autojs.autojs.core.plugin.epub

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

class EpubErrorMapperTest {

    private val call = EpubErrorMapper.Phase.CALL
    private val open = EpubErrorMapper.Phase.OPEN
    private val discovery = EpubErrorMapper.Phase.DISCOVERY

    @Test
    fun everyKnownCodeNormalizesToItselfWithADefaultMessage() {
        EpubErrorCodes.ALL.forEach { code ->
            assertEquals(EpubError(code, "m"), EpubErrorMapper.normalize(code, "m"))
            val blank = EpubErrorMapper.normalize(code, "  ")
            assertEquals(code, blank.code)
            assertTrue(code, blank.message.isNotBlank())
        }
        assertEquals(EpubErrorCodes.ALL.toSet(), EpubErrorCodes.ALL.map { EpubErrorMapper.normalize(it, null).code }.toSet())
    }

    @Test
    fun unknownCodesCollapseToInternalAndKeepTheOriginalCodeInTheMessage() {
        val error = EpubErrorMapper.normalize("TELEPORT_FAILED", "no pad")
        assertEquals(EpubErrorCodes.INTERNAL, error.code)
        assertEquals("unknown error code TELEPORT_FAILED: no pad", error.message)
        assertEquals(EpubError(EpubErrorCodes.INTERNAL, "unknown error code X"), EpubErrorMapper.normalize(" X ", null))
        assertEquals(EpubError(EpubErrorCodes.INTERNAL, EpubErrorMapper.defaultMessage(EpubErrorCodes.INTERNAL)), EpubErrorMapper.normalize(null, null))
    }

    @Test
    fun messagesAreTruncatedToTheContractCeilingOnACharacterBoundary() {
        val long = "书".repeat(EpubContract.MAX_ERROR_MESSAGE_BYTES)
        val error = EpubErrorMapper.normalize(EpubErrorCodes.IO, long)
        assertTrue(error.message.toByteArray(StandardCharsets.UTF_8).size <= EpubContract.MAX_ERROR_MESSAGE_BYTES)
        assertTrue(error.message.isNotEmpty())
        assertTrue(long.startsWith(error.message))
        assertEquals("short", EpubErrorMapper.truncate("short", 5))
    }

    @Test
    fun encodedExceptionsCarryTheirCode() {
        val invalid = IllegalArgumentException(EpubErrorCodes.encode(EpubErrorCodes.LIMIT_EXCEEDED, "options exceed 36864 bytes"))
        assertEquals(EpubError(EpubErrorCodes.LIMIT_EXCEEDED, "options exceed 36864 bytes"), EpubErrorMapper.fromThrowable(invalid, call))
        val state = IllegalStateException(EpubErrorCodes.encode(EpubErrorCodes.SESSION_CLOSED))
        assertEquals(EpubErrorCodes.SESSION_CLOSED, EpubErrorMapper.fromThrowable(state, discovery).code)
        assertEquals(EpubErrorCodes.NOT_EPUB, EpubErrorMapper.fromThrowable(IllegalStateException("NOT_EPUB: An error occurred when trying to read asset."), open).code)
        assertNull(EpubErrorMapper.encoded(IllegalStateException("plain message")))
        assertNull(EpubErrorMapper.encoded(IllegalStateException()))
        assertEquals(EpubErrorCodes.INTERNAL, EpubErrorMapper.fromThrowable(IllegalStateException("MADE_UP: detail"), call).code)
    }

    @Test
    fun plainExceptionsMapPerPhase() {
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, EpubErrorMapper.fromThrowable(IllegalArgumentException("bad"), call).code)
        assertEquals("invalid argument: bad", EpubErrorMapper.fromThrowable(IllegalArgumentException("bad"), call).message)
        assertEquals(EpubErrorCodes.PLUGIN_UNAVAILABLE, EpubErrorMapper.fromThrowable(IllegalStateException("no plugin"), discovery).code)
        assertEquals(EpubErrorCodes.PLUGIN_UNAVAILABLE, EpubErrorMapper.fromThrowable(IllegalStateException("binding died"), open).code)
        assertEquals(EpubErrorCodes.INTERNAL, EpubErrorMapper.fromThrowable(IllegalStateException("oops"), call).code)
        assertEquals(EpubErrorCodes.PLUGIN_UNAVAILABLE, EpubErrorMapper.fromThrowable(SecurityException("uid"), call).code)
        assertEquals(EpubErrorCodes.FILE_NOT_FOUND, EpubErrorMapper.fromThrowable(FileNotFoundException("x"), open).code)
        assertEquals(EpubErrorCodes.IO, EpubErrorMapper.fromThrowable(IOException("pipe"), EpubErrorMapper.Phase.TRANSFER).code)
        assertEquals(EpubErrorCodes.CANCELLED, EpubErrorMapper.fromThrowable(CancellationException("c"), call).code)
        assertEquals(EpubErrorCodes.CANCELLED, EpubErrorMapper.fromThrowable(InterruptedException(), call).code)
        assertEquals(EpubErrorCodes.CANCELLED, EpubErrorMapper.fromThrowable(InterruptedIOException(), call).code)
        assertEquals(EpubErrorCodes.INTERNAL, EpubErrorMapper.fromThrowable(RuntimeException("boom"), call).code)
        val timeout = runCatching { runBlocking { withTimeout(1) { delay(1000) } } }.exceptionOrNull()
        assertTrue(timeout is TimeoutCancellationException)
        assertEquals(EpubErrorCodes.TIMEOUT, EpubErrorMapper.fromThrowable(timeout!!, call).code)
    }

    @Test
    fun pluginExceptionsPassThroughUnchanged() {
        val error = EpubError(EpubErrorCodes.ENCRYPTED, "Readium LCP DRM scheme is not supported.")
        val exception = EpubPluginException(error)
        assertSame(error, EpubErrorMapper.fromThrowable(exception, call))
        assertEquals("ENCRYPTED: Readium LCP DRM scheme is not supported.", exception.message)
        assertEquals(EpubErrorCodes.ENCRYPTED, exception.code)
    }
}
