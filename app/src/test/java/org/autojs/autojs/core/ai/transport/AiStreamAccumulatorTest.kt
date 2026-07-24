package org.autojs.autojs.core.ai.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStreamAccumulatorTest {

    @Test
    fun openAiBuildsReusableAssistantMessageWithParallelToolCalls() {
        val parser = AiStreamParser("openai")
        val accumulator = AiStreamAccumulator("openai")
        accumulator.append(
            parser.parse(
                event(
                    """
                    {
                      "choices":[{
                        "delta":{
                          "tool_calls":[
                            {
                              "index":0,
                              "id":"call-a",
                              "function":{"name":"weather","arguments":"{\"city\":"}
                            },
                            {
                              "index":1,
                              "id":"call-b",
                              "function":{"name":"time","arguments":"{\"zone\":"}
                            }
                          ],
                          "reasoning_details":[{"type":"encrypted","data":"opaque-reasoning"}]
                        }
                      }]
                    }
                    """,
                ),
            ),
        )
        accumulator.append(
            parser.parse(
                event(
                    """
                    {
                      "choices":[{
                        "delta":{
                          "tool_calls":[
                            {"index":0,"function":{"arguments":"\"Paris\"}"}},
                            {"index":1,"function":{"arguments":"\"UTC\"}"}}
                          ]
                        },
                        "finish_reason":"tool_calls"
                      }]
                    }
                    """,
                ),
            ),
        )
        accumulator.append(parser.parse(event("[DONE]")))

        val response = accumulator.toResponse()
        val message = requireNotNull(response.message)
        val calls = message.getAsJsonArray("tool_calls")

        assertEquals(2, response.toolCalls.size)
        assertEquals("""{"city":"Paris"}""", calls[0].asJsonObject.getAsJsonObject("function").get("arguments").asString)
        assertEquals("""{"zone":"UTC"}""", calls[1].asJsonObject.getAsJsonObject("function").get("arguments").asString)
        assertEquals(
            "opaque-reasoning",
            message.getAsJsonArray("reasoning_details")[0].asJsonObject.get("data").asString,
        )
    }

    @Test
    fun openAiResponsesKeepsCallIdAcrossItemDeltasAndBuildsOneReusableToolCall() {
        val parser = AiStreamParser("openai")
        val accumulator = AiStreamAccumulator("openai")
        listOf(
            """
            {
              "type":"response.output_item.added",
              "output_index":0,
              "item":{
                "type":"function_call",
                "id":"item-1",
                "call_id":"call-1",
                "name":"lookup",
                "arguments":""
              }
            }
            """,
            """
            {
              "type":"response.function_call_arguments.delta",
              "item_id":"item-1",
              "output_index":0,
              "delta":"{\"id\":"
            }
            """,
            """
            {
              "type":"response.function_call_arguments.done",
              "item_id":"item-1",
              "output_index":0,
              "name":"lookup",
              "arguments":"{\"id\":7}"
            }
            """,
            """
            {
              "type":"response.completed",
              "response":{"usage":{"input_tokens":3,"output_tokens":2}}
            }
            """,
        ).forEach { payload ->
            accumulator.append(parser.parse(event(payload)))
        }

        val response = accumulator.toResponse()
        val toolCall = response.toolCalls.single()
        val messageToolCall = requireNotNull(response.message)
            .getAsJsonArray("tool_calls")
            .single()
            .asJsonObject

        assertEquals("call-1", toolCall.id)
        assertEquals("lookup", toolCall.name)
        assertEquals("""{"id":7}""", toolCall.arguments)
        assertEquals("call-1", messageToolCall.get("id").asString)
        assertEquals(
            """{"id":7}""",
            messageToolCall.getAsJsonObject("function").get("arguments").asString,
        )
    }

    @Test
    fun anthropicPreservesThinkingRedactionSignatureAndParallelToolBlocks() {
        val parser = AiStreamParser("anthropic")
        val accumulator = AiStreamAccumulator("anthropic")
        listOf(
            """
            {
              "type":"message_start",
              "message":{
                "role":"assistant",
                "content":[],
                "usage":{"input_tokens":6,"output_tokens":1}
              }
            }
            """,
            """
            {
              "type":"content_block_start",
              "index":0,
              "content_block":{"type":"thinking","thinking":""}
            }
            """,
            """
            {
              "type":"content_block_delta",
              "index":0,
              "delta":{"type":"thinking_delta","thinking":"Need tools"}
            }
            """,
            """
            {
              "type":"content_block_delta",
              "index":0,
              "delta":{"type":"signature_delta","signature":"anthropic-stream-signature"}
            }
            """,
            """
            {
              "type":"content_block_start",
              "index":1,
              "content_block":{"type":"redacted_thinking","data":"opaque-redacted"}
            }
            """,
            """
            {
              "type":"content_block_start",
              "index":2,
              "content_block":{"type":"tool_use","id":"tool-a","name":"weather","input":{}}
            }
            """,
            """
            {
              "type":"content_block_delta",
              "index":2,
              "delta":{"type":"input_json_delta","partial_json":"{\"city\":\"Paris\"}"}
            }
            """,
            """{"type":"content_block_stop","index":2}""",
            """
            {
              "type":"content_block_start",
              "index":3,
              "content_block":{"type":"tool_use","id":"tool-b","name":"time","input":{}}
            }
            """,
            """
            {
              "type":"content_block_delta",
              "index":3,
              "delta":{"type":"input_json_delta","partial_json":"{\"zone\":\"UTC\"}"}
            }
            """,
            """{"type":"content_block_stop","index":3}""",
            """
            {
              "type":"message_delta",
              "delta":{"stop_reason":"tool_use"},
              "usage":{"output_tokens":4}
            }
            """,
            """{"type":"message_stop"}""",
        ).forEach { payload ->
            accumulator.append(parser.parse(event(payload)))
        }

        val response = accumulator.toResponse()
        val content = requireNotNull(response.message).getAsJsonArray("content")

        assertEquals(4, content.size())
        assertEquals("Need tools", content[0].asJsonObject.get("thinking").asString)
        assertEquals("anthropic-stream-signature", content[0].asJsonObject.get("signature").asString)
        assertEquals("opaque-redacted", content[1].asJsonObject.get("data").asString)
        assertEquals("Paris", content[2].asJsonObject.getAsJsonObject("input").get("city").asString)
        assertEquals("UTC", content[3].asJsonObject.getAsJsonObject("input").get("zone").asString)
        assertEquals(2, response.toolCalls.size)
        assertEquals("tool_use", response.finishReason)
        assertEquals(6L, response.usage?.inputTokens)
        assertEquals(4L, response.usage?.outputTokens)
        assertEquals(10L, response.usage?.totalTokens)
        assertEquals(6L, response.usage?.raw?.get("input_tokens")?.asLong)
        assertEquals(4L, response.usage?.raw?.get("output_tokens")?.asLong)
    }

    @Test
    fun geminiPreservesThoughtSignatureAndParallelFunctionCallParts() {
        val parser = AiStreamParser("gemini")
        val accumulator = AiStreamAccumulator("gemini")
        accumulator.append(
            parser.parse(
                event(
                    """
                    {
                      "candidates":[{
                        "content":{
                          "role":"model",
                          "parts":[
                            {
                              "functionCall":{"id":"call-a","name":"weather","args":{"city":"Paris"}},
                              "thoughtSignature":"gemini-stream-signature"
                            },
                            {
                              "functionCall":{"id":"call-b","name":"time","args":{"zone":"UTC"}}
                            }
                          ]
                        }
                      }]
                    }
                    """,
                ),
            ),
        )
        accumulator.append(
            parser.parse(
                event(
                    """
                    {
                      "candidates":[{
                        "content":{"role":"model","parts":[{"text":""}]},
                        "finishReason":"STOP"
                      }]
                    }
                    """,
                ),
            ),
        )

        val response = accumulator.toResponse()
        val message = requireNotNull(response.message)
        val parts = message.getAsJsonArray("parts")

        assertEquals("model", message.get("role").asString)
        assertEquals("gemini-stream-signature", parts[0].asJsonObject.get("thoughtSignature").asString)
        assertEquals("weather", parts[0].asJsonObject.getAsJsonObject("functionCall").get("name").asString)
        assertEquals("time", parts[1].asJsonObject.getAsJsonObject("functionCall").get("name").asString)
        assertEquals(2, response.toolCalls.size)
        assertEquals("STOP", response.finishReason)
        assertTrue(parts[2].asJsonObject.has("text"))
    }

    @Test
    fun geminiAccumulatorKeepsFirstCandidateDataMessageFinishAndMergedUsageAligned() {
        val parser = AiStreamParser("gemini")
        val accumulator = AiStreamAccumulator("gemini")
        listOf(
            """
            {
              "candidates":[
                {
                  "content":{
                    "role":"model",
                    "parts":[
                      {"text":"First"},
                      {
                        "functionCall":{"id":"call-first","name":"firstTool","args":{"id":1}},
                        "thoughtSignature":"first-signature"
                      }
                    ]
                  }
                },
                {
                  "content":{
                    "role":"model",
                    "parts":[
                      {"text":"Second"},
                      {"functionCall":{"id":"call-second","name":"secondTool","args":{"id":2}}}
                    ]
                  },
                  "finishReason":"STOP"
                }
              ],
              "usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":1,"totalTokenCount":4}
            }
            """,
            """
            {
              "candidates":[
                {
                  "content":{"role":"model","parts":[{"text":"!"}]},
                  "finishReason":"STOP"
                },
                {
                  "content":{"role":"model","parts":[{"text":"Ignored"}]},
                  "finishReason":"MAX_TOKENS"
                }
              ],
              "usageMetadata":{"candidatesTokenCount":2,"totalTokenCount":5}
            }
            """,
        ).forEach { payload ->
            accumulator.append(parser.parse(event(payload)))
        }

        val response = accumulator.toResponse()
        val parts = requireNotNull(response.message).getAsJsonArray("parts")

        assertEquals("First!", response.text)
        assertEquals("STOP", response.finishReason)
        assertEquals(1, response.toolCalls.size)
        assertEquals("call-first", response.toolCalls.single().id)
        assertEquals(3, parts.size())
        assertEquals("First", parts[0].asJsonObject.get("text").asString)
        assertEquals(
            "first-signature",
            parts[1].asJsonObject.get("thoughtSignature").asString,
        )
        assertEquals("!", parts[2].asJsonObject.get("text").asString)
        assertEquals(3L, response.usage?.inputTokens)
        assertEquals(2L, response.usage?.outputTokens)
        assertEquals(5L, response.usage?.totalTokens)
    }

    private fun event(data: String): SseEvent {
        return SseEvent(
            id = null,
            event = null,
            data = data.trimIndent(),
            retryMillis = null,
            isDone = data.trim() == "[DONE]",
        )
    }
}
