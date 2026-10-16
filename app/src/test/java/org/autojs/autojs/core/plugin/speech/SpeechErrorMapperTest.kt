package org.autojs.autojs.core.plugin.speech

import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Error mapping of the speech host client (3-Macaw Speech roadmap D27, P1.4): unknown codes, encoded plugin exceptions,
 * the mapping of host failures per phase and the message ceiling.
 * zh-CN: 语音宿主客户端的错误映射 (3-Macaw Speech 路线图 D27, P1.4): 未知错误码, 编码的插件异常, 按阶段映射宿主失败, 以及信息上限.
 */
class SpeechErrorMapperTest {

    @Test
    fun unknownCodesCollapseToInternalAndKeepTheirName() {
        val error = SpeechErrorMapper.normalize("NOPE", "detail")
        assertEquals(SpeechErrorCodes.INTERNAL, error.code)
        assertEquals("unknown error code NOPE: detail", error.message)
        assertEquals(SpeechErrorMapper.defaultMessage(SpeechErrorCodes.SESSION_BUSY), SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_BUSY, "  ").message)
        SpeechErrorCodes.ALL.forEach { code -> assertTrue(code, SpeechErrorMapper.defaultMessage(code).isNotBlank()) }
    }

    @Test
    fun encodedPluginExceptionsKeepTheirCode() {
        val error = SpeechErrorMapper.fromThrowable(IllegalStateException("MODEL_MISSING: no asr model"), SpeechErrorMapper.Phase.OPEN)
        assertEquals(SpeechErrorCodes.MODEL_MISSING, error.code)
        assertEquals("no asr model", error.message)
        assertEquals(SpeechErrorCodes.SESSION_BUSY, SpeechErrorMapper.fromThrowable(IllegalStateException("SESSION_BUSY"), SpeechErrorMapper.Phase.OPEN).code)
        assertEquals(SpeechErrorCodes.PERMISSION_DENIED, SpeechErrorMapper.fromThrowable(SecurityException("PERMISSION_DENIED: RECORD_AUDIO"), SpeechErrorMapper.Phase.OPEN).code)
        assertNull(SpeechErrorMapper.encoded(IllegalStateException("plain message")))
    }

    @Test
    fun hostFailuresMapByPhase() {
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, SpeechErrorMapper.fromThrowable(IllegalArgumentException("bad"), SpeechErrorMapper.Phase.CALL).code)
        assertEquals(SpeechErrorCodes.PLUGIN_UNAVAILABLE, SpeechErrorMapper.fromThrowable(IllegalStateException("none"), SpeechErrorMapper.Phase.DISCOVERY).code)
        assertEquals(SpeechErrorCodes.INTERNAL, SpeechErrorMapper.fromThrowable(IllegalStateException("odd"), SpeechErrorMapper.Phase.CALL).code)
        assertEquals(SpeechErrorCodes.SESSION_CANCELLED, SpeechErrorMapper.fromThrowable(CancellationException("stop"), SpeechErrorMapper.Phase.CALL).code)
        assertEquals(SpeechErrorCodes.SESSION_CANCELLED, SpeechErrorMapper.fromThrowable(InterruptedException(), SpeechErrorMapper.Phase.CALL).code)
        assertEquals(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, SpeechErrorMapper.fromThrowable(UnsupportedOperationException("x"), SpeechErrorMapper.Phase.CALL).code)
        assertEquals(SpeechErrorCodes.IO_FAILED, SpeechErrorMapper.fromThrowable(FileNotFoundException("a.wav"), SpeechErrorMapper.Phase.TRANSFER).code)
        assertEquals(SpeechErrorCodes.IO_FAILED, SpeechErrorMapper.fromThrowable(IOException("disk"), SpeechErrorMapper.Phase.TRANSFER).code)
        assertEquals(SpeechErrorCodes.INTERNAL, SpeechErrorMapper.fromThrowable(RuntimeException("boom"), SpeechErrorMapper.Phase.CALL).code)
        val thrown = SpeechErrorMapper.failure(SpeechErrorCodes.LIMIT_EXCEEDED, "too long")
        assertEquals(thrown.error, SpeechErrorMapper.fromThrowable(thrown, SpeechErrorMapper.Phase.CALL))
        assertSame(thrown, SpeechErrorMapper.wrap(thrown, SpeechErrorMapper.Phase.OPEN))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, SpeechErrorMapper.wrap(IllegalArgumentException("x"), SpeechErrorMapper.Phase.CALL).code)
        assertEquals("LIMIT_EXCEEDED: too long", thrown.message)
    }

    @Test
    fun messagesAreTruncatedToTheContractCeiling() {
        val long = "语".repeat(SpeechLimits.MAX_ERROR_MESSAGE_BYTES)
        val error = SpeechErrorMapper.normalize(SpeechErrorCodes.ENGINE_FAILED, long)
        assertTrue(error.message.toByteArray(StandardCharsets.UTF_8).size <= SpeechLimits.MAX_ERROR_MESSAGE_BYTES)
        assertEquals("short", SpeechErrorMapper.truncate("short", 16))
    }
}
