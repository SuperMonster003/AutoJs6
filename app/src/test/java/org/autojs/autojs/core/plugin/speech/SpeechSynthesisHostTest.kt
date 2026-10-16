package org.autojs.autojs.core.plugin.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.speech.api.SpeechResultKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The host side of `synthesizeToFile` (3-Macaw Speech roadmap P4.3): the script result keeps the plugin's `warnings`, and
 * the host's wait is renewed by every progress instead of bounding the whole synthesis.
 * zh-CN: `synthesizeToFile` 的宿主侧 (3-Macaw Speech 路线图 P4.3): 脚本结果保留插件的 `warnings`, 宿主的等待随每次进度续期, 而不是限制整个合成.
 */
class SpeechSynthesisHostTest {

    @Test
    fun theResultCarriesThePluginWarnings() {
        val warning = mapOf(SpeechBundles.FIELD_CODE to "INVALID_OPTIONS", SpeechBundles.FIELD_MESSAGE to "single speaker; speaker '3' was ignored")
        val plain = mapOf(
            SpeechResultKeys.SAMPLE_RATE to 44100,
            SpeechResultKeys.DURATION_MILLIS to 1234,
            SpeechResultKeys.MODEL_ID to "tts.vits.melo.zh-en",
            SpeechResultKeys.SPEAKER_ID to 0,
            SpeechResultKeys.SIZE_BYTES to 1,
            SpeechResultKeys.WARNINGS to listOf(warning),
        )
        val result = SpeechBundles.synthesisResult("/sdcard/a.wav", plain, 108_884L)
        assertEquals(
            listOf("path", "sampleRate", "durationMillis", "modelId", "speakerId", "sizeBytes", "warnings"),
            result.keys.toList(),
        )
        assertEquals(108_884, result[SpeechResultKeys.SIZE_BYTES])
        assertEquals(listOf(warning), result[SpeechResultKeys.WARNINGS])
        assertEquals(emptyList<Any?>(), SpeechBundles.synthesisResult("/sdcard/a.wav", emptyMap(), 44L)[SpeechResultKeys.WARNINGS])
    }

    @Test
    fun progressRenewsTheDeadline() = runBlocking {
        val deadline = SpeechProgressDeadline()
        val outcome = CompletableDeferred<String>()
        launch {
            // Ten progress ticks 60 ms apart: 600 ms in total, three times the 200 ms window.
            // zh-CN: 十次进度, 间隔 60 ms: 共 600 ms, 为 200 ms 窗口的三倍.
            repeat(10) {
                delay(60)
                deadline.renew()
            }
            outcome.complete("done")
        }
        val started = System.nanoTime()
        assertEquals("done", deadline.await(200, outcome))
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 500)
    }

    @Test
    fun silenceEndsTheWait() = runBlocking {
        val deadline = SpeechProgressDeadline()
        val outcome = CompletableDeferred<String>()
        launch {
            delay(100)
            deadline.renew()
        }
        val started = System.nanoTime()
        try {
            deadline.await(200, outcome)
            fail("expected a timeout")
        } catch (_: TimeoutCancellationException) {
            val elapsed = (System.nanoTime() - started) / 1_000_000
            assertTrue("timed out after $elapsed ms, the window counts from the last progress", elapsed in 290..2_000)
        }
        outcome.cancel()
    }
}
