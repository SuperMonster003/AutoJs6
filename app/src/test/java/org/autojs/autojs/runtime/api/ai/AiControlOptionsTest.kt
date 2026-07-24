package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.ai.AiProviderSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiControlOptionsTest {

    @Test
    fun consumesSupportedAliasesAndLeavesProviderOptions() {
        val options = JsonParser.parseString(
            """
            {
              "profileId":"Work",
              "provider_id":"openai",
              "baseURL":"https://example.test/v1",
              "api_key":"secret",
              "timeoutMs":1500,
              "stream":true,
              "temperature":0.25
            }
            """,
        ).asJsonObject

        val control = AiControlOptions.takeFrom(options)

        assertEquals("Work", control.profile)
        assertEquals("openai", control.providerId)
        assertEquals("https://example.test/v1", control.baseUrl)
        assertEquals("secret", control.apiKey)
        assertEquals(1500L, control.timeoutMillis)
        assertFalse(options.has("profileId"))
        assertFalse(options.has("baseURL"))
        assertFalse(options.has("timeoutMs"))
        assertFalse(options.has("stream"))
        assertEquals(0.25, options.get("temperature").asDouble, 0.0)
    }

    @Test
    fun timeoutMustBeAnExactIntegerInRange() {
        listOf("1.5", "1e100", "999", "600001").forEach { timeout ->
            val options = JsonParser.parseString("""{"timeout":$timeout}""").asJsonObject
            assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(options)
            }
        }

        val scientificInteger = JsonParser.parseString("""{"timeout":1e3}""").asJsonObject
        assertEquals(1000L, AiControlOptions.takeFrom(scientificInteger).timeoutMillis)
    }

    @Test
    fun profileSelectorRecognizesCanonicalUuidAndNames() {
        val id = "1f81d3cb-9fe2-4e32-85a0-e0a6fc436620"
        assertEquals(
            AiProviderSelector.Id(id),
            AiControlOptions(id, null, null, null, null, null).profileSelector(),
        )
        assertEquals(
            AiProviderSelector.Name("Personal"),
            AiControlOptions("Personal", null, null, null, null, null).profileSelector(),
        )
    }

    @Test
    fun stringRepresentationRedactsExplicitApiKeyAndMatchingMetadata() {
        val apiKey = "private-secret-1234"
        val control = AiControlOptions(
            profile = "$apiKey-profile",
            providerId = "openai",
            baseUrl = "https://example.test/$apiKey",
            model = "model-$apiKey",
            apiKey = apiKey,
            timeoutMillis = 1_000L,
        )

        val rendered = control.toString()

        assertTrue(apiKey !in rendered)
        assertTrue("1234" !in rendered)
        assertTrue("[REDACTED]" in rendered)
    }

    @Test
    fun loopbackOpenAiCompatibleConfigCanBeStandaloneWithoutApiKey() {
        assertTrue(
            AiControlOptions(
                profile = null,
                providerId = "openai-compatible",
                baseUrl = "http://localhost:11434/v1",
                model = "local-model",
                apiKey = null,
                timeoutMillis = null,
            ).canUseStandaloneConfig(),
        )
        assertFalse(
            AiControlOptions(
                profile = null,
                providerId = "openai-compatible",
                baseUrl = "https://models.example.com/v1",
                model = "remote-model",
                apiKey = null,
                timeoutMillis = null,
            ).canUseStandaloneConfig(),
        )
    }
}
