package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolRoundTripTest {

    @Test
    fun openAiNativeAssistantMessageAndPublicToolCallIdsRoundTrip() {
        val response = AiResponseParser.parse(
            "openai",
            jsonObject(
                """
                {
                  "choices":[{
                    "message":{
                      "role":"assistant",
                      "content":null,
                      "tool_calls":[
                        {
                          "id":"call-weather",
                          "type":"function",
                          "function":{"name":"weather","arguments":"{\"city\":\"Paris\"}"}
                        },
                        {
                          "id":"call-time",
                          "type":"function",
                          "function":{"name":"time","arguments":"{\"zone\":\"UTC\"}"}
                        }
                      ]
                    },
                    "finish_reason":"tool_calls"
                  }]
                }
                """,
            ),
        )
        val messages = JsonArray().apply {
            add(jsonObject("""{"role":"user","content":"Weather and time?"}"""))
            add(requireNotNull(response.message).deepCopy())
            add(jsonObject("""{"role":"tool","toolCallId":"call-weather","content":{"temperature":21}}"""))
            add(jsonObject("""{"role":"tool","tool_call_id":"call-time","content":"12:00"}"""))
        }

        val body = requestBody("openai", messages)
        val providerMessages = body.getAsJsonArray("messages")
        val assistant = providerMessages[1].asJsonObject

        assertEquals(2, assistant.getAsJsonArray("tool_calls").size())
        assertEquals("call-weather", assistant.getAsJsonArray("tool_calls")[0].asJsonObject.get("id").asString)
        assertEquals("call-weather", providerMessages[2].asJsonObject.get("tool_call_id").asString)
        assertEquals("""{"temperature":21}""", providerMessages[2].asJsonObject.get("content").asString)
        assertEquals("call-time", providerMessages[3].asJsonObject.get("tool_call_id").asString)
    }

    @Test
    fun anthropicPreservesOpaqueAssistantBlocksAndGroupsParallelToolResults() {
        val response = AiResponseParser.parse(
            "anthropic",
            jsonObject(
                """
                {
                  "content":[
                    {
                      "type":"thinking",
                      "thinking":"Need two lookups",
                      "signature":"anthropic-signature"
                    },
                    {"type":"redacted_thinking","data":"opaque-redacted-data"},
                    {"type":"tool_use","id":"tool-a","name":"weather","input":{"city":"Paris"}},
                    {"type":"tool_use","id":"tool-b","name":"time","input":{"zone":"UTC"}}
                  ],
                  "stop_reason":"tool_use"
                }
                """,
            ),
        )
        val messages = JsonArray().apply {
            add(jsonObject("""{"role":"user","content":"Weather and time?"}"""))
            add(requireNotNull(response.message).deepCopy())
            add(jsonObject("""{"role":"tool","toolCallId":"tool-a","content":{"temperature":21}}"""))
            add(
                jsonObject(
                    """
                    {
                      "role":"tool",
                      "toolUseId":"tool-b",
                      "content":[{"type":"text","text":"12:00"}]
                    }
                    """,
                ),
            )
        }

        val providerMessages = requestBody("anthropic", messages).getAsJsonArray("messages")
        val assistantContent = providerMessages[1].asJsonObject.getAsJsonArray("content")
        val resultContent = providerMessages[2].asJsonObject.getAsJsonArray("content")

        assertEquals("anthropic-signature", assistantContent[0].asJsonObject.get("signature").asString)
        assertEquals("opaque-redacted-data", assistantContent[1].asJsonObject.get("data").asString)
        assertEquals(2, resultContent.size())
        assertEquals("tool-a", resultContent[0].asJsonObject.get("tool_use_id").asString)
        assertEquals("""{"temperature":21}""", resultContent[0].asJsonObject.get("content").asString)
        assertEquals("tool-b", resultContent[1].asJsonObject.get("tool_use_id").asString)
    }

    @Test
    fun geminiPreservesThoughtSignatureAndUsesUserTurnForParallelResults() {
        val response = AiResponseParser.parse(
            "gemini",
            jsonObject(
                """
                {
                  "candidates":[{
                    "content":{
                      "role":"model",
                      "parts":[
                        {
                          "functionCall":{"id":"call-a","name":"weather","args":{"city":"Paris"}},
                          "thoughtSignature":"gemini-signature"
                        },
                        {
                          "functionCall":{"id":"call-b","name":"time","args":{"zone":"UTC"}}
                        }
                      ]
                    },
                    "finishReason":"STOP"
                  }]
                }
                """,
            ),
        )
        val messages = JsonArray().apply {
            add(jsonObject("""{"role":"user","content":"Weather and time?"}"""))
            add(requireNotNull(response.message).deepCopy())
            add(jsonObject("""{"role":"tool","toolCallId":"call-a","content":{"temperature":21}}"""))
            add(jsonObject("""{"role":"tool","toolCallId":"call-b","content":{"time":"12:00"}}"""))
        }

        val contents = requestBody("gemini", messages).getAsJsonArray("contents")
        val assistantParts = contents[1].asJsonObject.getAsJsonArray("parts")
        val functionTurn = contents[2].asJsonObject
        val functionParts = functionTurn.getAsJsonArray("parts")

        assertEquals("gemini-signature", assistantParts[0].asJsonObject.get("thoughtSignature").asString)
        assertEquals("user", functionTurn.get("role").asString)
        assertEquals(2, functionParts.size())
        assertEquals(
            "call-a",
            functionParts[0].asJsonObject.getAsJsonObject("functionResponse").get("id").asString,
        )
        assertEquals(
            21,
            functionParts[0]
                .asJsonObject
                .getAsJsonObject("functionResponse")
                .getAsJsonObject("response")
                .get("temperature")
                .asInt,
        )
    }

    @Test
    fun flattenedAssistantToolCallsMapAcrossAllProtocols() {
        val flattenedAssistant = jsonObject(
            """
            {
              "role":"assistant",
              "content":"",
              "toolCalls":[
                {
                  "id":"call-1",
                  "name":"lookup",
                  "arguments":"{\"id\":7}",
                  "raw":{
                    "functionCall":{"id":"call-1","name":"lookup","args":{"id":7}},
                    "thoughtSignature":"signature-from-flat-call"
                  }
                }
              ]
            }
            """,
        )
        val messages = JsonArray().apply {
            add(jsonObject("""{"role":"user","content":"Lookup"}"""))
            add(flattenedAssistant)
        }

        val openAiAssistant = requestBody("openai", messages)
            .getAsJsonArray("messages")[1]
            .asJsonObject
        assertEquals(
            "lookup",
            openAiAssistant
                .getAsJsonArray("tool_calls")[0]
                .asJsonObject
                .getAsJsonObject("function")
                .get("name")
                .asString,
        )

        val anthropicAssistant = requestBody("anthropic", messages)
            .getAsJsonArray("messages")[1]
            .asJsonObject
        assertEquals(
            7,
            anthropicAssistant
                .getAsJsonArray("content")[0]
                .asJsonObject
                .getAsJsonObject("input")
                .get("id")
                .asInt,
        )

        val geminiAssistant = requestBody("gemini", messages)
            .getAsJsonArray("contents")[1]
            .asJsonObject
        assertEquals(
            "signature-from-flat-call",
            geminiAssistant.getAsJsonArray("parts")[0].asJsonObject.get("thoughtSignature").asString,
        )
        assertTrue(geminiAssistant.getAsJsonArray("parts")[0].asJsonObject.has("functionCall"))
    }

    private fun requestBody(providerId: String, messages: JsonArray): JsonObject {
        val baseUrl = when (providerId) {
            "anthropic" -> "https://anthropic.test/v1"
            "gemini" -> "https://gemini.test/v1beta"
            else -> "https://openai.test/v1"
        }
        val request = AiRequestFactory.build(
            AiProviderConfig(providerId, baseUrl, "test-model", "test-key"),
            messages,
        )
        return request.jsonBody()
    }

    private fun Request.jsonBody(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }

    private fun jsonObject(value: String): JsonObject {
        return JsonParser.parseString(value.trimIndent()).asJsonObject
    }
}
