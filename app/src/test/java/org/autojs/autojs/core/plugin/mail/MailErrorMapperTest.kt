package org.autojs.autojs.core.plugin.mail

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

class MailErrorMapperTest {

    @Test
    fun everyKnownCodeNormalizesToItselfWithTheDefaultRetryHint() {
        MailErrorCodes.ALL.forEach { code ->
            val error = MailErrorMapper.normalize(code, "m", retryable = null)
            assertEquals(code, error.code)
            assertEquals("m", error.message)
            assertEquals(code, MailErrorCodes.isRetryableByDefault(code), error.retryable)
            assertNull(error.details)
        }
        assertEquals(MailErrorCodes.ALL.toSet(), MailErrorCodes.ALL.map { MailErrorMapper.normalize(it, null, null).code }.toSet())
    }

    @Test
    fun unknownOrMissingCodesBecomeInternal() {
        val unknown = MailErrorMapper.normalize("WEIRD", "boom", retryable = true)
        assertEquals(MailErrorCodes.INTERNAL, unknown.code)
        assertEquals("unknown error code WEIRD: boom", unknown.message)
        assertTrue(unknown.retryable)
        assertEquals("unknown error code WEIRD", MailErrorMapper.normalize(" WEIRD ", "  ", null).message)
        assertEquals(MailErrorCodes.INTERNAL, MailErrorMapper.normalize(null, "boom", null).code)
        assertEquals("boom", MailErrorMapper.normalize(null, "boom", null).message)
        assertEquals(MailErrorCodes.INTERNAL, MailErrorMapper.normalize("", null, null).code)
    }

    @Test
    fun blankMessagesGetTheDefaultTextOfTheCode() {
        MailErrorCodes.ALL.forEach { code ->
            val error = MailErrorMapper.normalize(code, "  ", null)
            assertEquals(MailErrorMapper.defaultMessage(code), error.message)
            assertTrue(code, error.message.isNotBlank())
        }
        assertEquals("mail operation failed", MailErrorMapper.defaultMessage("SOMETHING_ELSE"))
    }

    @Test
    fun explicitRetryHintAndDetailsAreKept() {
        val error = MailErrorMapper.normalize(MailErrorCodes.AUTH_FAILED, "m", retryable = true, details = """{"x":1}""")
        assertTrue(error.retryable)
        assertEquals("""{"x":1}""", error.details)
        assertFalse(MailErrorMapper.normalize(MailErrorCodes.TIMEOUT, "m", retryable = false).retryable)
    }

    @Test
    fun longMessagesAreTruncatedOnCodePointBoundaries() {
        val ascii = "a".repeat(MailContract.MAX_ERROR_MESSAGE_BYTES * 2)
        val truncated = MailErrorMapper.normalize(MailErrorCodes.SERVER_ERROR, ascii, null).message
        assertEquals(MailContract.MAX_ERROR_MESSAGE_BYTES, truncated.toByteArray(StandardCharsets.UTF_8).size)
        assertTrue(truncated.endsWith("..."))

        val cjk = "汉".repeat(MailContract.MAX_ERROR_MESSAGE_BYTES)
        val cjkTruncated = MailErrorMapper.normalize(MailErrorCodes.SERVER_ERROR, cjk, null).message
        assertTrue(cjkTruncated.toByteArray(StandardCharsets.UTF_8).size <= MailContract.MAX_ERROR_MESSAGE_BYTES)
        assertTrue(cjkTruncated.endsWith("..."))
        assertTrue(cjkTruncated.dropLast(3).all { it == '汉' })

        val emoji = "😀".repeat(MailContract.MAX_ERROR_MESSAGE_BYTES)
        val emojiTruncated = MailErrorMapper.normalize(MailErrorCodes.SERVER_ERROR, emoji, null).message
        assertTrue(emojiTruncated.toByteArray(StandardCharsets.UTF_8).size <= MailContract.MAX_ERROR_MESSAGE_BYTES)
        assertEquals(0, emojiTruncated.dropLast(3).length % 2)
        assertFalse(emojiTruncated.dropLast(3).contains('�'))

        val exact = "b".repeat(MailContract.MAX_ERROR_MESSAGE_BYTES)
        assertEquals(exact, MailErrorMapper.normalize(MailErrorCodes.SERVER_ERROR, exact, null).message)
    }

    @Test
    fun throwablesMapByTypeAndPhase() {
        val call = MailErrorMapper.Phase.CALL
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, MailErrorMapper.fromThrowable(IllegalArgumentException("bad"), call).code)
        assertEquals("invalid argument: bad", MailErrorMapper.fromThrowable(IllegalArgumentException("bad"), call).message)
        assertEquals(MailErrorCodes.ATTACHMENT_NOT_FOUND, MailErrorMapper.fromThrowable(FileNotFoundException("x"), call).code)
        assertEquals(MailErrorCodes.IO_FAILED, MailErrorMapper.fromThrowable(IOException("disk"), MailErrorMapper.Phase.TRANSFER).code)
        assertEquals(MailErrorCodes.CANCELLED, MailErrorMapper.fromThrowable(CancellationException(), call).code)
        assertEquals(MailErrorCodes.CANCELLED, MailErrorMapper.fromThrowable(InterruptedException(), call).code)
        assertEquals(MailErrorCodes.PLUGIN_UNAVAILABLE, MailErrorMapper.fromThrowable(SecurityException("uid"), call).code)
        assertEquals(MailErrorCodes.PLUGIN_UNAVAILABLE, MailErrorMapper.fromThrowable(IllegalStateException("Plugin not authorized"), MailErrorMapper.Phase.OPEN).code)
        assertEquals(MailErrorCodes.PLUGIN_UNAVAILABLE, MailErrorMapper.fromThrowable(IllegalStateException("no plugin"), MailErrorMapper.Phase.DISCOVERY).code)
        assertEquals(MailErrorCodes.INTERNAL, MailErrorMapper.fromThrowable(IllegalStateException("bug"), call).code)
        assertEquals(MailErrorCodes.INTERNAL, MailErrorMapper.fromThrowable(RuntimeException(), call).code)
        assertEquals("mail host failure", MailErrorMapper.fromThrowable(RuntimeException(), call).message)
        MailErrorCodes.ALL.forEach { code ->
            val passthrough = MailError(code, "m", retryable = true, details = "d")
            assertEquals(passthrough, MailErrorMapper.fromThrowable(MailPluginException(passthrough), call))
        }
    }

    @Test
    fun timeoutCancellationIsARetryableTimeout() {
        val timeout = runBlocking {
            try {
                withTimeout(1) { delay(10_000) }
                null
            } catch (e: TimeoutCancellationException) {
                e
            }
        }
        val error = MailErrorMapper.fromThrowable(timeout!!, MailErrorMapper.Phase.CALL)
        assertEquals(MailErrorCodes.TIMEOUT, error.code)
        assertTrue(error.retryable)
        assertFalse(MailErrorMapper.fromThrowable(CancellationException(), MailErrorMapper.Phase.CALL).retryable)
    }
}
