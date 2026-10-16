package org.autojs.autojs.runtime.api.speech

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.speech.SpeechImportRequest
import org.autojs.autojs.core.plugin.speech.SpeechInstallRequest
import org.autojs.autojs.core.plugin.speech.SpeechKeyword
import org.autojs.autojs.core.plugin.speech.SpeechListenRequest
import org.autojs.autojs.core.plugin.speech.SpeechModelQuery
import org.autojs.autojs.core.plugin.speech.SpeechPluginException
import org.autojs.autojs.core.plugin.speech.SpeechRecognizeRequest
import org.autojs.autojs.core.plugin.speech.SpeechSynthesisRequest
import org.autojs.autojs.core.plugin.speech.SpeechVadRequest
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The pure argument rules of the `speech` script API (3-Macaw Speech roadmap P1.4, appendix B.2): defaults, unknown keys,
 * shapes and ranges (`INVALID_OPTIONS`) and the contract ceilings checked on the host side first (`LIMIT_EXCEEDED`).
 * zh-CN: `speech` 脚本 API 的纯参数规则 (3-Macaw Speech 路线图 P1.4, 附录 B.2): 默认值, 未知键, 形态与范围 (`INVALID_OPTIONS`), 以及先在宿主侧
 * 检查的契约上限 (`LIMIT_EXCEEDED`).
 */
class SpeechScriptArgumentsTest {

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun refused(code: String = SpeechErrorCodes.INVALID_OPTIONS, block: () -> Unit): String {
        try {
            block()
            fail("expected $code")
        } catch (e: SpeechPluginException) {
            assertEquals(code, e.code)
            return e.error.message
        }
        throw AssertionError()
    }

    @Test
    fun listenUsesTheAppendixDefaults() {
        val defaults = SpeechScriptArguments.listen(null)
        assertEquals(SpeechListenRequest(), defaults)
        assertEquals(SpeechContract.SOURCE_MIC, defaults.source)
        assertEquals(1200L, defaults.silenceEndMillis)
        assertEquals(300_000L, defaults.maxDurationMillis)
        assertEquals(15_000L, defaults.timeoutMillis)
        assertTrue(defaults.vad && defaults.punctuation)
        assertEquals(SpeechListenRequest(), SpeechScriptArguments.listen(json("{}")))
    }

    @Test
    fun listenNormalizesEveryOption() {
        val request = SpeechScriptArguments.listen(
            json(
                """{"model":" asr.zipformer ","language":"zh_CN","source":"External","sampleRate":16000,"vad":false,
                "silenceEndMillis":800,"maxDurationMillis":1800000,"timeoutMillis":0,"hotwords":["AutoJs6"," AutoJs6 ","speech"],"punctuation":false}""",
            ),
        )
        assertEquals(
            SpeechListenRequest(
                model = "asr.zipformer",
                language = "zh-CN",
                source = SpeechContract.SOURCE_EXTERNAL,
                sampleRate = 16_000,
                vad = false,
                silenceEndMillis = 800,
                maxDurationMillis = 1_800_000,
                timeoutMillis = 0,
                hotwords = listOf("AutoJs6", "speech"),
                punctuation = false,
            ),
            request,
        )
        assertTrue(request.isExternal)
    }

