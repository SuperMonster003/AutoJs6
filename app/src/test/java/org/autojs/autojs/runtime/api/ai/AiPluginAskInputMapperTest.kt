package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.toGenerationOptions
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
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
            AiProviderComponent(
                packageName = "org.example.provider",
                className = "org.example.provider.AiProviderService",
            ),
            request.component,
        )
        assertEquals("example.provider", request.providerId)
        assertEquals("local:example-model", request.targetId)
        assertEquals(
            listOf(AiPluginMessageRole.SYSTEM, AiPluginMessageRole.USER),
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
                AiPluginMessageRole.SYSTEM,
                AiPluginMessageRole.USER,
                AiPluginMessageRole.ASSISTANT,
                AiPluginMessageRole.USER,
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
                "The final AI provider plugin message must have the user role",
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
                    "maxTokens": 512,
                    "backend": "GPU",
                    "reasoning": true
                """.trimIndent(),
            ),
            defaultTimeoutMillis = 9_000L,
        )

        assertEquals(0.75, request.temperature!!, 0.0)
        assertEquals(32, request.topK)
        assertEquals(0.9, request.topP!!, 0.0)
        assertEquals(512L, request.maximumOutputTokens)
        assertEquals(AiProviderBackendProfile.GPU, request.backendProfile)
        assertTrue(request.includeReasoning)
        val protocolOptions = request.toGenerationOptions(
            stream = true,
            maximumOutputBytes = 4_096,
            timeoutMillis = 8_000,
            requiredCapabilityIds = listOf(AiProviderCapabilityId.STREAMING),
        )
        assertEquals(0.75, protocolOptions.temperature!!, 0.0)
        assertEquals(32, protocolOptions.topK)
        assertEquals(0.9, protocolOptions.topP!!, 0.0)
        assertEquals(512L, protocolOptions.maximumOutputTokens)
        assertEquals(AiProviderBackendProfile.GPU, protocolOptions.backendProfile)
        assertEquals(
            listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.REASONING),
            protocolOptions.requiredCapabilityIds,
        )
        assertTrue(protocolOptions.includeReasoning)
        assertTrue(protocolOptions.reportUsage)

        val defaults = AiPluginAskInputMapper.map(input("""[{"role":"user","content":"prompt"}]"""), 9_000L)
        assertNull(defaults.temperature)
        assertNull(defaults.topK)
        assertNull(defaults.topP)
        assertNull(defaults.maximumOutputTokens)
        assertNull(defaults.backendProfile)
        assertFalse(defaults.includeReasoning)
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
            "\"backend\": 7",
            "\"backend\": \"dsp\"",
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
            requiredCapabilityIds = listOf(AiProviderCapabilityId.STREAMING),
        )
        assertTrue(schemaOptions.structuredJson)
        assertEquals(AiProviderMimeType.JSON, schemaOptions.responseMimeType)
        assertEquals(
            setOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.STRUCTURED_JSON),
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
                  "className": "org.example.provider.AiProviderService"
                },
                "providerId": "example.provider"
              },
              "target": "local:example-model"
              $optionSuffix
            }
            """,
        ).asJsonObject,
    )
}
