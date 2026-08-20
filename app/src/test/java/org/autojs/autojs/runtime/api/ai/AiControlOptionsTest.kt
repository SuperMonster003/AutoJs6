package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.ai.AiProviderSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertNull(control.pluginSelection)
        assertFalse(options.has("profileId"))
        assertFalse(options.has("baseURL"))
        assertFalse(options.has("timeoutMs"))
        assertFalse(options.has("stream"))
        assertEquals(0.25, options.get("temperature").asDouble, 0.0)
    }

    @Test
    fun exactPluginSelectionIsConsumedWithoutCreatingCloudControls() {
        val options = pluginOptions(", \"timeout\": 1500")

        val control = AiControlOptions.takeFrom(options)

        assertEquals(
            AiPluginSelection(
                packageName = "org.example.provider",
                className = "org.example.provider.OnDeviceAiService",
                providerId = "example.provider",
                modelId = "example-model",
            ),
            control.pluginSelection,
        )
        assertEquals(1500L, control.timeoutMillis)
        assertNull(control.profile)
        assertNull(control.providerId)
        assertNull(control.baseUrl)
        assertNull(control.model)
        assertNull(control.apiKey)
        assertTrue(options.entrySet().isEmpty())
    }

    @Test
    fun malformedPluginSelectionIsRejectedWithItsExactBoundary() {
        data class Case(val pluginJson: String, val expectedMessage: String)

        val cases = listOf(
            Case("null", "AI plugin selection must be an object"),
            Case("[]", "AI plugin selection must be an object"),
            Case("false", "AI plugin selection must not be false"),
            Case(
                """{"component":{},"modelId":"example-model"}""",
                "AI plugin providerId is required",
            ),
            Case(
                """{"component":{},"providerId":1,"modelId":"example-model"}""",
                "AI plugin providerId must be a string",
            ),
            Case(
                """{"component":{},"providerId":"example.provider","modelId":" "}""",
                "AI plugin modelId must not be blank",
            ),
            Case(
                """{"component":{},"providerId":"bad/id","modelId":"example-model"}""",
                "AI plugin providerId is invalid",
            ),
            Case(
                """{"component":[],"providerId":"example.provider","modelId":"example-model"}""",
                "AI plugin component must be an object",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider"},"providerId":"example.provider","modelId":"example-model"}""",
                "AI plugin component className is required",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider","className":"org.example.\u0001Service"},"providerId":"example.provider","modelId":"example-model"}""",
                "AI plugin component className contains control characters",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider","className":"Service","extra":true},"providerId":"example.provider","modelId":"example-model"}""",
                "AI plugin component contains unsupported fields",
            ),
            Case(
                """{"component":{},"providerId":"example.provider","modelId":"example-model","extra":true}""",
                "AI plugin selection contains unsupported fields",
            ),
        )

        cases.forEach { case ->
            val options = JsonParser.parseString("""{"plugin":${case.pluginJson}}""").asJsonObject
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(options)
            }
            assertEquals(case.expectedMessage, error.message)
        }
    }

    @Test
    fun pluginShorthandSelectsOfficialOnDevicePlugin() {
        val officialPackage = "io.github.supermonster003.autojs6.plugin.ondeviceai"
        val officialClass = "$officialPackage.provider.OnDeviceAiProviderService"
        val officialProvider = "autojs6.on-device-ai"

        val boolean = AiControlOptions.takeFrom(
            JsonParser.parseString("""{"plugin":true}""").asJsonObject,
        )
        assertEquals(
            AiPluginSelection(officialPackage, officialClass, officialProvider, modelId = null),
            boolean.pluginSelection,
        )

        val withModel = AiControlOptions.takeFrom(
            JsonParser.parseString("""{"plugin":{"modelId":"example-model"}}""").asJsonObject,
        )
        assertEquals(
            AiPluginSelection(officialPackage, officialClass, officialProvider, "example-model"),
            withModel.pluginSelection,
        )

        val explicitComponentWithoutModel = AiControlOptions.takeFrom(pluginOptionsWithoutModel())
        assertEquals(
            AiPluginSelection(
                packageName = "org.example.provider",
                className = "org.example.provider.OnDeviceAiService",
                providerId = "example.provider",
                modelId = null,
            ),
            explicitComponentWithoutModel.pluginSelection,
        )
    }

    @Test
    fun pluginSelectionRejectsCloudControlsAndUnsupportedOuterOptions() {
        val cloudControls = listOf(
            "\"profile\":\"Work\"",
            "\"profileId\":\"Work\"",
            "\"profile_id\":\"Work\"",
            "\"provider\":\"openai\"",
            "\"providerId\":\"openai\"",
            "\"provider_id\":\"openai\"",
            "\"baseUrl\":\"https://example.test/v1\"",
            "\"baseURL\":\"https://example.test/v1\"",
            "\"base_url\":\"https://example.test/v1\"",
            "\"model\":\"cloud-model\"",
            "\"apiKey\":\"secret\"",
            "\"api_key\":\"secret\"",
        )
        cloudControls.forEach { cloudControl ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(pluginOptions(", $cloudControl"))
            }
            assertEquals(
                "AI plugin selection cannot be combined with cloud provider controls",
                error.message,
            )
        }

        listOf("\"stream\":true", "\"temperature\":0.25").forEach { unsupported ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(pluginOptions(", $unsupported"))
            }
            assertEquals("AI plugin selection contains unsupported options", error.message)
        }
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

    private fun pluginOptions(extra: String = "") = JsonParser.parseString(
        """
        {
          "plugin": {
            "component": {
              "packageName": "org.example.provider",
              "className": "org.example.provider.OnDeviceAiService"
            },
            "providerId": "example.provider",
            "modelId": "example-model"
          }
          $extra
        }
        """,
    ).asJsonObject

    private fun pluginOptionsWithoutModel() = JsonParser.parseString(
        """
        {
          "plugin": {
            "component": {
              "packageName": "org.example.provider",
              "className": "org.example.provider.OnDeviceAiService"
            },
            "providerId": "example.provider"
          }
        }
        """,
    ).asJsonObject
}
