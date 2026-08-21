package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent
import org.autojs.autojs.core.plugin.ai.toGenerationOptions
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPluginAskInputMapperTest {

    @Test
    fun exactPluginAskMapsOrderedSystemAndUserMessagesWithoutLeakingContent() {
        val systemPrompt = "Answer as a concise local assistant."
        val userPrompt = "first line\nsecond line"
        val input = input(
            messages = """[
                {"role":"system","content":"$systemPrompt"},
                {"role":"user","content":"first line\nsecond line"}
            ]""",
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
        assertEquals(
            listOf(OnDeviceAiPluginMessageRole.SYSTEM, OnDeviceAiPluginMessageRole.USER),
            request.messages.map { it.role },
        )
        assertEquals(listOf(systemPrompt, userPrompt), request.messages.map { it.content })
        val expectedInputBytes = (systemPrompt + userPrompt).toByteArray(Charsets.UTF_8).size.toLong()
        assertEquals(expectedInputBytes, request.inputBytes)
        assertEquals(2_500L, request.timeoutMillis)
        val rendered = request.toString()
        assertTrue("messageCount=2" in rendered)
        assertTrue("inputBytes=$expectedInputBytes" in rendered)
        assertFalse(systemPrompt in rendered)
        assertFalse(userPrompt in rendered)
        assertFalse("first line" in rendered)
        assertFalse("second line" in rendered)
    }

    @Test
    fun pluginAskPreservesAssistantHistoryBeforeTheFinalUserMessage() {
        val request = AiPluginAskInputMapper.map(
            input(
                """[
                    {"role":"SYSTEM","content":"system"},
                    {"role":"user","content":"question one"},
                    {"role":"assistant","content":"answer one"},
                    {"role":"user","content":"question two"}
                ]""",
            ),
            defaultTimeoutMillis = 9_000L,
        )

        assertEquals(
            listOf(
                OnDeviceAiPluginMessageRole.SYSTEM,
                OnDeviceAiPluginMessageRole.USER,
                OnDeviceAiPluginMessageRole.ASSISTANT,
                OnDeviceAiPluginMessageRole.USER,
            ),
            request.messages.map { it.role },
        )
        assertEquals(
            listOf("system", "question one", "answer one", "question two"),
            request.messages.map { it.content },
        )
    }

    @Test
    fun pluginAskRejectsInvalidMessageSequencesAndShapes() {
        data class Case(val messages: String, val expectedMessage: String)

        val cases = listOf(
            Case("[]", "AI plugin ask message count is invalid"),
            Case("[42]", "AI plugin messages must be objects"),
            Case(
                """[{"role":"assistant","content":"answer"}]""",
                "The final on-device AI plugin message must have the user role",
            ),
            Case(
                """[
                    {"role":"tool","content":"result"},
                    {"role":"user","content":"prompt"}
                ]""",
                "AI plugin message role is unsupported",
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
        assertTrue(protocolOptions.reportUsage)

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

    @Test
    fun pluginAskMapsNativeStructuredJsonSchemaAndProvidesAnObjectDefault() {
        val schemaRequest = AiPluginAskInputMapper.map(
            input(
                messages = """[{"role":"user","content":"Return the requested object"}]""",
                optionSuffix = """,
                    "responseSchema": {
                      "type": "object",
                      "properties": {"answer": {"type": "string"}},
                      "required": ["answer"]
                    }
                """.trimIndent(),
            ),
            defaultTimeoutMillis = 9_000L,
        )

        assertTrue(schemaRequest.structuredJson)
        assertEquals(
            """{"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}""",
            schemaRequest.responseSchemaJson,
        )
        val schemaOptions = schemaRequest.toGenerationOptions(
            stream = true,
            maximumOutputBytes = 4_096,
            timeoutMillis = 8_000,
            requiredCapabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING),
        )
        assertTrue(schemaOptions.structuredJson)
        assertEquals(OnDeviceAiMimeType.JSON, schemaOptions.responseMimeType)
        assertEquals(
            setOf(OnDeviceAiCapabilityId.STREAMING, OnDeviceAiCapabilityId.STRUCTURED_JSON),
            schemaOptions.requiredCapabilityIds.toSet(),
        )
        assertEquals(
            schemaRequest.responseSchemaJson,
            checkNotNull(schemaOptions.responseSchema?.inlineBytes).toString(Charsets.UTF_8),
        )

        val defaultRequest = AiPluginAskInputMapper.map(
            input(
                messages = """[{"role":"user","content":"Return an object"}]""",
                optionSuffix = ", \"structuredJson\": true",
            ),
            defaultTimeoutMillis = 9_000L,
        )
        assertEquals("""{"type":"object"}""", defaultRequest.responseSchemaJson)
    }

    @Test
    fun pluginAskRejectsInvalidStructuredJsonOptionCombinations() {
        listOf(
            "\"structuredJson\": \"yes\"",
            "\"responseSchema\": []",
            "\"structuredJson\": false, \"responseSchema\": {\"type\": \"object\"}",
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
