package org.autojs.autojs.runtime.api.tts

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsOptionsTest {

    @Test
    fun parsesAndNormalizesCompleteOptions() {
        val parsed = TtsOptionsParser.merge(
            TtsOptions(),
            json(
                """
                {
                  "engine": " engine.package ",
                  "language": "zh_CN",
                  "speechRate": 1.25,
                  "pitch": 0.8,
                  "volume": 0.7,
                  "pan": -0.2,
                  "queue": "append",
                  "split": false,
                  "offline": true,
                  "emitAudio": true,
                  "audioUsage": "assistant",
                  "timeoutMs": 5000,
                  "params": {
                    "engine.package:style": "news",
                    "engine.package:enabled": true,
                    "engine.package:weight": 2
                  },
                  "overwrite": true,
                  "mkdirs": false
                }
                """,
            ),
            includeFileOptions = true,
        )

        assertEquals("engine.package", parsed.speech.engine)
        assertEquals("zh-CN", parsed.speech.locale)
        assertNull(parsed.speech.voice)
        assertEquals(1.25F, parsed.speech.rate)
        assertEquals(0.8F, parsed.speech.pitch)
        assertEquals(0.7F, parsed.speech.volume)
        assertEquals(-0.2F, parsed.speech.pan)
        assertEquals(TtsQueueMode.ADD, parsed.speech.queueMode)
        assertFalse(parsed.speech.autoSplit)
        assertTrue(parsed.speech.offlineOnly)
        assertTrue(parsed.speech.audioEvents)
        assertEquals("assistant", parsed.speech.usage)
        assertEquals(5_000L, parsed.speech.timeoutMillis)
        assertEquals("news", parsed.speech.parameters["engine.package:style"])
        assertEquals(true, parsed.speech.parameters["engine.package:enabled"])
        assertEquals(2.0, parsed.speech.parameters["engine.package:weight"])
        assertTrue(parsed.file.overwrite)
        assertFalse(parsed.file.createDirectories)
    }

    @Test
    fun perCallVoiceAndLocaleReplaceOppositeDefaults() {
        val localeDefault = TtsOptions(locale = "en-US")
        val withVoice = TtsOptionsParser.merge(
            localeDefault,
            json("""{"voice":"voice-a"}"""),
        ).speech
        assertNull(withVoice.locale)
        assertEquals("voice-a", withVoice.voice)

        val voiceDefault = TtsOptions(voice = "voice-a")
        val withLocale = TtsOptionsParser.merge(
            voiceDefault,
            json("""{"locale":"fr_FR"}"""),
        ).speech
        assertEquals("fr-FR", withLocale.locale)
        assertNull(withLocale.voice)
    }

    @Test
    fun forcedQueueModeOverridesOptionsAndDefaults() {
        val parsed = TtsOptionsParser.merge(
            TtsOptions(queueMode = TtsQueueMode.FLUSH),
            json("""{"queue":"flush"}"""),
            forcedQueueMode = TtsQueueMode.ADD,
        )

        assertEquals(TtsQueueMode.ADD, parsed.speech.queueMode)
    }

    @Test
    fun zeroTimeoutExplicitlyDisablesAutomaticTimeout() {
        val parsed = TtsOptionsParser.merge(
            TtsOptions(timeoutMillis = 9_000L),
            json("""{"timeout":0}"""),
        )

        assertEquals(0L, parsed.speech.timeoutMillis)
    }

    @Test
    fun automaticTimeoutRoundTripsThroughPublicDefaults() {
        val automatic = TtsOptionsParser.merge(
            TtsOptions(timeoutMillis = 9_000L),
            json("""{"timeout":"auto"}"""),
        ).speech
        val fromNull = TtsOptionsParser.merge(
            TtsOptions(timeoutMillis = 9_000L),
            json("""{"timeout":null}"""),
        ).speech
        val roundTripped = TtsOptionsParser.merge(
            TtsOptions(),
            json("""{"timeout":"${automatic.toPublicValue()["timeout"]}"}"""),
        ).speech

        assertNull(automatic.timeoutMillis)
        assertNull(fromNull.timeoutMillis)
        assertNull(roundTripped.timeoutMillis)
    }

    @Test
    fun fileOptionsAreAcceptedOnlyForSynthesis() {
        assertThrows(IllegalArgumentException::class.java) {
            TtsOptionsParser.merge(
                TtsOptions(),
                json("""{"overwrite":true}"""),
            )
        }
        val parsed = TtsOptionsParser.merge(
            TtsOptions(),
            json("""{"overwrite":true,"mkdirs":false}"""),
            includeFileOptions = true,
        )

        assertTrue(parsed.file.overwrite)
        assertFalse(parsed.file.createDirectories)
    }

    @Test
    fun synthesisDefaultsToAtomicReplacementButSupportsStrictNoReplace() {
        val defaults = TtsOptionsParser.merge(
            TtsOptions(),
            json("{}"),
            includeFileOptions = true,
        )
        val strict = TtsOptionsParser.merge(
            TtsOptions(),
            json("""{"overwrite":false}"""),
            includeFileOptions = true,
        )

        assertTrue(defaults.file.overwrite)
        assertFalse(strict.file.overwrite)
    }

    @Test
    fun rejectsConflictingLocaleVoiceAndInvalidRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            TtsOptionsParser.merge(
                TtsOptions(),
                json("""{"locale":"en-US","voice":"voice-a"}"""),
            )
        }
        listOf(
            """{"rate":0}""",
            """{"pitch":5}""",
            """{"volume":1.1}""",
            """{"pan":-1.1}""",
            """{"timeout":999}""",
            """{"usage":"unknown"}""",
            """{"queue":"later"}""",
            """{"locale":"und"}""",
        ).forEach { source ->
            assertThrows(source, IllegalArgumentException::class.java) {
                TtsOptionsParser.merge(TtsOptions(), json(source))
            }
        }
    }

    @Test
    fun rejectsNestedNonFiniteOrExcessiveParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            TtsOptionsParser.merge(
                TtsOptions(),
                json("""{"params":{"nested":{"bad":true}}}"""),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsOptionsParser.merge(
                TtsOptions(),
                json("""{"params":{"long":"${"x".repeat(4_097)}"}}"""),
            )
        }
        listOf("volume", "pan", "utteranceId", "streamType", "sessionId").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) {
                TtsOptionsParser.merge(
                    TtsOptions(),
                    json("""{"params":{"$name":"reserved"}}"""),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsOptionsParser.merge(
                TtsOptions(),
                json("""{"engine":"engine\u0000package"}"""),
            )
        }
    }

    @Test
    fun normalizesAndValidatesEngineNamesForEveryEntryPoint() {
        assertEquals("engine.package", normalizeTtsEngineName(" engine.package "))
        assertNull(normalizeTtsEngineName(""))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeTtsEngineName("x".repeat(TtsOptionsParser.MAX_ENGINE_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeTtsEngineName("engine\u0000package")
        }
    }

    private fun json(source: String) = JsonParser.parseString(source).asJsonObject
}
