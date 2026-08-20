package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent
import org.autojs.autojs.core.plugin.ai.toGenerationOptions
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPluginAskInputMapperTest {

    @Test
    fun exactPluginAskMapsMultilinePromptPinsAndTimeoutWithoutLeakingPrompt() {
        val prompt = "first line\nsecond line"
        val input = input(
            messages = """[{"role":"user","content":"first line\nsecond line"}]""",
            optionSuffix = ", \"timeout\": 2500",
        )

        val request = AiPluginAskInputMapper.map(input, defaultTimeoutMillis = 9_000L)

        assertEquals(
            OnDeviceAiProviderComponent(
                packageName = "org.example.provider",
                className = "org.example.provider.OnDeviceAiService",
            ),
            request.component,
        )
        assertEquals("example.provider", request.providerId)
        assertEquals("example-model", request.modelId)
        assertEquals(prompt, request.prompt)
        assertEquals(2_500L, request.timeoutMillis)
        val rendered = request.toString()
        assertTrue("promptBytes=${prompt.toByteArray(Charsets.UTF_8).size}" in rendered)
        assertFalse(prompt in rendered)
        assertFalse("first line" in rendered)
        assertFalse("second line" in rendered)
    }

    @Test
    fun pluginAskRequiresExactlyOneUserTextMessage() {
        data class Case(val messages: String, val expectedMessage: String)

        val cases = listOf(
            Case("[]", "AI plugin ask requires exactly one user text message"),
            Case(
                """[
                    {"role":"user","content":"one"},
                    {"role":"user","content":"two"}
                ]""",
                "AI plugin ask requires exactly one user text message",
            ),
            Case("[42]", "AI plugin ask requires exactly one user text message"),
            Case(
                """[{"role":"assistant","content":"answer"}]""",
                "AI plugin ask requires exactly one user text message",
            ),
            Case(
                """[{"role":"user","content":42}]""",
                "AI plugin message content must be a string",
            ),
        )

        cases.forEach { case ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiPluginAskInputMapper.map(input(case.messages), defaultTimeoutMillis = 9_000L)
            }
            assertEquals(case.expectedMessage, error.message)
        }
    }

    @Test
    fun pluginAskRejectsUnknownMessageAndOptionFields() {
        val messageError = assertThrows(IllegalArgumentException::class.java) {
            AiPluginAskInputMapper.map(
                input("""[{"role":"user","content":"prompt","name":"unexpected"}]"""),
                defaultTimeoutMillis = 9_000L,
            )
        }
        assertEquals("AI plugin ask message contains unsupported fields", messageError.message)

        val optionError = assertThrows(IllegalArgumentException::class.java) {
            AiPluginAskInputMapper.map(
                input(
                    messages = """[{"role":"user","content":"prompt"}]""",
                    optionSuffix = ", \"seed\": 42",
                ),
                defaultTimeoutMillis = 9_000L,
            )
        }
        assertEquals("AI plugin selection contains unsupported options", optionError.message)
    }

    @Test
    fun pluginAskMapsSamplingAndTokenOptionsThroughTypedProtocolOptions() {
        val request = AiPluginAskInputMapper.map(
            input(
                messages = """[{"role":"user","content":"prompt"}]""",
                optionSuffix = """,
                    "temperature": 0.75,
                    "topK": 32,
                    "topP": 0.9,
                    "maxTokens": 512
                """.trimIndent(),
            ),
            defaultTimeoutMillis = 9_000L,
        )

        assertEquals(0.75, request.temperature!!, 0.0)
        assertEquals(32, request.topK)
        assertEquals(0.9, request.topP!!, 0.0)
        assertEquals(512L, request.maximumOutputTokens)
        val protocolOptions = request.toGenerationOptions(
            stream = true,
            maximumOutputBytes = 4_096,
            timeoutMillis = 8_000,
            requiredCapabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING),
        )
        assertEquals(0.75, protocolOptions.temperature!!, 0.0)
        assertEquals(32, protocolOptions.topK)
        assertEquals(0.9, protocolOptions.topP!!, 0.0)
        assertEquals(512L, protocolOptions.maximumOutputTokens)
        assertEquals(listOf(OnDeviceAiCapabilityId.STREAMING), protocolOptions.requiredCapabilityIds)

        val defaults = AiPluginAskInputMapper.map(input("""[{"role":"user","content":"prompt"}]"""), 9_000L)
        assertNull(defaults.temperature)
        assertNull(defaults.topK)
        assertNull(defaults.topP)
        assertNull(defaults.maximumOutputTokens)
    }

    @Test
    fun pluginAskRejectsMalformedOrOutOfRangeGenerationOptions() {
        listOf(
            "\"temperature\": \"warm\"",
            "\"temperature\": -0.1",
            "\"temperature\": 1e10000",
            "\"topK\": 0",
            "\"topK\": 1.5",
            "\"topP\": -0.1",
            "\"topP\": 1.1",
            "\"maxTokens\": 0",
            "\"maxTokens\": 1.5",
            "\"maxTokens\": 2147483648",
        ).forEach { option ->
            assertThrows(option, IllegalArgumentException::class.java) {
                AiPluginAskInputMapper.map(
                    input(
                        messages = """[{"role":"user","content":"prompt"}]""",
                        optionSuffix = ", $option",
                    ),
                    defaultTimeoutMillis = 9_000L,
                )
            }
        }
    }

    private fun input(
        messages: String,
        optionSuffix: String = "",
    ) = AiRequestInput(
        messages = JsonParser.parseString(messages).asJsonArray,
        options = JsonParser.parseString(
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
              $optionSuffix
            }
            """,
        ).asJsonObject,
    )
}
