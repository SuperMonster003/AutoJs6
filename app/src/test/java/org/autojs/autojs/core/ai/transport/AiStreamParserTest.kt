package org.autojs.autojs.core.ai.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStreamParserTest {

    @Test
    fun parsesStatefulOpenAiChatDeltasReasoningToolsUsageAndDone() {
        val parser = AiStreamParser("openai-compatible")
        val first = parser.parse(
            event(
                """
                {
                  "choices":[{"delta":{
                    "content":"Hello ",
                    "reasoning_content":"Think ",
                    "tool_calls":[{
                      "index":0,
                      "id":"call-1",
                      "function":{"name":"weather","arguments":"{\"city\":"}
                    }]
                  }}]
                }
                """,
            ),
        )
        val second = parser.parse(
            event(
                """
                {
                  "choices":[{
                    "delta":{"content":"world","tool_calls":[{
                      "index":0,
                      "function":{"arguments":"\"Paris\"}"}
                    }]},
                    "finish_reason":"tool_calls"
                  }],
                  "usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}
                }
                """,
            ),
        )
        val done = parser.parse(event("[DONE]"))

        assertEquals("Hello ", first.text)
        assertEquals("Think ", first.reasoning)
        assertEquals("call-1", first.toolCalls.single().id)
        assertEquals("weather", first.toolCalls.single().name)
        assertEquals("""{"city":""", first.toolCalls.single().arguments)
        assertTrue(first.toolCalls.single().argumentsAreDelta)

        assertEquals("world", second.text)
        assertEquals("call-1", second.toolCalls.single().id)
        assertEquals("weather", second.toolCalls.single().name)
        assertEquals(""""Paris"}""", second.toolCalls.single().arguments)
        assertEquals("tool_calls", second.finishReason)
        assertEquals(8L, second.usage?.totalTokens)
        assertFalse(second.done)

        assertTrue(done.done)
        assertEquals("[DONE]", done.raw.asString)
    }

    @Test
    fun parsesOpenAiResponsesTypedEvents() {
        val parser = AiStreamParser("openai")
        val item = parser.parse(
            event(
                """
                {
                  "type":"response.output_item.added",
                  "output_index":1,
                  "item":{
                    "type":"function_call",
                    "id":"item-2",
                    "call_id":"call-2",
                    "name":"lookup",
                    "arguments":""
                  }
                }
                """,
            ),
        )
        val arguments = parser.parse(
            event(
                """
                {
                  "type":"response.function_call_arguments.delta",
                  "item_id":"item-2",
                  "output_index":1,
                  "delta":"{\"id\":7}"
                }
                """,
            ),
        )
        val reasoning = parser.parse(
            event("""{"type":"response.reasoning_summary_text.delta","delta":"Think"}"""),
        )
        val completed = parser.parse(
            event(
                """
                {
                  "type":"response.completed",
                  "response":{"usage":{"input_tokens":3,"output_tokens":2}}
                }
                """,
            ),
        )

        assertEquals("call-2", item.toolCalls.single().id)
        assertEquals("call-2", arguments.toolCalls.single().id)
        assertEquals("lookup", arguments.toolCalls.single().name)
        assertEquals("""{"id":7}""", arguments.toolCalls.single().arguments)
        assertTrue(arguments.toolCalls.single().argumentsAreDelta)
        assertEquals("Think", reasoning.reasoning)
        assertTrue(completed.done)
        assertEquals("completed", completed.finishReason)
        assertEquals(5L, completed.usage?.totalTokens)
    }

    @Test
    fun parsesAnthropicEventsAndRetainsToolMetadata() {
        val parser = AiStreamParser("anthropic")
        val start = parser.parse(
            event(
                """
                {
                  "type":"content_block_start",
                  "index":2,
                  "content_block":{
                    "type":"tool_use",
                    "id":"tool-1",
                    "name":"lookup",
                    "input":{}
                  }
                }
                """,
            ),
        )
        val arguments = parser.parse(
            event(
                """
                {
                  "type":"content_block_delta",
                  "index":2,
                  "delta":{"type":"input_json_delta","partial_json":"{\"id\":7}"}
                }
                """,
            ),
        )
        val text = parser.parse(
            event(
                """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}""",
            ),
        )
        val reasoning = parser.parse(
            event(
                """
                {
                  "type":"content_block_delta",
                  "index":1,
                  "delta":{"type":"thinking_delta","thinking":"Think"}
                }
                """,
            ),
        )
        val messageDelta = parser.parse(
            event(
                """
                {
                  "type":"message_delta",
                  "delta":{"stop_reason":"tool_use"},
                  "usage":{"input_tokens":6,"output_tokens":4}
                }
                """,
            ),
        )
        val stop = parser.parse(event("""{"type":"message_stop"}"""))

        assertEquals("tool-1", start.toolCalls.single().id)
        assertEquals("{}", start.toolCalls.single().arguments)
        assertEquals("tool-1", arguments.toolCalls.single().id)
        assertEquals("lookup", arguments.toolCalls.single().name)
        assertTrue(arguments.toolCalls.single().argumentsAreDelta)
        assertEquals("Hi", text.text)
        assertEquals("Think", reasoning.reasoning)
        assertEquals("tool_use", messageDelta.finishReason)
        assertEquals(10L, messageDelta.usage?.totalTokens)
        assertTrue(stop.done)
    }

    @Test
    fun parsesAnthropicStreamErrorReturnedWithHttpSuccess() {
        val chunk = AiStreamParser("anthropic").parse(
            event(
                """
                {
                  "type":"error",
                  "error":{"type":"overloaded_error","message":"Busy"}
                }
                """,
            ),
        )

        assertTrue(chunk.done)
        assertNotNull(chunk.error)
        assertEquals("overloaded_error", chunk.error?.type)
        assertEquals("Busy", chunk.error?.message)
    }

    @Test
    fun parsesGeminiSseResponseIncludingThoughtToolsUsageAndFinish() {
        val chunk = AiStreamParser("gemini").parse(
            event(
                """
                {
                  "candidates":[{
                    "content":{"parts":[
                      {"text":"Think","thought":true},
                      {"text":"Answer"},
                      {"functionCall":{"name":"lookup","args":{"id":7}}}
                    ]},
                    "finishReason":"STOP"
                  }],
                  "usageMetadata":{
                    "promptTokenCount":5,
                    "candidatesTokenCount":3,
                    "totalTokenCount":8
                  }
                }
                """,
            ),
        )

        assertEquals("Answer", chunk.text)
        assertEquals("Think", chunk.reasoning)
        assertEquals("lookup", chunk.toolCalls.single().name)
        assertEquals("""{"id":7}""", chunk.toolCalls.single().arguments)
        assertEquals(8L, chunk.usage?.totalTokens)
        assertEquals("STOP", chunk.finishReason)
        assertTrue(chunk.done)
    }

    @Test
    fun geminiStreamUsesOnlyFirstCandidateForDataAndCompletion() {
        val chunk = AiStreamParser("gemini").parse(
            event(
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
                  "usageMetadata":{"promptTokenCount":3}
                }
                """,
            ),
        )

        assertEquals("First", chunk.text)
        assertEquals(1, chunk.toolCalls.size)
        assertEquals("call-first", chunk.toolCalls.single().id)
        assertEquals(null, chunk.finishReason)
        assertFalse(chunk.done)
        assertEquals(3L, chunk.usage?.inputTokens)
    }

    @Test
    fun malformedStreamPayloadDoesNotAppearInException() {
        val secret = "super-secret-stream-value"
        val exception = assertThrows(AiStreamProtocolException::class.java) {
            AiStreamParser("openai").parse(event("""{"value":"$secret""""))
        }

        assertFalse(exception.message.orEmpty().contains(secret))
        assertFalse(exception.stackTraceToString().contains(secret))
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
