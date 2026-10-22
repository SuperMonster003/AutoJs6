package org.autojs.autojs.core.plugin.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The host-side audio checks of the speech client (3-Macaw Speech roadmap D15, D20): the WAV signature, the input
 * ceiling and the PCM16 WAV header the host writes around script PCM; numbers are pinned against the canonical format.
 * zh-CN: 语音客户端在宿主侧的音频检查 (3-Macaw Speech 路线图 D15, D20): WAV 签名, 输入上限, 以及宿主包裹脚本 PCM 时写入的 PCM16 WAV 头;
 * 数值按标准格式固定.
 */
class SpeechAudioTest {

    @Test
    fun theWavSignatureNeedsRiffAndWave() {
        assertTrue(SpeechAudio.isWav("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(SpeechAudio.isWav("RIFF\u0000\u0000\u0000\u0000AVI LIST".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(SpeechAudio.isWav("ID3\u0004\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(SpeechAudio.isWav("RIFF".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun nonWavFilesNeedAPluginThatDecodesThem() {
        // 3-Macaw Speech roadmap D84: a WAV goes to every plugin, anything else only to one with "audioDecode".
        val wavOnly = setOf(SpeechContract.FEATURE_ASR, SpeechContract.FEATURE_VAD)
        SpeechAudio.requireReadable(wav = true, features = wavOnly, name = "a.wav")
        SpeechAudio.requireReadable(wav = true, features = emptySet(), name = "a.wav")
        SpeechAudio.requireReadable(wav = false, features = wavOnly + SpeechContract.FEATURE_AUDIO_DECODE, name = "a.mp3")
        val refused = assertThrows(SpeechPluginException::class.java) { SpeechAudio.requireReadable(wav = false, features = wavOnly, name = "a.mp3") }
        assertEquals(SpeechErrorCodes.UNSUPPORTED_FORMAT, refused.code)
        assertTrue(refused.message, refused.message!!.contains("a.mp3 is not a WAV file"))
        assertTrue(refused.message, refused.message!!.contains("3-Macaw Speech 1.2.0"))
    }

    @Test
    fun theHeaderDescribesMonoPcm16() {
        val out = ByteArrayOutputStream()
        SpeechAudio.writeWavHeader(out, 16_000, 32_000)
        val header = out.toByteArray()
        assertEquals(SpeechAudio.WAV_HEADER_BYTES, header.size)
        assertTrue(SpeechAudio.isWav(header))
        val le = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 32_000, le.getInt(4))
        assertArrayEquals("fmt ".toByteArray(Charsets.ISO_8859_1), header.copyOfRange(12, 16))
        assertEquals(16, le.getInt(16))
        assertEquals(1, le.getShort(20).toInt())
        assertEquals(1, le.getShort(22).toInt())
        assertEquals(16_000, le.getInt(24))
        assertEquals(32_000, le.getInt(28))
        assertEquals(2, le.getShort(32).toInt())
        assertEquals(16, le.getShort(34).toInt())
        assertArrayEquals("data".toByteArray(Charsets.ISO_8859_1), header.copyOfRange(36, 40))
        assertEquals(32_000, le.getInt(40))
    }

    @Test
    fun oddPcmAndBadRatesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { SpeechAudio.writeWavHeader(ByteArrayOutputStream(), 16_000, 3) }
        assertThrows(IllegalArgumentException::class.java) { SpeechAudio.writeWavHeader(ByteArrayOutputStream(), 0, 2) }
    }

    @Test
    fun inputsAboveTheCeilingExceedTheLimit() {
        SpeechAudio.requireInputSize(SpeechLimits.MAX_AUDIO_FILE_BYTES, "a.wav")
        val error = assertThrows(SpeechPluginException::class.java) { SpeechAudio.requireInputSize(SpeechLimits.MAX_AUDIO_FILE_BYTES + 1, "a.wav") }
        assertEquals(SpeechErrorCodes.LIMIT_EXCEEDED, error.code)
    }
}
