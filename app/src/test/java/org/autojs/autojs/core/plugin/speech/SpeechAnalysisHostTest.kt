package org.autojs.autojs.core.plugin.speech

import org.autojs.plugin.speech.api.SpeechResultKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/**
 * The host side of the offline analyses (3-Macaw Speech roadmap D147 - D151): the script results are rebuilt from the
 * plain completions, parallel arrays included, scores keep four decimals and arrays of different lengths are refused.
 * zh-CN: 离线分析的宿主侧 (3-Macaw Speech 路线图 D147 - D151): 脚本结果由普通完成值 (含并列数组) 还原, 分数保留四位小数, 长度不一的数组被拒绝.
 */
class SpeechAnalysisHostTest {

    @Test
    fun diarizationRebuildsTheSegmentsFromParallelArrays() {
        val plain = mapOf(
            SpeechResultKeys.SEGMENT_STARTS to listOf(0, 1520, 4200),
            SpeechResultKeys.SEGMENT_ENDS to listOf(1480, 4100, 6000),
            SpeechResultKeys.SEGMENT_SPEAKERS to listOf(0, 1, 0),
            SpeechResultKeys.SPEAKER_COUNT to 2,
            SpeechResultKeys.DURATION_MILLIS to 6000,
            SpeechResultKeys.MODEL_ID to "segment.pyannote.3-0.int8",
        )
        val result = SpeechBundles.diarization(plain)
        assertEquals(listOf("segments", "speakers", "durationMillis", "modelId"), result.keys.toList())
        assertEquals(
            listOf(
                mapOf("startMillis" to 0, "endMillis" to 1480, "speaker" to 0),
                mapOf("startMillis" to 1520, "endMillis" to 4100, "speaker" to 1),
                mapOf("startMillis" to 4200, "endMillis" to 6000, "speaker" to 0),
            ),
            result["segments"],
        )
        assertEquals(2, result["speakers"])

        val named = SpeechBundles.diarization(
            plain + (SpeechResultKeys.NAMES to listOf(
                mapOf(SpeechResultKeys.SPEAKER to 0, SpeechResultKeys.NAME to "Alice", SpeechResultKeys.SCORE to 0.712345),
                mapOf(SpeechResultKeys.SPEAKER to 1, SpeechResultKeys.SCORE to 0.31),
            )),
        )
        assertEquals(
            listOf(mapOf("speaker" to 0, "name" to "Alice", "score" to 0.7123), mapOf("speaker" to 1, "name" to null, "score" to 0.31)),
            named["names"],
        )

        val empty = SpeechBundles.diarization(mapOf(SpeechResultKeys.DURATION_MILLIS to 900))
        assertEquals(emptyList<Any?>(), empty["segments"])
        assertEquals(0, empty["speakers"])

        refused { SpeechBundles.diarization(plain + (SpeechResultKeys.SEGMENT_ENDS to listOf(1480))) }
    }

    @Test
    fun languageResultKeepsTheVotesAndWindows() {
        val result = SpeechBundles.languageResult(
            mapOf(
                SpeechResultKeys.LANGUAGE to "ja",
                SpeechResultKeys.CANDIDATES to listOf(
                    mapOf(SpeechResultKeys.LANGUAGE to "ja", SpeechResultKeys.SCORE to 0.6666666),
                    mapOf(SpeechResultKeys.LANGUAGE to "zh", SpeechResultKeys.SCORE to 0.3333333),
                ),
                SpeechResultKeys.SEGMENTS to listOf(
                    mapOf(SpeechResultKeys.START_MILLIS to 300, SpeechResultKeys.END_MILLIS to 8100, SpeechResultKeys.LANGUAGE to "ja"),
                ),
                SpeechResultKeys.DURATION_MILLIS to 30000,
                SpeechResultKeys.MODEL_ID to "langid.whisper.tiny.int8",
            ),
        )
        assertEquals(listOf("language", "candidates", "segments", "durationMillis", "modelId"), result.keys.toList())
        assertEquals(listOf(mapOf("language" to "ja", "score" to 0.6667), mapOf("language" to "zh", "score" to 0.3333)), result["candidates"])
        assertEquals(listOf(mapOf("startMillis" to 300, "endMillis" to 8100, "language" to "ja")), result["segments"])
    }

    @Test
    fun tagResultRebuildsTheTimelineOnlyWhenSent() {
        val plain = mapOf(
            SpeechResultKeys.LABELS to listOf(
                mapOf(
                    SpeechResultKeys.LABEL to "Dog", SpeechResultKeys.MID to "/m/0bt9lr", SpeechResultKeys.SCORE to 0.912345,
                    SpeechResultKeys.START_MILLIS to 10000, SpeechResultKeys.END_MILLIS to 20000,
                ),
            ),
            SpeechResultKeys.DURATION_MILLIS to 20000,
            SpeechResultKeys.MODEL_ID to "tagger.ced.mini.int8",
        )
        val plainResult = SpeechBundles.tagResult(plain)
        assertEquals(listOf("labels", "durationMillis", "modelId"), plainResult.keys.toList())
        assertFalse("timeline" in plainResult)
        assertEquals(0.9123, (plainResult["labels"] as List<*>).filterIsInstance<Map<*, *>>().single()["score"])

        val timeline = plain + mapOf(
            SpeechResultKeys.TIMELINE_STARTS to listOf(0, 10000),
            SpeechResultKeys.TIMELINE_ENDS to listOf(10000, 20000),
            SpeechResultKeys.TIMELINE_TOP_K to 2,
            SpeechResultKeys.TIMELINE_LABELS to listOf(1, 0, 0, 1),
            SpeechResultKeys.TIMELINE_SCORES to listOf(0.5, 0.25, 0.9, 0.125),
            SpeechResultKeys.LABEL_NAMES to listOf("Dog", "Speech"),
            SpeechResultKeys.LABEL_MIDS to listOf("/m/0bt9lr", "/m/09x0r"),
        )
        assertEquals(
            listOf(
                mapOf(
                    "startMillis" to 0, "endMillis" to 10000,
                    "labels" to listOf(mapOf("label" to "Speech", "mid" to "/m/09x0r", "score" to 0.5), mapOf("label" to "Dog", "mid" to "/m/0bt9lr", "score" to 0.25)),
                ),
                mapOf(
                    "startMillis" to 10000, "endMillis" to 20000,
                    "labels" to listOf(mapOf("label" to "Dog", "mid" to "/m/0bt9lr", "score" to 0.9), mapOf("label" to "Speech", "mid" to "/m/09x0r", "score" to 0.125)),
                ),
            ),
            SpeechBundles.tagResult(timeline)["timeline"],
        )

        refused { SpeechBundles.tagResult(timeline + (SpeechResultKeys.TIMELINE_LABELS to listOf(1, 0, 0, 2))) }
        refused { SpeechBundles.tagResult(timeline + (SpeechResultKeys.TIMELINE_SCORES to listOf(0.5))) }
        refused { SpeechBundles.tagResult(timeline + (SpeechResultKeys.TIMELINE_ENDS to listOf(10000))) }
    }

    private fun refused(block: () -> Unit) {
        try {
            block()
            fail("expected INTERNAL")
        } catch (e: SpeechPluginException) {
            assertEquals("INTERNAL", e.error.code)
        }
    }
}
