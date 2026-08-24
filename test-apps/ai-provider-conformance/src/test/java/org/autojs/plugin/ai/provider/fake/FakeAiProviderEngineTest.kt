package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiProviderEngineTest {
    @Test
    fun echoPlanIsDeterministicAndReportsStableUsage() {
        val request = fakeRequest(prompt = "hello")
        val materialized = FakeMaterializedRequest("hello", 5L)

        val first = FakeAiProviderEngine.plan(request, materialized)
        val second = FakeAiProviderEngine.plan(request, materialized)

        assertEquals(first, second)
        assertEquals("hello", first.output)
        assertEquals(2L, first.usage.inputTokens)
        assertEquals(2L, first.usage.outputTokens)
        assertEquals(4L, first.usage.totalTokens)
    }

    @Test
    fun streamChunksReassembleUnicodeWithoutSplittingCodePoints() {
        val prompt = "a😀bc😀defghijklmnopqrstuvwxyz"
        val request = fakeRequest(
            modelId = FakeAiProviderFixture.MODEL_STREAM,
            prompt = prompt,
            stream = true,
        )
        val plan = FakeAiProviderEngine.plan(
            request,
            FakeMaterializedRequest(prompt, AiValidation.utf8Size(prompt).toLong()),
        )

        assertEquals(prompt, plan.chunks.joinToString(separator = "") { it.textDelta })
        assertTrue(plan.chunks.all { AiValidation.utf8Size(it.textDelta) <= 12 })
    }

    @Test
    fun reasoningPlanSeparatesReasoningFromVisibleText() {
        val request = fakeRequest(
            modelId = FakeAiProviderFixture.MODEL_REASONING,
            stream = true,
            reasoning = true,
        )
        val plan = FakeAiProviderEngine.plan(request, FakeMaterializedRequest("hello", 5L))

        assertEquals("deterministic reasoning", plan.chunks.first().reasoningDelta)
        assertEquals("hello", plan.chunks.drop(1).joinToString(separator = "") { it.textDelta })
        assertTrue(plan.reasoningSummary != null)
        assertEquals(2L, plan.usage.reasoningTokens)
    }

    @Test
    fun outputTokenAndReasoningByteLimitsAreHonoredTogether() {
        val tokenLimited = FakeAiProviderEngine.plan(
            fakeRequest(prompt = "a long deterministic prompt", maximumOutputTokens = 1),
            FakeMaterializedRequest("a long deterministic prompt", 27L),
        )
        assertTrue(tokenLimited.outputPayload.declaredLengthBytes <= 4L)
        assertTrue(requireNotNull(tokenLimited.usage.outputTokens) <= 1L)

        val reasoningLimitedRequest = fakeRequest(
            modelId = FakeAiProviderFixture.MODEL_REASONING,
            stream = true,
            reasoning = true,
            maximumOutputBytes = 16L,
        )
        val reasoningLimited = FakeAiProviderEngine.plan(
            reasoningLimitedRequest,
            FakeMaterializedRequest("hello", 5L),
        )
        val streamedBytes = reasoningLimited.chunks.sumOf { chunk ->
            AiValidation.utf8Size(chunk.textDelta).toLong() + AiValidation.utf8Size(chunk.reasoningDelta).toLong()
        }
        val terminalBytes = reasoningLimited.outputPayload.declaredLengthBytes +
            (reasoningLimited.reasoningSummary?.declaredLengthBytes ?: 0L)
        assertTrue(streamedBytes <= reasoningLimitedRequest.options.maximumOutputBytes)
        assertTrue(terminalBytes <= reasoningLimitedRequest.options.maximumOutputBytes)
    }

    @Test
    fun jsonMimeAlwaysProducesJsonEvenWithoutStructuredFlag() {
        val request = fakeRequest(
            responseMimeType = AiProviderMimeType.JSON,
            structuredJson = false,
            prompt = "quote \" and newline\n",
        )
        val plan = FakeAiProviderEngine.plan(request, FakeMaterializedRequest("quote \" and newline\n", 20L))

        assertEquals("{\"echo\":\"quote \\\" and newline\\n\"}", plan.output)
        assertEquals(AiProviderMimeType.JSON, plan.outputPayload.mimeType)
    }

    @Test
    fun tinyJsonLimitStillProducesAValidJsonValue() {
        val request = fakeRequest(
            responseMimeType = AiProviderMimeType.JSON,
            maximumOutputBytes = 1L,
        )
        val plan = FakeAiProviderEngine.plan(request, FakeMaterializedRequest("hello", 5L))

        assertEquals("0", plan.output)
        assertEquals(1, plan.outputPayload.inlineBytes?.size)
    }

    @Test
    fun toolCallAndResultOutputAreDeterministic() {
        val request = fakeRequest(
            modelId = FakeAiProviderFixture.MODEL_TOOLS,
            stream = true,
            tools = listOf(fakeTool()),
        )
        val calls = FakeAiProviderEngine.createToolCalls(request)
        val plan = FakeAiProviderEngine.plan(request, FakeMaterializedRequest("hello", 5L), "result")

        assertEquals(FakeAiProviderEngine.TOOL_CALL_ID, calls.calls.single().callId)
        assertEquals("lookup", calls.calls.single().name)
        assertEquals("tool:result", plan.output)
        assertFalse(plan.chunks.isEmpty())
    }
}
