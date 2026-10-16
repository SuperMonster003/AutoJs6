package org.autojs.autojs.runtime.api.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TtsBackendTest {

    @Test
    fun normalizesSelectionLanguageAsBcp47() {
        assertEquals(
            "zh-Hans-CN",
            TtsSelection.Language.from(" zh-hans-cn ").languageTag,
        )
        assertThrows(IllegalArgumentException::class.java) {
            TtsSelection.Language.from("zh_CN")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsSelection.Language.from("und")
        }
    }

    @Test
    fun derivesMutuallyExclusiveSelectionsFromOptions() {
        assertEquals(
            TtsSelection.Default,
            TtsConfiguration.from(TtsOptions()).selection,
        )
        assertEquals(
            TtsSelection.Language.from("en-US"),
            TtsConfiguration.from(TtsOptions(locale = "en-US")).selection,
        )
        assertEquals(
            TtsSelection.Voice.from("local.voice"),
            TtsConfiguration.from(TtsOptions(voice = "local.voice")).selection,
        )
        assertThrows(IllegalArgumentException::class.java) {
            TtsConfiguration.from(
                TtsOptions(locale = "en-US", voice = "local.voice"),
            )
        }
    }

    @Test
    fun acceptsOnlyBoundedScalarRequestParameters() {
        val parameters = TtsRequestParameters.from(
            TtsOptions(
                volume = 0.5F,
                pan = -0.25F,
                parameters = linkedMapOf(
                    "engine.text" to "value",
                    "engine.flag" to true,
                    "engine.number" to 1.25,
                ),
            ),
        )

        assertEquals(0.5F, parameters.volume)
        assertEquals(-0.25F, parameters.pan)
        assertTrue(parameters.engineParameters["engine.text"] is TtsScalarValue.Text)
        assertTrue(
            parameters.engineParameters["engine.flag"] is TtsScalarValue.BooleanValue,
        )
        assertTrue(
            parameters.engineParameters["engine.number"] is TtsScalarValue.DoubleValue,
        )
        assertThrows(IllegalArgumentException::class.java) {
            TtsScalarValue.from(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsScalarValue.from(BigDecimal.ONE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsRequestParameters(volume = 1.01F)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsRequestParameters(pan = -1.01F)
        }
    }

    @Test
    fun engineParametersRequireTheExactActiveEnginePrefix() {
        validateTtsEngineParameterPrefixes(
            listOf(
                "engine.package:style",
                "engine.package_enabled",
            ),
            "engine.package",
        )
        listOf(
            "style",
            "other.engine:style",
            "engine.package:",
            "engine.package_",
        ).forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) {
                validateTtsEngineParameterPrefixes(listOf(name), "engine.package")
            }
        }
    }

    @Test
    fun choosesInstalledOfflineVoiceDeterministically() {
        val exactOffline = voice(
            name = "exact-offline",
            languageTag = "en-US",
            quality = 300,
            latency = 300,
        )
        val languageOnlyHigherQuality = voice(
            name = "language-only",
            languageTag = "en-GB",
            quality = 500,
            latency = 100,
        )
        val networkVoice = voice(
            name = "network",
            languageTag = "en-US",
            quality = 500,
            latency = 100,
            networkRequired = true,
        )
        val notInstalled = voice(
            name = "not-installed",
            languageTag = "en-US",
            quality = 500,
            latency = 100,
            notInstalled = true,
        )

        assertEquals(
            exactOffline,
            selectOfflineTtsVoice(
                listOf(
                    languageOnlyHigherQuality,
                    networkVoice,
                    exactOffline,
                    notInstalled,
                ),
                preferredLanguageTag = "en-US",
            ),
        )
        assertEquals(
            languageOnlyHigherQuality,
            selectOfflineTtsVoice(
                listOf(exactOffline, languageOnlyHigherQuality),
                preferredLanguageTag = "en-US",
                preferredVoiceName = "language-only",
            ),
        )
        assertFalse(networkVoice.isOffline)
        assertFalse(notInstalled.isOffline)
    }

    @Test
    fun neverFallsBackToAnUnrelatedOfflineLanguage() {
        assertNull(
            selectOfflineTtsVoice(
                voices = listOf(
                    voice(
                        name = "french",
                        languageTag = "fr-FR",
                        quality = 500,
                        latency = 100,
                    ),
                ),
                preferredLanguageTag = "en-US",
            ),
        )
    }

    @Test
    fun explicitScriptNeverFallsBackToAnotherWritingSystem() {
        val traditional = voice(
            name = "traditional",
            languageTag = "zh-Hant-TW",
            quality = 300,
            latency = 300,
        )
        val simplified = voice(
            name = "simplified",
            languageTag = "zh-Hans-CN",
            quality = 500,
            latency = 100,
        )

        assertEquals(
            traditional,
            selectOfflineTtsVoice(
                voices = listOf(simplified, traditional),
                preferredLanguageTag = "zh-Hant",
            ),
        )
        assertTrue(ttsLocaleMatches("zh-Hant-TW", "zh-Hant"))
        assertFalse(ttsLocaleMatches("zh-Hans-CN", "zh-Hant"))
        assertTrue(ttsLocaleMatches("zh-Hant-TW", "zh"))
    }

    @Test
    fun modelVoiceReplacesVoiceAndLocale() {
        val modelVoice = TtsSelection.ModelVoice("tts.kokoro", null, "zh")
        assertEquals(modelVoice, TtsConfiguration.from(TtsOptions(speechSelection = modelVoice)).selection)
        assertThrows(IllegalArgumentException::class.java) {
            TtsConfiguration.from(TtsOptions(voice = "tts.kokoro/3", speechSelection = modelVoice))
        }
        assertEquals(
            TtsSelection.PreferredLanguage("zh"),
            TtsConfiguration.from(TtsOptions(speechSelection = TtsSelection.PreferredLanguage("zh"))).selection,
        )
        assertThrows(IllegalArgumentException::class.java) { TtsSelection.PreferredLanguage("zh_CN") }
        assertThrows(IllegalArgumentException::class.java) { TtsSelection.ModelVoice("tts.kokoro", "a/b") }
        assertThrows(IllegalArgumentException::class.java) { TtsSelection.ModelVoice("tts.kokoro", null, "zh_CN") }
    }

    @Test
    fun modelVoiceFollowsTheSpeakerOrTheLanguage() {
        // Kokoro multi-lang v1.1 as 3-Macaw Speech lists it: 0 - 2 English first, 3 and above Chinese first.
        val kokoro = listOf(
            voice("tts.kokoro/0", "en-US", 400, 300),
            voice("tts.kokoro/0/zh-CN", "zh-CN", 400, 300),
            voice("tts.kokoro/10", "zh-CN", 400, 300),
            voice("tts.kokoro/2", "en-US", 400, 300),
            voice("tts.kokoro/3", "zh-CN", 400, 300),
            voice("tts.kokoro/3/en-US", "en-US", 400, 300),
            voice("tts.kokoro.other/1", "zh-CN", 400, 300),
        )
        fun listed(speaker: String?, language: String?): String {
            val choice = resolveTtsModelVoice(kokoro, TtsSelection.ModelVoice("tts.kokoro", speaker, language))
            return (choice as TtsModelVoiceChoice.Listed).voice.name
        }
        assertEquals("tts.kokoro/3", listed(null, "zh"))
        assertEquals("tts.kokoro/3", listed(null, "zh-TW"))
        assertEquals("tts.kokoro/0", listed(null, "en"))
        assertEquals("tts.kokoro/0", listed(null, "ja"))
        assertEquals("tts.kokoro/0", listed(null, null))
        assertEquals("tts.kokoro/10", listed("10", "en"))
        assertEquals("tts.kokoro/2", listed("02", null))

        val named = resolveTtsModelVoice(kokoro, TtsSelection.ModelVoice("tts.kokoro", "SSB0005"))
        assertEquals(TtsModelVoiceChoice.Named("tts.kokoro/SSB0005", kokoro[0]), named)

        val missingNumber = assertThrows(TtsBackendException::class.java) {
            resolveTtsModelVoice(kokoro, TtsSelection.ModelVoice("tts.kokoro", "11"))
        }
        assertTrue(missingNumber.message!!.contains("speakers 0 to 10"))
        assertThrows(TtsBackendException::class.java) {
            resolveTtsModelVoice(kokoro, TtsSelection.ModelVoice("tts.missing", null, "zh"))
        }
    }

    private fun voice(
        name: String,
        languageTag: String,
        quality: Int,
        latency: Int,
        networkRequired: Boolean = false,
        notInstalled: Boolean = false,
    ): TtsVoice = TtsVoice(
        name = name,
        languageTag = languageTag,
        quality = quality,
        latency = latency,
        networkRequired = networkRequired,
        notInstalled = notInstalled,
        features = emptySet(),
    )
}
