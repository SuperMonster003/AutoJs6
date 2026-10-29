package org.autojs.autojs.runtime.api.speech

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.speech.SpeechDiarizeRequest
import org.autojs.autojs.core.plugin.speech.SpeechEnrollRequest
import org.autojs.autojs.core.plugin.speech.SpeechIdentifyRequest
import org.autojs.autojs.core.plugin.speech.SpeechImportRequest
import org.autojs.autojs.core.plugin.speech.SpeechInstallRequest
import org.autojs.autojs.core.plugin.speech.SpeechKeyword
import org.autojs.autojs.core.plugin.speech.SpeechLanguageRequest
import org.autojs.autojs.core.plugin.speech.SpeechListenRequest
import org.autojs.autojs.core.plugin.speech.SpeechModelQuery
import org.autojs.autojs.core.plugin.speech.SpeechPluginException
import org.autojs.autojs.core.plugin.speech.SpeechRecognizeRequest
import org.autojs.autojs.core.plugin.speech.SpeechSynthesisRequest
import org.autojs.autojs.core.plugin.speech.SpeechTagRequest
import org.autojs.autojs.core.plugin.speech.SpeechVadRequest
import org.autojs.autojs.core.plugin.speech.SpeechVerifyRequest
import org.autojs.autojs.runtime.api.tts.TtsSelection
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
        val speak = SpeechScriptArguments.speak(json("""{"model":"tts.vits","speaker":3,"speed":0.8,"pitch":1.1,"queue":"add"}"""), "hello")
        assertEquals(TtsSelection.ModelVoice("tts.vits", "3", "en"), speak.selection)
        assertEquals(0.8, speak.speed)
        assertEquals(setOf("pitch", "queue"), speak.ttsOptions.keySet())
        // Without a model the engine's voice follows the text unless the script names a language, which goes to tts.
        // zh-CN: 不给模型时, 引擎语音随文本语言而定, 除非脚本给出语言 (该语言交给 tts).
        assertEquals(TtsSelection.PreferredLanguage("en"), SpeechScriptArguments.speak(null, "hello").selection)
        assertEquals(TtsSelection.PreferredLanguage("zh"), SpeechScriptArguments.speak(json("""{"pitch":1.1}"""), "\u4f60\u597d").selection)
        assertNull(SpeechScriptArguments.speak(null, "42").selection)
        assertNull(SpeechScriptArguments.speak(json("""{"language":"zh-CN"}"""), "hello").selection)
        assertEquals(setOf("language"), SpeechScriptArguments.speak(json("""{"language":"zh-CN"}"""), "hello").ttsOptions.keySet())
        listOf("engine", "voice", "rate", "speechRate").forEach { key ->
            refused { SpeechScriptArguments.speak(json("""{"$key":"x"}"""), "hello") }
        }
        refused { SpeechScriptArguments.speak(json("""{"speaker":1}"""), "hello") }
        refused { SpeechScriptArguments.speak(json("""{"model":"tts.vits","speaker":"a/b"}"""), "hello") }
    }

    @Test
    fun speakLeavesTheSpeakerToTheTextLanguageOrTheLanguageOption() {
        // Without a speaker the engine picks the model's speaker for this language (Kokoro: 3 for Chinese, 0 for English).
        // zh-CN: 未给说话人时, 引擎按此语言选择模型的说话人 (Kokoro: 中文为 3 号, 英文为 0 号).
        val kokoro = "tts.kokoro.multi-lang.v1_1"
        assertEquals(TtsSelection.ModelVoice(kokoro, null, "zh"), SpeechScriptArguments.speak(json("""{"model":"$kokoro"}"""), "\u4efb\u52a1 done").selection)
        assertEquals(TtsSelection.ModelVoice(kokoro, null, "en"), SpeechScriptArguments.speak(json("""{"model":"$kokoro"}"""), "The task is done.").selection)
        assertEquals(TtsSelection.ModelVoice(kokoro, null, null), SpeechScriptArguments.speak(json("""{"model":"$kokoro"}"""), "123 ...").selection)
        val tagged = SpeechScriptArguments.speak(json("""{"model":"$kokoro","language":"en_US","pitch":1.2}"""), "\u4f60\u597d")
        assertEquals(TtsSelection.ModelVoice(kokoro, null, "en-US"), tagged.selection)
        assertEquals(setOf("pitch"), tagged.ttsOptions.keySet())
        val named = SpeechScriptArguments.speak(json("""{"model":"tts.vits.icefall.zh-aishell3","speaker":"SSB0005"}"""), "hi").selection
        assertEquals("SSB0005", (named as TtsSelection.ModelVoice).speaker)
        refused { SpeechScriptArguments.speak(json("""{"model":"$kokoro","locale":"not a tag"}"""), "hi") }
    }

    @Test
    fun textLanguageFollowsTheScripts() {
        assertEquals("zh", SpeechScriptArguments.textLanguage("\u4f60\u597d, AutoJs6"))
        assertEquals("en", SpeechScriptArguments.textLanguage("Hello, world"))
        assertEquals("ja", SpeechScriptArguments.textLanguage("\u4eca\u65e5\u306f"))
        assertEquals("ko", SpeechScriptArguments.textLanguage("\uc548\ub155"))
        assertNull(SpeechScriptArguments.textLanguage("42!"))
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

    /** 3-Macaw Speech roadmap D132 - D134: `speech.speaker` arguments. */
    @Test
    fun speakerEnrollTakesOnePathOrAnArrayAndAppendsByDefault() {
        val one = SpeechScriptArguments.enroll(json("\" Alice \""), json("\"a.wav\""), null)
        assertEquals(SpeechEnrollRequest("Alice"), one.request)
        assertEquals(listOf("a.wav"), one.paths)
        assertFalse(one.request.replace)

        val many = SpeechScriptArguments.enroll(json("\"Bob\""), json("[\"1.wav\", \"2.m4a\"]"), json("{\"replace\": true, \"model\": \"speaker.x\"}"))
        assertEquals(SpeechEnrollRequest("Bob", replace = true, model = "speaker.x"), many.request)
        assertEquals(listOf("1.wav", "2.m4a"), many.paths)

        val ten = (1..SpeechLimits.MAX_ENROLL_FILES).joinToString(",", "[", "]") { "\"$it.wav\"" }
        assertEquals(10, SpeechScriptArguments.enroll(json("\"a\""), json(ten), null).paths.size)
        val eleven = (1..SpeechLimits.MAX_ENROLL_FILES + 1).joinToString(",", "[", "]") { "\"$it.wav\"" }
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.enroll(json("\"a\""), json(eleven), null) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), json("[]"), null) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), json("[1]"), null) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), null, null) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), json("\"content://x\""), null) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), json("\"a.wav\""), json("{\"append\": true}")) }
        refused { SpeechScriptArguments.enroll(json("\"a\""), json("\"a.wav\""), json("{\"replace\": 1}")) }
    }

    @Test
    fun speakerNamesAreTrimmedBoundedAndPrintable() {
        assertEquals("Alice Smith", SpeechScriptArguments.speakerName(json("\"  Alice Smith \""), "name"))
        assertEquals("\u5f20\u4e09", SpeechScriptArguments.speakerName(json("\"\u5f20\u4e09\""), "name"))
        assertEquals(64, SpeechScriptArguments.speakerName(json("\"" + "x".repeat(64) + "\""), "name").length)
        refused(SpeechErrorCodes.LIMIT_EXCEEDED) { SpeechScriptArguments.speakerName(json("\"" + "x".repeat(65) + "\""), "name") }
        refused { SpeechScriptArguments.speakerName(json("\"   \""), "name") }
        refused { SpeechScriptArguments.speakerName(json("\"a\\nb\""), "name") }
        refused { SpeechScriptArguments.speakerName(json("42"), "name") }
        refused { SpeechScriptArguments.speakerName(null, "name") }
    }

    @Test
    fun speakerVerifyAndIdentifyCheckThresholdAndTopK() {
        assertEquals(SpeechVerifyRequest("a"), SpeechScriptArguments.verify(json("\"a\""), null))
        assertEquals(SpeechVerifyRequest("a", 0.5), SpeechScriptArguments.verify(json("\"a\""), json("{\"threshold\": 0.5}")))
        assertEquals(SpeechVerifyRequest("a", 0.0), SpeechScriptArguments.verify(json("\"a\""), json("{\"threshold\": 0}")))
        refused { SpeechScriptArguments.verify(json("\"a\""), json("{\"threshold\": 1.5}")) }
        refused { SpeechScriptArguments.verify(json("\"a\""), json("{\"threshold\": -0.1}")) }
        refused { SpeechScriptArguments.verify(json("\"a\""), json("{\"topK\": 1}")) }

        assertEquals(SpeechIdentifyRequest(), SpeechScriptArguments.identify(null))
        assertEquals(SpeechLimits.DEFAULT_IDENTIFY_CANDIDATES, SpeechScriptArguments.identify(json("{}")).topK)
        assertEquals(SpeechIdentifyRequest(0.4, 10), SpeechScriptArguments.identify(json("{\"threshold\": 0.4, \"topK\": 10}")))
        refused { SpeechScriptArguments.identify(json("{\"topK\": 0}")) }
        refused { SpeechScriptArguments.identify(json("{\"topK\": 11}")) }
        refused { SpeechScriptArguments.identify(json("{\"topK\": 2.5}")) }
        refused { SpeechScriptArguments.identify(json("{\"name\": \"a\"}")) }
    }

    /** 3-Macaw Speech roadmap D147 - D149: `speech.diarize` / `detectLanguage` / `tag` options. */
    @Test
    fun analysisOptionsTakeTheirDefaultsAndRanges() {
        assertEquals(SpeechDiarizeRequest(), SpeechScriptArguments.diarize(null))
        assertEquals(
            SpeechDiarizeRequest("segment.x", "speaker.y", 3, 0.85, identify = true),
            SpeechScriptArguments.diarize(json("""{"model":"segment.x","speakerModel":"speaker.y","speakers":3,"threshold":0.85,"identify":true}""")),
        )
        assertEquals(0, SpeechScriptArguments.diarize(json("""{"speakers":0}""")).speakers)
        assertEquals(SpeechLimits.MAX_DIARIZATION_SPEAKERS, SpeechScriptArguments.diarize(json("""{"speakers":20}""")).speakers)
        refused { SpeechScriptArguments.diarize(json("""{"speakers":21}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"speakers":-1}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"speakers":1.5}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"threshold":2.5}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"identify":"yes"}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"onProgress":1}""")) }
        refused { SpeechScriptArguments.diarize(json("""{"topK":1}""")) }

        assertEquals(SpeechLanguageRequest(), SpeechScriptArguments.detectLanguage(null))
        assertEquals(SpeechLimits.DEFAULT_LANGUAGE_WINDOWS, SpeechScriptArguments.detectLanguage(json("{}")).windows)
        assertEquals(SpeechLanguageRequest("langid.x", 60), SpeechScriptArguments.detectLanguage(json("""{"model":"langid.x","windows":60}""")))
        refused { SpeechScriptArguments.detectLanguage(json("""{"windows":0}""")) }
        refused { SpeechScriptArguments.detectLanguage(json("""{"windows":61}""")) }
        refused { SpeechScriptArguments.detectLanguage(json("""{"language":"en"}""")) }

        assertEquals(SpeechTagRequest(), SpeechScriptArguments.tag(null))
        assertEquals(SpeechTagRequest("tagger.x", 20, timeline = true), SpeechScriptArguments.tag(json("""{"model":"tagger.x","topK":20,"timeline":true}""")))
        assertEquals(SpeechLimits.DEFAULT_TAG_TOP_K, SpeechScriptArguments.tag(json("""{"onProgress":null}""")).topK)
        refused { SpeechScriptArguments.tag(json("""{"topK":0}""")) }
        refused { SpeechScriptArguments.tag(json("""{"topK":21}""")) }
        refused { SpeechScriptArguments.tag(json("""{"timeline":1}""")) }
        refused { SpeechScriptArguments.tag(json("""{"windows":3}""")) }
    }
}
