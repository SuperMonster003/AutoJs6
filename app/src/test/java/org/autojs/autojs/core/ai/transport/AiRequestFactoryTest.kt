package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestFactoryTest {

    @Test
    fun openAiCompatibleAliasesUseBearerHeaderAndChatCompletionsEndpoint() {
        listOf("openai", "DEEPSEEK", " openrouter ", "openai-compatible").forEach { providerId ->
            val request = AiRequestFactory.build(
                config = config(providerId, "https://example.test/v1"),
                messages = messages(),
                options = jsonObject("""{"temperature":0.25,"apiKey":"must-not-leak","model":"ignored"}"""),
            )
            val body = request.jsonBody()

            assertEquals("https://example.test/v1/chat/completions", request.url.toString())
            assertEquals("Bearer $API_KEY", request.header("Authorization"))
            assertNull(request.header("x-api-key"))
            assertNull(request.header("x-goog-api-key"))
            assertEquals(MODEL, body.get("model").asString)
            assertEquals(false, body.get("stream").asBoolean)
            assertEquals(0.25, body.get("temperature").asDouble, 0.0)
            assertFalse(body.has("apiKey"))
            assertSecretOnlyInHeader(request, body)
        }
    }

    @Test
    fun existingOpenAiEndpointIsNotDuplicated() {
        val request = AiRequestFactory.build(
            config = config("openai", "https://example.test/v1/chat/completions"),
            messages = messages(),
        )

        assertEquals("https://example.test/v1/chat/completions", request.url.toString())
    }

    @Test
    fun credentialBearingJsonBodyIsOneShotToPreventAutomaticReplay() {
        listOf("openai", "anthropic", "gemini").forEach { providerId ->
            val request = AiRequestFactory.build(
                config = config(providerId, "https://example.test/v1"),
                messages = messages(),
            )

            assertTrue(
                "$providerId request body must not be replayable",
                requireNotNull(request.body).isOneShot(),
            )
        }
    }

    @Test
    fun anthropicExtractsSystemMessagesAndUsesApiKeyHeader() {
        val input = jsonArray(
            """
            [
              {"role":"system","content":"Be concise"},
              {"role":"developer","content":[{"type":"text","text":"Use metric"}]},
              {"role":"user","content":"Weather?"},
              {"role":"assistant","content":"Calling a tool"},
              {"role":"tool","tool_call_id":"tool-1","content":{"temperature":21}}
            ]
            """,
        )
        val request = AiRequestFactory.build(
            config = config("anthropic", "https://api.anthropic.test/v1"),
            messages = input,
            options = jsonObject("""{"temperature":0.1}"""),
            stream = true,
        )
        val body = request.jsonBody()

        assertEquals("https://api.anthropic.test/v1/messages", request.url.toString())
        assertEquals(API_KEY, request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        assertNull(request.header("Authorization"))
        assertEquals("text/event-stream", request.header("Accept"))
        assertEquals(MODEL, body.get("model").asString)
        assertEquals(true, body.get("stream").asBoolean)
        assertEquals(1024, body.get("max_tokens").asInt)
        assertEquals(2, body.getAsJsonArray("system").size())
        assertEquals("Be concise", body.getAsJsonArray("system")[0].asJsonObject.get("text").asString)
        assertEquals(3, body.getAsJsonArray("messages").size())
        val toolMessage = body.getAsJsonArray("messages")[2].asJsonObject
        assertEquals("user", toolMessage.get("role").asString)
        assertEquals(
            "tool-1",
            toolMessage.getAsJsonArray("content")[0].asJsonObject.get("tool_use_id").asString,
        )
        assertEquals(
            """{"temperature":21}""",
            toolMessage.getAsJsonArray("content")[0].asJsonObject.get("content").asString,
        )
        assertSecretOnlyInHeader(request, body)
    }

    @Test
    fun geminiMapsRolesSystemInstructionAndStreamingEndpoint() {
        val input = jsonArray(
            """
            [
              {"role":"system","content":"System rule"},
              {"role":"user","content":[{"type":"text","text":"Hello"}]},
              {"role":"assistant","content":"Hi"},
              {"role":"tool","name":"weather","content":{"temperature":21}}
            ]
            """,
        )
        val request = AiRequestFactory.build(
            config = config("gemini", "https://generativelanguage.test/v1beta"),
            messages = input,
            options = jsonObject("""{"generationConfig":{"temperature":0.5}}"""),
            stream = true,
        )
        val body = request.jsonBody()

        assertEquals(
            "https://generativelanguage.test/v1beta/models/test-model:streamGenerateContent?alt=sse",
            request.url.toString(),
        )
        assertEquals(API_KEY, request.header("x-goog-api-key"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("x-api-key"))
        assertEquals(
            "System rule",
            body.getAsJsonObject("systemInstruction")
                .getAsJsonArray("parts")[0]
                .asJsonObject
                .get("text")
                .asString,
        )
        val contents = body.getAsJsonArray("contents")
        assertEquals("user", contents[0].asJsonObject.get("role").asString)
        assertEquals("model", contents[1].asJsonObject.get("role").asString)
        assertEquals("user", contents[2].asJsonObject.get("role").asString)
        assertEquals(
            "weather",
            contents[2].asJsonObject
                .getAsJsonArray("parts")[0]
                .asJsonObject
                .getAsJsonObject("functionResponse")
                .get("name")
                .asString,
        )
        assertSecretOnlyInHeader(request, body)
    }

    @Test
    fun neutralSystemOptionAppliesAcrossAllProtocols() {
        val options = jsonObject("""{"system":"Shared instruction"}""")

        val openAi = AiRequestFactory.build(
            config("openai", "https://openai.test/v1"),
            messages(),
            options,
        ).jsonBody()
        val openAiSystem = openAi.getAsJsonArray("messages")[0].asJsonObject
        assertEquals("system", openAiSystem.get("role").asString)
        assertEquals("Shared instruction", openAiSystem.get("content").asString)
        assertFalse(openAi.has("system"))

        val anthropic = AiRequestFactory.build(
            config("anthropic", "https://anthropic.test/v1"),
            messages(),
            options,
        ).jsonBody()
        assertEquals(
            "Shared instruction",
            anthropic.getAsJsonArray("system")[0].asJsonObject.get("text").asString,
        )

        val gemini = AiRequestFactory.build(
            config("gemini", "https://gemini.test/v1beta"),
            messages(),
            options,
        ).jsonBody()
        assertEquals(
            "Shared instruction",
            gemini.getAsJsonObject("systemInstruction")
                .getAsJsonArray("parts")[0]
                .asJsonObject
                .get("text")
                .asString,
        )
        assertFalse(gemini.has("system"))
        assertTrue(options.has("system"))
    }

    @Test
    fun mapsNeutralGenerationAndToolOptionsWithoutSendingFacadeControls() {
        val options = jsonObject(
            """
            {
              "maxTokens":64,
              "temperature":0.2,
              "topP":0.8,
              "topK":40,
              "stopSequences":["END","STOP"],
              "tools":[{
                "name":"lookup",
                "description":"Look up a value",
                "parameters":{
                  "type":"object",
                  "properties":{"id":{"type":"integer"}},
                  "required":["id"]
                }
              }],
              "toolChoice":{"name":"lookup"},
              "apiKey":"$API_KEY",
              "baseUrl":"https://credential-sink.test",
              "profileId":"private-profile",
              "signal":"private-signal",
              "timeoutMillis":1234,
              "timeout_millis":5678,
              "headers":{"Authorization":"Bearer $API_KEY"},
              "onChunk":"private-callback",
              "returnRaw":true
            }
            """,
        )

        val openAi = AiRequestFactory.build(
            config("openai", "https://openai.test/v1"),
            messages(),
            options,
        ).jsonBody()
        assertEquals(64, openAi.get("max_tokens").asInt)
        assertEquals(0.2, openAi.get("temperature").asDouble, 0.0)
        assertEquals(0.8, openAi.get("top_p").asDouble, 0.0)
        assertFalse(openAi.has("top_k"))
        assertEquals("END", openAi.getAsJsonArray("stop")[0].asString)
        val openAiTool = openAi.getAsJsonArray("tools")[0].asJsonObject
        assertEquals("function", openAiTool.get("type").asString)
        assertEquals("lookup", openAiTool.getAsJsonObject("function").get("name").asString)
        assertEquals(
            "object",
            openAiTool.getAsJsonObject("function")
                .getAsJsonObject("parameters")
                .get("type")
                .asString,
        )
        assertEquals(
            "lookup",
            openAi.getAsJsonObject("tool_choice")
                .getAsJsonObject("function")
                .get("name")
                .asString,
        )
        assertNoFacadeControls(openAi)

        val openRouter = AiRequestFactory.build(
            config("openrouter", "https://openrouter.test/api/v1"),
            messages(),
            options,
        ).jsonBody()
        assertEquals(40, openRouter.get("top_k").asInt)

        val anthropic = AiRequestFactory.build(
            config("anthropic", "https://anthropic.test/v1"),
            messages(),
            options,
        ).jsonBody()
        assertEquals(64, anthropic.get("max_tokens").asInt)
        assertEquals(0.2, anthropic.get("temperature").asDouble, 0.0)
        assertEquals(0.8, anthropic.get("top_p").asDouble, 0.0)
        assertEquals(40, anthropic.get("top_k").asInt)
        assertEquals("END", anthropic.getAsJsonArray("stop_sequences")[0].asString)
        val anthropicTool = anthropic.getAsJsonArray("tools")[0].asJsonObject
        assertEquals("lookup", anthropicTool.get("name").asString)
        assertEquals("object", anthropicTool.getAsJsonObject("input_schema").get("type").asString)
        assertEquals("tool", anthropic.getAsJsonObject("tool_choice").get("type").asString)
        assertEquals("lookup", anthropic.getAsJsonObject("tool_choice").get("name").asString)
        assertNoFacadeControls(anthropic)

        val gemini = AiRequestFactory.build(
            config("gemini", "https://gemini.test/v1beta"),
            messages(),
            options,
        ).jsonBody()
        val generationConfig = gemini.getAsJsonObject("generationConfig")
        assertEquals(64, generationConfig.get("maxOutputTokens").asInt)
        assertEquals(0.2, generationConfig.get("temperature").asDouble, 0.0)
        assertEquals(0.8, generationConfig.get("topP").asDouble, 0.0)
        assertEquals(40, generationConfig.get("topK").asInt)
        assertEquals("END", generationConfig.getAsJsonArray("stopSequences")[0].asString)
        val declaration = gemini.getAsJsonArray("tools")[0]
            .asJsonObject
            .getAsJsonArray("functionDeclarations")[0]
            .asJsonObject
        assertEquals("lookup", declaration.get("name").asString)
        assertEquals("object", declaration.getAsJsonObject("parameters").get("type").asString)
        val functionCalling = gemini.getAsJsonObject("toolConfig")
            .getAsJsonObject("functionCallingConfig")
        assertEquals("ANY", functionCalling.get("mode").asString)
        assertEquals("lookup", functionCalling.getAsJsonArray("allowedFunctionNames")[0].asString)
        assertNoFacadeControls(gemini)

        assertTrue(options.has("maxTokens"))
        assertTrue(options.has("apiKey"))
    }

    @Test
    fun nativeProviderOptionWinsOverNeutralAlias() {
        val openAi = AiRequestFactory.build(
            config("openai", "https://openai.test/v1"),
            messages(),
            jsonObject("""{"maxTokens":64,"max_tokens":32,"topP":0.8,"top_p":0.4}"""),
        ).jsonBody()
        assertEquals(32, openAi.get("max_tokens").asInt)
        assertEquals(0.4, openAi.get("top_p").asDouble, 0.0)
        assertFalse(openAi.has("maxTokens"))
        assertFalse(openAi.has("topP"))

        val gemini = AiRequestFactory.build(
            config("gemini", "https://gemini.test/v1beta"),
            messages(),
            jsonObject(
                """
                {
                  "maxTokens":64,
                  "temperature":0.8,
                  "generationConfig":{"maxOutputTokens":32,"temperature":0.4}
                }
                """,
            ),
        ).jsonBody().getAsJsonObject("generationConfig")
        assertEquals(32, gemini.get("maxOutputTokens").asInt)
        assertEquals(0.4, gemini.get("temperature").asDouble, 0.0)
    }

    @Test
    fun onlyLoopbackOpenAiCompatibleRequestsMayOmitAuthentication() {
        listOf(
            "http://localhost:11434/v1",
            "https://127.0.0.1:1234/v1",
            "http://[::1]:11434/v1",
        ).forEach { baseUrl ->
            val request = AiRequestFactory.build(
                AiProviderConfig("openai-compatible", baseUrl, MODEL, null),
                messages(),
            )
            assertNull(request.header("Authorization"))
        }

        listOf(
            AiProviderConfig(
                "openai-compatible",
                "https://models.example.test/v1",
                MODEL,
                null,
            ),
            AiProviderConfig("openai", "http://127.0.0.1:11434/v1", MODEL, null),
            AiProviderConfig(
                "openai-compatible",
                "http://192.168.1.20:11434/v1",
                MODEL,
                null,
            ),
        ).forEach { config ->
            assertThrows(IllegalArgumentException::class.java) {
                AiRequestFactory.build(config, messages())
            }
        }
    }

    @Test
    fun requestValidationAndConfigRenderingNeverExposeApiKey() {
        val config = AiProviderConfig(
            providerId = API_KEY,
            baseUrl = "https://example.test/$API_KEY",
            model = API_KEY,
            apiKey = API_KEY,
        )

        assertFalse(config.toString().contains(API_KEY))
        val unsupported = assertThrows(IllegalArgumentException::class.java) {
            AiRequestFactory.build(config, messages())
        }
        assertFalse(unsupported.message.orEmpty().contains(API_KEY))

        val invalidHeaderKey = "prefix-\u00E9-secret"
        val invalid = assertThrows(IllegalArgumentException::class.java) {
            AiRequestFactory.build(
                AiProviderConfig("openai", "https://example.test/v1", MODEL, invalidHeaderKey),
                messages(),
            )
        }
        assertFalse(invalid.message.orEmpty().contains(invalidHeaderKey))
    }

    private fun assertSecretOnlyInHeader(request: Request, body: JsonObject) {
        assertFalse(request.url.toString().contains(API_KEY))
        assertFalse(body.toString().contains(API_KEY))
    }

    private fun assertNoFacadeControls(body: JsonObject) {
        listOf(
            "apiKey",
            "baseUrl",
            "profileId",
            "signal",
            "timeoutMillis",
            "timeout_millis",
            "headers",
            "onChunk",
            "returnRaw",
            "maxTokens",
            "topP",
            "topK",
            "stopSequences",
            "toolChoice",
        ).forEach { key ->
            assertFalse("Unexpected request body key: $key", body.has(key))
        }
        assertFalse(body.toString().contains(API_KEY))
        assertFalse(body.toString().contains("private-"))
    }

    private fun config(providerId: String, baseUrl: String): AiProviderConfig {
        return AiProviderConfig(providerId, baseUrl, MODEL, API_KEY)
    }

    private fun messages(): JsonArray {
        return jsonArray("""[{"role":"user","content":"Hello"}]""")
    }

    private fun Request.jsonBody(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }

    private fun jsonArray(value: String): JsonArray = JsonParser.parseString(value).asJsonArray

    private fun jsonObject(value: String): JsonObject = JsonParser.parseString(value).asJsonObject

    companion object {
        private const val API_KEY = "sk-test-super-secret"
        private const val MODEL = "test-model"
    }

}
