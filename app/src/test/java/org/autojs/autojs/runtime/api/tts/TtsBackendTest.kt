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
