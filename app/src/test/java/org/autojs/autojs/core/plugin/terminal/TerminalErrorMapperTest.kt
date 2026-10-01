package org.autojs.autojs.core.plugin.terminal

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

class TerminalErrorMapperTest {

    private val call = TerminalErrorMapper.Phase.CALL
    private val open = TerminalErrorMapper.Phase.OPEN
    private val discovery = TerminalErrorMapper.Phase.DISCOVERY

    @Test
    fun `every known code normalizes to itself with a default message`() {
        TerminalErrorCodes.ALL.forEach { code ->
            assertEquals(TerminalError(code, "m"), TerminalErrorMapper.normalize(code, "m"))
            val blank = TerminalErrorMapper.normalize(code, "  ")
            assertEquals(code, blank.code)
            assertTrue(code, blank.message.isNotBlank())
            assertNull(blank.sessionId)
        }
        assertEquals("s-1", TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "x", "s-1").sessionId)
        assertNull(TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "x", " ").sessionId)
    }

    @Test
    fun `unknown codes collapse to INTERNAL and keep the original code in the message`() {
        val error = TerminalErrorMapper.normalize("TELEPORT_FAILED", "no pad")
        assertEquals(TerminalErrorCodes.INTERNAL, error.code)
        assertEquals("unknown error code TELEPORT_FAILED: no pad", error.message)
        assertEquals("unknown error code TELEPORT_FAILED", TerminalErrorMapper.normalize("TELEPORT_FAILED", null).message)
        assertEquals(TerminalErrorMapper.defaultMessage(TerminalErrorCodes.INTERNAL), TerminalErrorMapper.normalize(null, null).message)
    }

    @Test
    fun `plugin answers may not carry host-only codes`() {
        TerminalErrorCodes.HOST_ONLY.forEach { code ->
            val error = TerminalErrorMapper.fromPlugin(code, "detail", "s-2")
            assertEquals(code, TerminalErrorCodes.INTERNAL, error.code)
            assertTrue(error.message.contains(code))
            assertTrue(error.message.contains("detail"))
            assertEquals("s-2", error.sessionId)
        }
        assertEquals(TerminalError(TerminalErrorCodes.PTY_FAILED, "ptmx", null), TerminalErrorMapper.fromPlugin("PTY_FAILED", "ptmx"))
    }

    @Test
    fun `encoded exception messages decode plugin codes but never host-only ones`() {
        assertEquals("SESSION_NOT_FOUND" to "s-3", TerminalErrorMapper.decode("SESSION_NOT_FOUND: s-3"))
        assertEquals("SESSION_NOT_FOUND" to null, TerminalErrorMapper.decode("SESSION_NOT_FOUND"))
        assertEquals("SESSION_NOT_FOUND" to null, TerminalErrorMapper.decode(" SESSION_NOT_FOUND : "))
        assertNull(TerminalErrorMapper.decode("TIMEOUT: host only"))
        assertNull(TerminalErrorMapper.decode("plain message: with colon"))
        assertNull(TerminalErrorMapper.decode(""))
        val encoded = TerminalErrorMapper.encoded(IllegalStateException("SESSION_CLOSED: exited"))!!
        assertEquals(TerminalError(TerminalErrorCodes.SESSION_CLOSED, "exited"), encoded)
        assertNull(TerminalErrorMapper.encoded(IllegalStateException("something else")))
        assertNull(TerminalErrorMapper.encoded(IllegalStateException()))
    }

    @Test
    fun `throwables map by phase`() {
        val own = TerminalPluginException(TerminalError(TerminalErrorCodes.SESSION_LIMIT, "full"))
        assertSame(own.error, TerminalErrorMapper.fromThrowable(own, call))

        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, TerminalErrorMapper.fromThrowable(IllegalArgumentException("bad"), call).code)
        assertEquals(TerminalErrorCodes.DIRECTORY_INACCESSIBLE, TerminalErrorMapper.fromThrowable(IllegalArgumentException("DIRECTORY_INACCESSIBLE: /x"), call).code)
        assertEquals(TerminalErrorCodes.PLUGIN_UNAVAILABLE, TerminalErrorMapper.fromThrowable(IllegalStateException("No plugin service found"), discovery).code)
        assertEquals(TerminalErrorCodes.PLUGIN_UNAVAILABLE, TerminalErrorMapper.fromThrowable(IllegalStateException("Plugin not authorized"), open).code)
        assertEquals(TerminalErrorCodes.INTERNAL, TerminalErrorMapper.fromThrowable(IllegalStateException("odd"), call).code)
        assertEquals(TerminalErrorCodes.SESSION_CLOSED, TerminalErrorMapper.fromThrowable(IllegalStateException("SESSION_CLOSED: gone"), call).code)
        assertEquals(TerminalErrorCodes.PLUGIN_UNAVAILABLE, TerminalErrorMapper.fromThrowable(SecurityException("denied"), call).code)
        assertEquals(TerminalErrorCodes.CANCELLED, TerminalErrorMapper.fromThrowable(CancellationException("stop"), call).code)
        assertEquals(TerminalErrorCodes.CANCELLED, TerminalErrorMapper.fromThrowable(InterruptedException(), call).code)
        assertEquals(TerminalErrorCodes.CANCELLED, TerminalErrorMapper.fromThrowable(InterruptedIOException(), call).code)
        assertEquals(TerminalErrorCodes.INTERNAL, TerminalErrorMapper.fromThrowable(RuntimeException("boom"), call).code)
        assertEquals("terminal host failure: boom", TerminalErrorMapper.fromThrowable(RuntimeException("boom"), call).message)

        val timeout = runCatching { runBlocking { withTimeout(1) { delay(1_000) } } }.exceptionOrNull()
        assertTrue(timeout is TimeoutCancellationException)
        assertEquals(TerminalErrorCodes.TIMEOUT, TerminalErrorMapper.fromThrowable(timeout!!, call).code)
    }

    @Test
    fun `messages are truncated to the byte ceiling without splitting the limit`() {
        val long = "终".repeat(TerminalErrorMapper.MAX_MESSAGE_BYTES)
        val truncated = TerminalErrorMapper.truncate(long, TerminalErrorMapper.MAX_MESSAGE_BYTES)
        assertTrue(truncated.toByteArray(StandardCharsets.UTF_8).size <= TerminalErrorMapper.MAX_MESSAGE_BYTES)
        assertTrue(truncated.isNotEmpty())
        assertEquals("short", TerminalErrorMapper.truncate("short", 100))
        assertTrue(TerminalErrorMapper.normalize(TerminalErrorCodes.INTERNAL, long).message.toByteArray(StandardCharsets.UTF_8).size <= TerminalErrorMapper.MAX_MESSAGE_BYTES)
    }

    @Test
    fun `exception text carries the code first`() {
        val e = TerminalPluginException(TerminalError(TerminalErrorCodes.NODE_UNAVAILABLE, "no node"))
        assertEquals("NODE_UNAVAILABLE: no node", e.message)
        assertEquals(TerminalErrorCodes.NODE_UNAVAILABLE, e.code)
    }
}
