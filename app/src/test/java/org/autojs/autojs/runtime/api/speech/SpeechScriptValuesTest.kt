package org.autojs.autojs.runtime.api.speech

import org.autojs.autojs.core.plugin.speech.SpeechCapabilities
import org.autojs.autojs.core.plugin.speech.SpeechValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The plain values of the `speech` script API (3-Macaw Speech roadmap appendix B.3): script-friendly numbers, the
 * accumulated session text and the `capabilities()` object.
 * zh-CN: `speech` 脚本 API 的普通值 (3-Macaw Speech 路线图附录 B.3): 适合脚本的数字, 累计的会话文本与 `capabilities()` 对象.
 */
class SpeechScriptValuesTest {

    @Test
    fun numbersBecomeScriptFriendly() {
        assertEquals(5, SpeechValues.number(5L))
        assertEquals(1.0E10, SpeechValues.number(10_000_000_000L))
        assertEquals(0.5, SpeechValues.number(0.5f))
        assertEquals(7, SpeechValues.number(7.toShort()))
    }

    @Test
    fun finalsJoinWithoutSpacesBetweenIdeographs() {
        assertEquals("", SpeechScriptValues.joinText(emptyList()))
        assertEquals("hello world", SpeechScriptValues.joinText(listOf("hello", " world ")))
        assertEquals("你好世界", SpeechScriptValues.joinText(listOf("你好", "世界")))
        assertEquals("AutoJs6你好42 go", SpeechScriptValues.joinText(listOf("AutoJs6", "你好", "", "42", "go")))
        assertEquals("ok,next", SpeechScriptValues.joinText(listOf("ok,", "next")))
    }

    @Test
    fun roundsAreReadAndStripped() {
        assertEquals(2, SpeechScriptValues.roundOf(mapOf("round" to 2)))
        assertNull(SpeechScriptValues.roundOf(mapOf("text" to "x")))
        assertEquals(mapOf("text" to "x"), SpeechScriptValues.withoutRound(mapOf("text" to "x", "round" to 1)))
        assertEquals("x", SpeechScriptValues.textOf(mapOf("text" to "x")))
    }

    @Test
    fun capabilitiesListEveryField() {
        val value = SpeechScriptValues.capabilities(
            SpeechCapabilities(
                contractVersion = 1,
                features = listOf("asr", "vad"),
                catalogVersion = 3,
                engineName = "sherpa-onnx",
                engineVersion = "1.13.8",
                ttsEnginePackage = null,
                microphoneGranted = true,
                packageName = "io.github.supermonster003.autojs6.plugin.three.macaw.speech",
                pluginVersionName = "1.0.0",
                pluginVersionCode = 12L,
            ),
        )
        assertEquals(
            listOf(
                "contractVersion", "features", "catalogVersion", "engineName", "engineVersion", "ttsEnginePackage", "microphoneGranted",
                "packageName", "versionName", "versionCode",
            ),
            value.keys.toList(),
        )
        assertEquals(12, value["versionCode"])
        assertEquals(listOf("asr", "vad"), value["features"])
    }
}
