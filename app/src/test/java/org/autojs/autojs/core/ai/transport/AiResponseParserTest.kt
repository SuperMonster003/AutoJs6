package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {

    @Test
    fun normalizesOpenAiChatResponse() {
        val response = AiResponseParser.parse(
            "openai",
            json(
                """
                {
                  "choices":[{
                    "message":{
                      "content":[{"type":"text","text":"Hello "},{"type":"output_text","text":"world"}],
                      "reasoning_content":"reason",
                      "tool_calls":[{
                        "id":"call-1",
                        "function":{"name":"weather","arguments":"{\"city\":\"Paris\"}"}
                      }]
                    },
                    "finish_reason":"tool_calls"
                  }],
                  "usage":{
                    "prompt_tokens":10,
                    "completion_tokens":4,
                    "total_tokens":14,
                    "completion_tokens_details":{"reasoning_tokens":2},
                    "prompt_tokens_details":{"cached_tokens":3}
                  }
                }
                """,
            ),
        )

        assertEquals("Hello world", response.text)
        assertEquals("reason", response.reasoning)
        assertEquals("tool_calls", response.finishReason)
        assertEquals(1, response.toolCalls.size)
        assertEquals("call-1", response.toolCalls[0].id)
        assertEquals("weather", response.toolCalls[0].name)
        assertEquals("""{"city":"Paris"}""", response.toolCalls[0].arguments)
        assertEquals("assistant", response.message?.get("role")?.asString)
        assertEquals(
            "call-1",
            response.message
                ?.getAsJsonArray("tool_calls")
                ?.get(0)
                ?.asJsonObject
                ?.get("id")
                ?.asString,
        )
        assertEquals(10L, response.usage?.inputTokens)
        assertEquals(4L, response.usage?.outputTokens)
        assertEquals(14L, response.usage?.totalTokens)
        assertEquals(2L, response.usage?.reasoningTokens)
        assertEquals(3L, response.usage?.cachedInputTokens)
    }

    @Test
    fun normalizesOpenAiResponsesApiShape() {
        val response = AiResponseParser.parse(
            "openrouter",
            json(
                """
                {
                  "status":"completed",
                  "output":[
                    {"type":"reasoning","summary":[{"text":"Think"}]},
                    {"type":"message","content":[{"type":"output_text","text":"Answer"}]},
                    {
                      "type":"function_call",
                      "call_id":"call-2",
                      "name":"lookup",
                      "arguments":"{\"id\":7}"
                    }
                  ],
                  "usage":{"input_tokens":5,"output_tokens":3}
                }
                """,
            ),
        )

        assertEquals("Answer", response.text)
        assertEquals("Think", response.reasoning)
        assertEquals("completed", response.finishReason)
        assertEquals("lookup", response.toolCalls.single().name)
        assertEquals("""{"id":7}""", response.toolCalls.single().arguments)
        assertEquals(8L, response.usage?.totalTokens)
    }

    @Test
    fun normalizesOnlyFirstOpenAiChoice() {
        val response = AiResponseParser.parse(
            "openai",
            json(
                """
                {
                  "choices":[
                    {
                      "message":{"role":"assistant","content":"First"},
                      "finish_reason":"stop"
                    },
                    {
                      "message":{
                        "role":"assistant",
                        "content":"Second",
                        "tool_calls":[{
                          "id":"ignored-call",
                          "function":{"name":"ignored","arguments":"{}"}
                        }]
                      },
                      "finish_reason":"tool_calls"
                    }
                  ]
                }
                """,
            ),
        )

        assertEquals("First", response.text)
        assertEquals("First", response.message?.get("content")?.asString)
        assertEquals("stop", response.finishReason)
        assertTrue(response.toolCalls.isEmpty())
        assertEquals(2, response.raw.asJsonObject.getAsJsonArray("choices").size())
    }

    @Test
    fun normalizesOpenAiRefusalFromMessageAndContentPart() {
        val standalone = AiResponseParser.parse(
            "openai",
            json(
                """
                {
                  "choices":[{
                    "message":{"role":"assistant","content":null,"refusal":"Cannot comply"},
                    "finish_reason":"stop"
                  }]
                }
                """,
            ),
        )
        val contentPart = AiResponseParser.parse(
            "openai",
            json(
                """
                {
                  "choices":[{
                    "message":{
                      "role":"assistant",
                      "content":[{"type":"refusal","refusal":"Request refused"}]
                    },
                    "finish_reason":"stop"
                  }]
                }
                """,
            ),
        )
        val responsesApi = AiResponseParser.parse(
            "openai",
            json(
                """
                {
                  "status":"completed",
                  "output":[{
                    "type":"message",
                    "content":[{"type":"refusal","refusal":"Responses refusal"}]
                  }]
                }
                """,
            ),
        )

        assertEquals("Cannot comply", standalone.text)
        assertEquals("Cannot comply", standalone.message?.get("refusal")?.asString)
        assertEquals("Request refused", contentPart.text)
        assertEquals(
            "Request refused",
            contentPart.message
                ?.getAsJsonArray("content")
                ?.get(0)
                ?.asJsonObject
                ?.get("refusal")
                ?.asString,
        )
        assertEquals("Responses refusal", responsesApi.text)
        assertEquals("Responses refusal", responsesApi.message?.get("content")?.asString)
    }

    @Test
    fun normalizesAnthropicMessagesResponse() {
        val response = AiResponseParser.parse(
            "anthropic",
            json(
                """
                {
                  "content":[
                    {"type":"thinking","thinking":"Reason"},
                    {"type":"text","text":"Answer"},
                    {"type":"tool_use","id":"tool-1","name":"lookup","input":{"id":7}}
                  ],
                  "stop_reason":"tool_use",
                  "usage":{
                    "input_tokens":6,
                    "output_tokens":4,
                    "cache_read_input_tokens":2,
                    "cache_creation_input_tokens":1
                  }
                }
                """,
            ),
        )

        assertEquals("Answer", response.text)
        assertEquals("Reason", response.reasoning)
        assertEquals("tool_use", response.finishReason)
        assertEquals("tool-1", response.toolCalls.single().id)
        assertEquals("""{"id":7}""", response.toolCalls.single().arguments)
        assertEquals("assistant", response.message?.get("role")?.asString)
        assertEquals(
            "tool-1",
            response.message
                ?.getAsJsonArray("content")
                ?.get(2)
                ?.asJsonObject
                ?.get("id")
                ?.asString,
        )
        assertEquals(10L, response.usage?.totalTokens)
        assertEquals(3L, response.usage?.cachedInputTokens)
    }

    @Test
    fun normalizesGeminiGenerateContentResponse() {
        val response = AiResponseParser.parse(
            "gemini",
            json(
                """
                {
                  "candidates":[{
                    "content":{"parts":[
                      {"text":"Reason","thought":true},
                      {"text":"Answer"},
                      {"functionCall":{"name":"lookup","args":{"id":7}}}
                    ]},
                    "finishReason":"STOP"
                  }],
                  "usageMetadata":{
                    "promptTokenCount":6,
                    "candidatesTokenCount":4,
                    "totalTokenCount":10,
                    "cachedContentTokenCount":2
                  }
                }
                """,
            ),
        )

        assertEquals("Answer", response.text)
        assertEquals("Reason", response.reasoning)
        assertEquals("STOP", response.finishReason)
        assertEquals("lookup", response.toolCalls.single().name)
        assertEquals("""{"id":7}""", response.toolCalls.single().arguments)
        assertEquals("model", response.message?.get("role")?.asString)
        assertEquals(
            "lookup",
            response.message
                ?.getAsJsonArray("parts")
                ?.get(2)
                ?.asJsonObject
                ?.getAsJsonObject("functionCall")
                ?.get("name")
                ?.asString,
        )
        assertEquals(10L, response.usage?.totalTokens)
        assertEquals(2L, response.usage?.cachedInputTokens)
    }

    @Test
    fun normalizesOnlyFirstGeminiCandidate() {
        val response = AiResponseParser.parse(
            "gemini",
            json(
                """
                {
                  "candidates":[
                    {
                      "content":{
                        "role":"model",
                        "parts":[
                          {"text":"First"},
                          {"functionCall":{"id":"call-first","name":"firstTool","args":{"id":1}}}
                        ]
                      },
                      "finishReason":"STOP"
                    },
                    {
                      "content":{
                        "role":"model",
                        "parts":[
                          {"text":"Second"},
                          {"functionCall":{"id":"call-second","name":"secondTool","args":{"id":2}}}
                        ]
                      },
                      "finishReason":"MAX_TOKENS"
                    }
                  ],
                  "usageMetadata":{
                    "promptTokenCount":5,
                    "candidatesTokenCount":7,
                    "totalTokenCount":12
                  }
                }
                """,
            ),
        )

        assertEquals("First", response.text)
        assertEquals("STOP", response.finishReason)
        assertEquals(1, response.toolCalls.size)
        assertEquals("call-first", response.toolCalls.single().id)
        assertEquals("firstTool", response.toolCalls.single().name)
        assertEquals(2, response.message?.getAsJsonArray("parts")?.size())
        assertEquals(
            "First",
            response.message
                ?.getAsJsonArray("parts")
                ?.first()
                ?.asJsonObject
                ?.get("text")
                ?.asString,
        )
        assertEquals(12L, response.usage?.totalTokens)
    }

    @Test
    fun preservesStructuredProviderError() {
        val response = AiResponseParser.parse(
            "deepseek",
            json("""{"error":{"type":"invalid_request","code":"bad_model","message":"No model"}}"""),
        )

        assertNotNull(response.error)
        assertEquals("invalid_request", response.error?.type)
        assertEquals("bad_model", response.error?.code)
        assertEquals("No model", response.error?.message)
    }

    @Test
    fun rejectsMissingProviderResponseShapesWithoutEchoingPayload() {
        val secret = "provider-response-secret"
        val malformedResponses = listOf(
            "openai" to """{"usage":{},"private":"$secret"}""",
            "anthropic" to """{"stop_reason":"end_turn","private":"$secret"}""",
            "gemini" to """{"private":"$secret"}""",
        )

        malformedResponses.forEach { (providerId, body) ->
            val exception = assertThrows(AiResponseProtocolException::class.java) {
                AiResponseParser.parse(providerId, json(body))
            }
            assertFalse(exception.message.orEmpty().contains(secret))
            assertFalse(exception.stackTraceToString().contains(secret))
        }

        val blocked = assertThrows(AiResponseProtocolException::class.java) {
            AiResponseParser.parse(
                "gemini",
                json("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
            )
        }
        assertTrue(blocked.message.orEmpty().contains("SAFETY"))
    }

    private fun json(value: String): JsonElement = JsonParser.parseString(value)

}