    @Test
    fun listenRefusesShapesAndRangesAndLimitsTheCeilings() {
        assertTrue(refused { SpeechScriptArguments.listen(json("""{"sauce":"mic"}""")) }.contains("options.sauce"))
        refused { SpeechScriptArguments.listen(json("""{"source":"file"}""")) }
        refused { SpeechScriptArguments.listen(json("""{"sampleRate":44100}""")) }
        refused { SpeechScriptArguments.listen(json("""{"silenceEndMillis":0}""")) }
        refused { SpeechScriptArguments.listen(json("""{"silenceEndMillis":1.5}""")) }
        refused { SpeechScriptArguments.listen(json("""{"timeoutMillis":-1}""")) }
        refused { SpeechScriptArguments.listen(json("""{"maxDurationMillis":0}""")) }
        refused { SpeechScriptArguments.listen(json("""{"vad":"yes"}""")) }
        refused { SpeechScriptArguments.listen(json("""{"language":"zh cn"}""")) }
        refused { SpeechScriptArguments.listen(json("""{"model":"  "}""")) }
        refused { SpeechScriptArguments.listen(json("""{"hotwords":"AutoJs6"}""")) }
        refused { SpeechScriptArguments.listen(json("[]")) }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.listen(json("""{"maxDurationMillis":1800001}""")) }
        val tooMany = (0..SpeechLimits.MAX_HOTWORDS).joinToString(",", "[", "]") { "\"w$it\"" }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.listen(json("""{"hotwords":$tooMany}""")) }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.listen(json("""{"model":"${"m".repeat(SpeechLimits.MAX_TOKEN_CHARS + 1)}"}""")) }
    }

    @Test
    fun wakeTakesStringsAndKeywordObjects() {
        val request = SpeechScriptArguments.wake(json("""["xiao liu", {"keyword":" hey macaw ","boost":1.5,"threshold":0.25}]"""), null)
        assertEquals(listOf(SpeechKeyword("xiao liu"), SpeechKeyword("hey macaw", 1.5, 0.25)), request.keywords)
        assertEquals(1500L, request.minIntervalMillis)
        assertNull(request.thenListen)
        assertEquals(listOf(SpeechKeyword("alone")), SpeechScriptArguments.wake(json("\"alone\""), null).keywords)

        val listening = SpeechScriptArguments.wake(json("""["a"]"""), json("""{"thenListen":{"silenceEndMillis":900},"minIntervalMillis":0,"model":"kws.zh-en"}"""))
        assertEquals(SpeechListenRequest(silenceEndMillis = 900), listening.thenListen)
        assertEquals(0L, listening.minIntervalMillis)
        assertEquals("kws.zh-en", listening.model)
        assertEquals(SpeechListenRequest(), SpeechScriptArguments.wake(json("""["a"]"""), json("""{"thenListen":true}""")).thenListen)
        assertNull(SpeechScriptArguments.wake(json("""["a"]"""), json("""{"thenListen":false}""")).thenListen)
    }

    @Test
    fun wakeRefusesBadKeywordsAndLimitsTheirCount() {
        refused { SpeechScriptArguments.wake(null, null) }
        refused { SpeechScriptArguments.wake(json("[]"), null) }
        refused { SpeechScriptArguments.wake(json("[\" \"]"), null) }
        refused { SpeechScriptArguments.wake(json("[\"a\",\"a\"]"), null) }
        refused { SpeechScriptArguments.wake(json("""[{"keyword":"a","threshold":2}]"""), null) }
        refused { SpeechScriptArguments.wake(json("""[{"keyword":"a","weight":1}]"""), null) }
        refused { SpeechScriptArguments.wake(json("[1]"), null) }
        refused { SpeechScriptArguments.wake(json("[\"a\"]"), json("""{"thenListen":{"source":"external"}}""")) }
        refused { SpeechScriptArguments.wake(json("[\"a\"]"), json("""{"thenListen":"yes"}""")) }
        val tooMany = (0..SpeechLimits.MAX_KEYWORDS).joinToString(",", "[", "]") { "\"k$it\"" }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.wake(json(tooMany), null) }
    }

    @Test
    fun recognizeAndDetectVoiceCarryThePcmSampleRate() {
        val plain = SpeechScriptArguments.recognize(null)
        assertEquals(SpeechRecognizeRequest(), plain.request)
        assertEquals(16_000, plain.sampleRate)
        val custom = SpeechScriptArguments.recognize(json("""{"model":"asr.sense-voice","language":"yue","hotwords":["a"],"punctuation":false,"vad":false,"sampleRate":44100}"""))
        assertEquals(SpeechRecognizeRequest("asr.sense-voice", "yue", listOf("a"), punctuation = false, vad = false), custom.request)
        assertEquals(44_100, custom.sampleRate)
        refused { SpeechScriptArguments.recognize(json("""{"sampleRate":4000}""")) }
        refused { SpeechScriptArguments.recognize(json("""{"source":"mic"}""")) }

        val vad = SpeechScriptArguments.detectVoice(json("""{"threshold":0.6,"minSpeechMillis":250,"minSilenceMillis":100}"""))
        assertEquals(SpeechVadRequest(threshold = 0.6, minSpeechMillis = 250, minSilenceMillis = 100), vad.request)
        refused { SpeechScriptArguments.detectVoice(json("""{"threshold":1.5}""")) }
        refused { SpeechScriptArguments.detectVoice(json("""{"minSpeechMillis":-1}""")) }
    }

    @Test
    fun pathsAreFilePathsNotUris() {
        assertEquals("audio/a.wav", SpeechScriptArguments.path(json("\"audio/a.wav\""), "source"))
        assertEquals("/sdcard/a.wav", SpeechScriptArguments.path(json("\"/sdcard/a.wav\""), "source"))
        refused { SpeechScriptArguments.path(json("\"content://media/1\""), "source") }
        refused { SpeechScriptArguments.path(json("\" \""), "source") }
        refused { SpeechScriptArguments.path(json("1"), "source") }
    }

    @Test
    fun synthesizeLimitsTheTextAndChecksTheVoice() {
        assertEquals(SpeechSynthesisRequest("hello"), SpeechScriptArguments.synthesize(json("\"hello\""), null))
        assertEquals(
            SpeechSynthesisRequest("hi", model = "tts.vits", speaker = "10", speed = 1.25, format = "wav"),
            SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"model":"tts.vits","speaker":10,"speed":1.25,"format":"WAV"}""")),
        )
        assertEquals("narrator", SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"speaker":"narrator"}""")).speaker)
        refused { SpeechScriptArguments.synthesize(json("\"  \""), null) }
        refused { SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"speed":3}""")) }
        refused { SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"speaker":-1}""")) }
        refused { SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"format":"mp3"}""")) }
        refused { SpeechScriptArguments.synthesize(json("\"hi\""), json("""{"voice":"x"}""")) }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.synthesize(json("\"${"a".repeat(SpeechLimits.MAX_SYNTH_TEXT_CHARS + 1)}\""), null) }
    }

    @Test
    fun speakMapsTheVoiceAndPassesTtsOptionsThrough() {
        val speak = SpeechScriptArguments.speak(json("""{"model":"tts.vits","speaker":3,"speed":0.8,"pitch":1.1,"queue":"add"}"""))
        assertEquals("tts.vits/3", speak.voice)
        assertEquals(0.8, speak.speed)
        assertEquals(setOf("pitch", "queue"), speak.ttsOptions.keySet())
        assertEquals("tts.vits/0", SpeechScriptArguments.speak(json("""{"model":"tts.vits"}""")).voice)
        assertNull(SpeechScriptArguments.speak(null).voice)
        listOf("engine", "voice", "rate", "speechRate").forEach { key ->
            refused { SpeechScriptArguments.speak(json("""{"$key":"x"}""")) }
        }
        refused { SpeechScriptArguments.speak(json("""{"speaker":1}""")) }
    }

    @Test
    fun modelArgumentsAreChecked() {
        assertEquals(SpeechModelQuery(), SpeechScriptArguments.modelQuery(null))
        assertEquals(SpeechModelQuery("asr", "zh", true), SpeechScriptArguments.modelQuery(json("""{"kind":"ASR","language":"zh","installed":true}""")))
        refused { SpeechScriptArguments.modelQuery(json("""{"kind":"music"}""")) }
        assertEquals(SpeechInstallRequest("tts.matcha", acceptLicense = true), SpeechScriptArguments.install(json("\"tts.matcha\""), json("""{"acceptLicense":true}""")))
        refused { SpeechScriptArguments.install(json("\"tts.matcha\""), json("""{"onProgress":1}""")) }
        refused { SpeechScriptArguments.install(null, null) }
        assertEquals(
            SpeechImportRequest("my.vits", "tts", "vits", "My voice", listOf("zh", "en"), acceptLicense = false),
            SpeechScriptArguments.importModel(json("""{"id":"my.vits","kind":"tts","modelType":"vits","name":" My voice ","languages":["zh","en"]}""")),
        )
        refused { SpeechScriptArguments.importModel(json("""{"languages":"zh"}""")) }
        assertEquals("kws", SpeechScriptArguments.kind(json("\"KWS\""), "kind"))
        refused { SpeechScriptArguments.kind(json("\"x\""), "kind") }
        assertFalse(SpeechScriptArguments.modelId(json("\" asr.x \""), "id").contains(' '))
    }
}
